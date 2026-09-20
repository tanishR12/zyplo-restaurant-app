package com.zyplo.restaurant.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object RestaurantApi {
    fun listOrders(): JSONObject? {
        val id = Prefs.restaurantId ?: return null
        val token = Prefs.restaurantSessionToken ?: return null
        return post(
            "get-restaurant-orders",
            JSONObject()
                .put("restaurant_id", id)
                .put("session_token", token)
        )
    }

    fun registerAndroidPush(fcmToken: String): Boolean {
        val id = Prefs.restaurantId ?: return false
        val token = Prefs.restaurantSessionToken ?: return false
        val result = post(
            "restaurant-push-register",
            JSONObject()
                .put("restaurant_id", id)
                .put("session_token", token)
                .put("action", "register")
                .put("token", fcmToken)
                .put("device_type", "android-fcm")
        ) ?: return false
        return result.optBoolean("ok", true) && result.optString("error").isBlank()
    }

    fun updateOrder(orderId: String, action: String): Boolean {
        if (orderId.isBlank()) return false
        val restaurantId = Prefs.restaurantId ?: return false
        val token = Prefs.restaurantSessionToken ?: return false
        val accept = action.equals("accept", ignoreCase = true)
        val status = if (accept) "accepted" else "rejected"
        val body = JSONObject()
            .put("restaurant_id", restaurantId)
            .put("session_token", token)
            .put("order_id", orderId)
            .put("id", orderId)
            .put("action", if (accept) "accept" else "reject")
            .put("status", status)
        val functions = listOf(
            "restaurant-order-action",
            "update-restaurant-order",
            "update-order-status",
            "restaurant-update-order"
        )
        functions.forEach { name ->
            val result = post(name, body) ?: return@forEach
            val err = result.optString("error")
            if (err.isBlank() || result.optBoolean("ok", false) || result.optBoolean("success", false)) {
                return true
            }
        }
        val statuses = if (accept) listOf("accepted", "confirmed", "preparing") else listOf("rejected", "cancelled", "declined")
        statuses.forEach { value ->
            if (patchRow("orders", orderId, value) || patchRow("food_orders", orderId, value)) return true
        }
        return false
    }

    private fun patchRow(table: String, orderId: String, status: String): Boolean {
        return runCatching {
            val conn = (URL("${Config.SUPABASE_URL}/rest/v1/$table?id=eq.$orderId").openConnection() as HttpURLConnection).apply {
                requestMethod = "PATCH"
                connectTimeout = 12_000
                readTimeout = 12_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", Config.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer ${Config.SUPABASE_ANON_KEY}")
                Prefs.restaurantSessionToken?.let { setRequestProperty("X-Restaurant-Session", it) }
                setRequestProperty("Prefer", "return=minimal")
            }
            conn.outputStream.use {
                it.write(JSONObject().put("status", status).toString().toByteArray(Charsets.UTF_8))
            }
            conn.responseCode in 200..299
        }.getOrDefault(false)
    }

    private fun post(function: String, body: JSONObject): JSONObject? {
        return runCatching {
            val conn = (URL("${Config.SUPABASE_URL}/functions/v1/$function").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", Config.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer ${Config.SUPABASE_ANON_KEY}")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
            if (text.isBlank()) {
                return@runCatching if (conn.responseCode in 200..299) JSONObject().put("ok", true) else null
            }
            JSONObject(text)
        }.getOrNull()
    }
}
