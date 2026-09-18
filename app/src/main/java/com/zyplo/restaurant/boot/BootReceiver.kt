package com.zyplo.restaurant.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zyplo.restaurant.alerts.OrderWatchService
import com.zyplo.restaurant.data.Prefs
import com.zyplo.restaurant.location.RestaurantLocationService
import com.zyplo.restaurant.overlay.OverlayBubbleService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!Prefs.setupComplete) return
        OrderWatchService.start(context)
        if (Prefs.loggedIn) {
            OverlayBubbleService.start(context)
            RestaurantLocationService.start(context)
        }
    }
}
