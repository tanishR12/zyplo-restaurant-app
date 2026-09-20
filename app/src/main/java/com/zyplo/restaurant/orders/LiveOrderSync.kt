package com.zyplo.restaurant.orders

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.zyplo.restaurant.alerts.OrderWatchService
import com.zyplo.restaurant.data.Prefs
import com.zyplo.restaurant.data.RestaurantApi
import org.json.JSONObject

object LiveOrderSync {
    fun tick(context: Context) {
        if (!Prefs.hasRestaurantSession) return
        registerPushIfNeeded()
        pollNewBookings(context)
    }

    fun registerPushIfNeeded() {
        val fcm = Prefs.fcmToken ?: return
        if (!Prefs.hasRestaurantSession) return
        if (fcm == Prefs.registeredPushToken) return
        val ok = RestaurantApi.registerAndroidPush(fcm)
        if (ok) {
            Prefs.registeredPushToken = fcm
            val id = Prefs.restaurantId
            runCatching {
                val messaging = FirebaseMessaging.getInstance()
                messaging.subscribeToTopic("restaurant_orders")
                messaging.subscribeToTopic("zyplo_restaurant")
                messaging.subscribeToTopic("lovebul_restaurant")
                if (!id.isNullOrBlank()) {
                    messaging.subscribeToTopic("restaurant_$id")
                    messaging.subscribeToTopic("food_orders")
                }
            }
        }
    }

    private fun pollNewBookings(context: Context) {
        val payload = RestaurantApi.listOrders() ?: return
        val err = payload.optString("error")
        if (err.equals("session_expired", ignoreCase = true) || err.contains("unauthorized", ignoreCase = true)) {
            return
        }
        val orders = payload.optJSONArray("orders") ?: return
        val pending = mutableListOf<JSONObject>()
        for (i in 0 until orders.length()) {
            val item = orders.optJSONObject(i) ?: continue
            if (item.optString("status").equals("pending", ignoreCase = true)) {
                pending += item
            }
        }
        val ids = pending.mapNotNull { idOf(it) }.toSet()
        if (!Prefs.liveOrdersSeeded) {
            Prefs.knownPendingIds = ids
            Prefs.pendingCount = pending.size
            Prefs.liveOrdersSeeded = true
            return
        }
        val newIds = ids - Prefs.knownPendingIds
        val countIncreased = pending.size > Prefs.pendingCount
        Prefs.knownPendingIds = ids
        Prefs.pendingCount = pending.size
        if (newIds.isEmpty() && !countIncreased) return
        val toAlert = if (newIds.isNotEmpty()) {
            pending.filter { idOf(it) in newIds }
        } else {
            pending.takeLast(1)
        }
        toAlert.forEach { item ->
            val copy = JSONObject(item.toString())
            idOf(item)?.let { copy.put("order_id", it) }
            val number = copy.optString("order_number").ifBlank { copy.optString("order_id") }
            val amount = copy.opt("total_amount")?.toString().orEmpty()
            val body = buildString {
                append("New food booking")
                if (number.isNotBlank()) append(" · ").append(number)
                if (amount.isNotBlank() && amount != "null") append(" · ₹").append(amount)
            }
            OrderWatchService.notifyNewOrder(context, "New food order", body, copy.toString())
        }
    }

    private fun idOf(item: JSONObject): String? {
        return item.optString("id").ifBlank { item.optString("order_id") }.ifBlank { null }
    }
}
