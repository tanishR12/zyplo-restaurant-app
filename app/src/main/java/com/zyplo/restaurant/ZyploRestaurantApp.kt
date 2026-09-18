package com.zyplo.restaurant

import android.app.Application
import com.google.firebase.FirebaseApp
import com.zyplo.restaurant.alerts.NotificationHelper
import com.zyplo.restaurant.data.Prefs

class ZyploRestaurantApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        NotificationHelper.ensureChannels(this)
        runCatching { FirebaseApp.initializeApp(this) }
    }
}
