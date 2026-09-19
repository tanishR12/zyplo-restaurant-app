package com.zyplo.restaurant.data

import com.zyplo.restaurant.location.RestaurantLocationService
import org.json.JSONObject
import kotlin.math.roundToInt

data class IncomingOrder(
    val title: String,
    val body: String,
    val rawJson: String,
    val orderId: String?,
    val riderName: String?,
    val address: String?,
    val dropLat: Double?,
    val dropLng: Double?,
    val distanceKm: Double?
) {
    val summary: String
        get() = buildString {
            append(body)
            if (!riderName.isNullOrBlank()) {
                if (isNotEmpty()) append("\n")
                append("Rider: ").append(riderName)
            }
            if (!address.isNullOrBlank()) {
                if (isNotEmpty()) append("\n")
                append(address)
            }
            distanceLabel?.let {
                if (isNotEmpty()) append("\n")
                append(it)
            }
        }

    val distanceLabel: String?
        get() = distanceKm?.let { km ->
            if (km < 1) "${(km * 1000).roundToInt()} m to customer"
            else String.format("%.1f km to customer", km)
        }

    val isRealAlert: Boolean
        get() {
            val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return true
            val source = obj.optString("source")
            if (source == "dom" || source == "fetch") return !orderId.isNullOrBlank()
            return true
        }

    companion object {
        fun parse(title: String, body: String, json: String): IncomingOrder {
            val obj = runCatching { JSONObject(json) }.getOrNull()
            val orderId = firstString(obj, "order_id", "orderId")
            val rider = firstString(obj, "rider_name", "riderName", "driver_name", "driverName", "rider")
            val address = firstString(obj, "address", "drop_address", "dropAddress", "delivery_address", "customer_address")
            val dropLat = firstDouble(obj, "drop_lat", "dropLat", "customer_lat", "dest_lat", "latitude", "lat")
            val dropLng = firstDouble(obj, "drop_lng", "dropLng", "customer_lng", "dest_lng", "longitude", "lng")
            val givenDistance = firstDouble(obj, "distance_km", "distanceKm", "delivery_distance", "distance")
            val computed = if (dropLat != null && dropLng != null) {
                RestaurantLocationService.distanceKmTo(dropLat, dropLng)
            } else null
            return IncomingOrder(
                title = firstString(obj, "title") ?: title,
                body = firstString(obj, "body", "message") ?: body,
                rawJson = json,
                orderId = orderId,
                riderName = rider,
                address = address,
                dropLat = dropLat,
                dropLng = dropLng,
                distanceKm = givenDistance ?: computed
            )
        }

        private fun firstString(obj: JSONObject?, vararg keys: String): String? {
            if (obj == null) return null
            keys.forEach { key ->
                val value = obj.optString(key).takeIf { it.isNotBlank() && it != "null" }
                if (value != null) return value
            }
            return null
        }

        private fun firstDouble(obj: JSONObject?, vararg keys: String): Double? {
            if (obj == null) return null
            keys.forEach { key ->
                if (obj.has(key) && !obj.isNull(key)) {
                    val number = obj.optDouble(key, Double.NaN)
                    if (!number.isNaN()) return number
                    obj.optString(key).toDoubleOrNull()?.let { return it }
                }
            }
            return null
        }
    }
}
