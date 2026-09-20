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
        set(value) {
            if (value.isBlank()) return
            if (Config.isLoginUrl(value) && hasRestaurantSession) return
            prefs.edit().putString("last_url", value).apply()
        }

    var restaurantSessionRaw: String?
        get() = prefs.getString("restaurant_session_raw", null)
        set(value) = prefs.edit().putString("restaurant_session_raw", value).apply()

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

    var overlayPrompted: Boolean
        get() = prefs.getBoolean("overlay_prompted", false)
        set(value) = prefs.edit().putBoolean("overlay_prompted", value).apply()

    var batteryPrompted: Boolean
        get() = prefs.getBoolean("battery_prompted", false)
        set(value) = prefs.edit().putBoolean("battery_prompted", value).apply()

    var fullScreenPrompted: Boolean
        get() = prefs.getBoolean("fullscreen_prompted", false)
        set(value) = prefs.edit().putBoolean("fullscreen_prompted", value).apply()

    var autostartPrompted: Boolean
        get() = prefs.getBoolean("autostart_prompted", false)
        set(value) = prefs.edit().putBoolean("autostart_prompted", value).apply()

    var restaurantId: String?
        get() = prefs.getString("restaurant_id", null)
        set(value) = prefs.edit().putString("restaurant_id", value).apply()

    var restaurantSessionToken: String?
        get() = prefs.getString("restaurant_session_token", null)
        set(value) = prefs.edit().putString("restaurant_session_token", value).apply()

    var restaurantEmail: String?
        get() = prefs.getString("restaurant_email", null)
        set(value) = prefs.edit().putString("restaurant_email", value).apply()

    var liveOrdersSeeded: Boolean
        get() = prefs.getBoolean("live_orders_seeded", false)
        set(value) = prefs.edit().putBoolean("live_orders_seeded", value).apply()

    var pendingCount: Int
        get() = prefs.getInt("pending_count", 0)
        set(value) = prefs.edit().putInt("pending_count", value).apply()

    var knownPendingIds: Set<String>
        get() = prefs.getString("known_pending_ids", "")
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
        set(value) = prefs.edit().putString("known_pending_ids", value.joinToString("\n")).apply()

    var registeredPushToken: String?
        get() = prefs.getString("registered_push_token", null)
        set(value) = prefs.edit().putString("registered_push_token", value).apply()

    fun saveRestaurantSession(id: String?, token: String?, email: String?, raw: String? = null) {
        if (id.isNullOrBlank() || token.isNullOrBlank()) return
        restaurantId = id
        restaurantSessionToken = token
        restaurantEmail = email
        if (!raw.isNullOrBlank()) restaurantSessionRaw = raw
        else if (restaurantSessionRaw.isNullOrBlank()) {
            restaurantSessionRaw = org.json.JSONObject()
                .put("restaurant_id", id)
                .put("session_token", token)
                .put("email", email ?: "")
                .toString()
        }
    }

    val hasRestaurantSession: Boolean
        get() = !restaurantId.isNullOrBlank() && (restaurantSessionToken?.length ?: 0) >= 16
}
