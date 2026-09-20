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

    private fun post(function: String, body: JSONObject): JSONObject? {
        return runCatching {
            val conn = (URL("${Config.SUPABASE_URL}/functions/v1/$function").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", Config.SUPABASE_ANON_KEY)
                val bearer = body.optString("session_token").ifBlank { Config.SUPABASE_ANON_KEY }
                setRequestProperty("Authorization", "Bearer $bearer")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
            if (text.isBlank()) return@runCatching null
            JSONObject(text)
        }.getOrNull()
    }
}
