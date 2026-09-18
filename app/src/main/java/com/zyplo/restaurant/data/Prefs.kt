package com.zyplo.restaurant.data

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "zyplo_restaurant"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    var setupComplete: Boolean
        get() = prefs.getBoolean("setup_complete", false)
        set(value) = prefs.edit().putBoolean("setup_complete", value).apply()

    var loggedIn: Boolean
        get() = prefs.getBoolean("logged_in", false)
        set(value) = prefs.edit().putBoolean("logged_in", value).apply()

    var fcmToken: String?
        get() = prefs.getString("fcm_token", null)
        set(value) = prefs.edit().putString("fcm_token", value).apply()

    var lastPortalUrl: String
        get() = prefs.getString("last_url", Config.PORTAL_URL) ?: Config.PORTAL_URL
        set(value) = prefs.edit().putString("last_url", value).apply()

    var pendingOrderJson: String?
        get() = prefs.getString("pending_order", null)
        set(value) = prefs.edit().putString("pending_order", value).apply()

    var overlayEnabled: Boolean
        get() = prefs.getBoolean("overlay_enabled", true)
        set(value) = prefs.edit().putBoolean("overlay_enabled", value).apply()

    var sirenEnabled: Boolean
        get() = prefs.getBoolean("siren_enabled", true)
        set(value) = prefs.edit().putBoolean("siren_enabled", value).apply()

    var lastOrderSignature: String?
        get() = prefs.getString("last_order_sig", null)
        set(value) = prefs.edit().putString("last_order_sig", value).apply()
}
