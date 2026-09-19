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
        val was = Prefs.loggedIn
        Prefs.loggedIn = value
        if (value && !was) {
            Handler(Looper.getMainLooper()).post { onLoggedIn() }
        }
    }

    @JavascriptInterface
    fun saveRestaurantSession(raw: String) {
        val obj = runCatching { JSONObject(raw) }.getOrNull()
        val id = obj?.optString("restaurant_id").orEmpty().ifBlank { null }
        val token = obj?.optString("session_token").orEmpty().ifBlank { null }
        val email = obj?.optString("email").orEmpty().ifBlank { null }
        val changed = Prefs.restaurantId != id || Prefs.restaurantSessionToken != token
        Prefs.saveRestaurantSession(id, token, email)
        if (!id.isNullOrBlank() && !token.isNullOrBlank()) {
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
}
