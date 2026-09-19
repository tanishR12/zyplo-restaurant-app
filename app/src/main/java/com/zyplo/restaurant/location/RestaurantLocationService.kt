package com.zyplo.restaurant.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.zyplo.restaurant.alerts.NotificationHelper

class RestaurantLocationService : LifecycleService() {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            lastLat = result.lastLocation?.latitude
            lastLng = result.lastLocation?.longitude
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else 0
        ServiceCompat.startForeground(
            this,
            NotificationHelper.ID_LOCATION,
            NotificationHelper.locationNotification(this),
            type
        )
        startUpdates()
    }

    private fun startUpdates() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 20_000L)
            .setMinUpdateIntervalMillis(10_000L)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        super.onDestroy()
    }

    companion object {
        @Volatile var lastLat: Double? = null
        @Volatile var lastLng: Double? = null

        fun distanceKmTo(lat: Double, lng: Double): Double? {
            val kitchenLat = lastLat ?: return null
            val kitchenLng = lastLng ?: return null
            val out = FloatArray(1)
            Location.distanceBetween(kitchenLat, kitchenLng, lat, lng, out)
            return out[0] / 1000.0
        }

        fun start(context: Context) {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            if (fine != PackageManager.PERMISSION_GRANTED) return
            runCatching { context.startForegroundService(Intent(context, RestaurantLocationService::class.java)) }
        }
    }
}
