package com.zyplo.restaurant.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zyplo.restaurant.alerts.OrderWatchService
import com.zyplo.restaurant.data.Prefs
import org.json.JSONObject

class ZyploMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Prefs.fcmToken = token
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = (data["type"] ?: data["event"] ?: data["kind"] ?: "").lowercase()
        if (type.contains("silent") || type.contains("ping")) return

        val title = message.notification?.title
            ?: data["title"]
            ?: getString(com.zyplo.restaurant.R.string.new_order_title)
        val body = message.notification?.body
            ?: data["body"]
            ?: data["message"]
            ?: getString(com.zyplo.restaurant.R.string.new_order_body)
        val json = JSONObject().apply {
            data.forEach { (k, v) -> put(k, v) }
            put("title", title)
            put("body", body)
        }.toString()

        val isOrder = type.contains("order") ||
            type.contains("incoming") ||
            data.containsKey("order_id") ||
            data.containsKey("orderId") ||
            title.contains("new order", ignoreCase = true) ||
            title.contains("incoming order", ignoreCase = true)

        if (isOrder) {
            OrderWatchService.notifyNewOrder(applicationContext, title, body, json)
        }
    }
}
