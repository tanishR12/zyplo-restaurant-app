package com.zyplo.restaurant.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.zyplo.restaurant.alerts.OrderWatchService
import com.zyplo.restaurant.data.Prefs
import com.zyplo.restaurant.location.RestaurantLocationService
import com.zyplo.restaurant.overlay.OverlayBubbleService
import org.json.JSONObject

class WebAppBridge(
    private val context: android.content.Context,
    private val onLoggedIn: () -> Unit
) {
    @JavascriptInterface
    fun onNewOrder(payload: String) {
        val obj = runCatching { JSONObject(payload) }.getOrNull()
        val source = obj?.optString("source").orEmpty()
        if (source == "dom" || source == "fetch") return
        val orderId = obj?.optString("order_id").orEmpty().ifBlank { obj?.optString("orderId").orEmpty() }
        if (orderId.isBlank() && payload.length > 2000) return
        OrderWatchService.notifyNewOrder(
            context,
            "New restaurant order",
            payload.take(140),
            payload
        )
    }

    @JavascriptInterface
    fun stopSiren() {
        OrderWatchService.stopSiren(context)
        OverlayBubbleService.stop(context)
        OverlayBubbleService.start(context)
    }

    @JavascriptInterface
    fun setLoggedIn(value: Boolean) {
        if (!value && Prefs.hasRestaurantSession) {
            Prefs.loggedIn = true
            return
        }
        val was = Prefs.loggedIn
        Prefs.loggedIn = value
        if (value && !was) {
            Handler(Looper.getMainLooper()).post { onLoggedIn() }
        }
    }

    @JavascriptInterface
    fun restaurantSession(): String = Prefs.restaurantSessionRaw.orEmpty()

    @JavascriptInterface
    fun saveRestaurantSession(raw: String) {
        if (raw.isBlank() || raw == "null" || raw == "{}") return
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val id = firstString(obj, "restaurant_id", "restaurantId", "id")
        val token = firstString(obj, "session_token", "sessionToken", "token", "access_token")
        val email = firstString(obj, "email", "restaurant_email")
        if (id.isNullOrBlank() || token.isNullOrBlank()) return
        val changed = Prefs.restaurantId != id || Prefs.restaurantSessionToken != token
        Prefs.saveRestaurantSession(id, token, email, raw)
        Prefs.loggedIn = true
        if (changed) {
            Prefs.registeredPushToken = null
            Prefs.liveOrdersSeeded = false
            Handler(Looper.getMainLooper()).post {
                onLoggedIn()
                OrderWatchService.syncNow(context)
            }
        }
    }

    @JavascriptInterface
    fun fcmToken(): String = Prefs.fcmToken.orEmpty()

    @JavascriptInterface
    fun locationJson(): String {
        val obj = JSONObject()
        RestaurantLocationService.lastLat?.let { obj.put("lat", it) }
        RestaurantLocationService.lastLng?.let { obj.put("lng", it) }
        return obj.toString()
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String? {
        keys.forEach { key ->
            val value = obj.optString(key).takeIf { it.isNotBlank() && it != "null" }
            if (value != null) return value
        }
        return null
    }
}
