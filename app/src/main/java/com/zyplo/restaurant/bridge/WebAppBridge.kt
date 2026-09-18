package com.zyplo.restaurant.bridge

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
        Prefs.loggedIn = value
        if (value) onLoggedIn()
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
