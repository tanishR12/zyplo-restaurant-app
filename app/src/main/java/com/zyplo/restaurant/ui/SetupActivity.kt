package com.zyplo.restaurant.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.zyplo.restaurant.R
import com.zyplo.restaurant.alerts.OrderWatchService
import com.zyplo.restaurant.data.Prefs
import com.zyplo.restaurant.device.DeviceSettings

class SetupActivity : AppCompatActivity() {
    private lateinit var status: TextView

    private val runtimePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshStatus()
    }

    private val backgroundLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.setupComplete &&
            DeviceSettings.notificationsOn(this) &&
            DeviceSettings.overlayOn(this)
        ) {
            openPortal()
            return
        }
        setContentView(R.layout.activity_setup)
        status = findViewById(R.id.setupStatus)
        findViewById<Button>(R.id.btnNotifications).setOnClickListener { askNotifications() }
        findViewById<Button>(R.id.btnLocation).setOnClickListener { askLocation() }
        findViewById<Button>(R.id.btnCamera).setOnClickListener {
            runtimePermissions.launch(arrayOf(Manifest.permission.CAMERA))
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener { askOverlay() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { askBattery() }
        findViewById<Button>(R.id.btnLockScreen).setOnClickListener {
            if (com.zyplo.restaurant.device.DeviceSettings.fullScreenAlertsOn(this)) {
                Toast.makeText(this, R.string.lock_screen_ok, Toast.LENGTH_SHORT).show()
            } else {
                com.zyplo.restaurant.device.DeviceSettings.openFullScreenIntentSettings(this)
            }
        }
        findViewById<Button>(R.id.btnAutostart).setOnClickListener {
            com.zyplo.restaurant.device.DeviceSettings.openAutostart(this)
        }
        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            Prefs.setupComplete = true
            OrderWatchService.start(this)
            openPortal()
        }
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (!::status.isInitialized) return
        refreshStatus()
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runtimePermissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        } else {
            Toast.makeText(this, R.string.notifications_ok, Toast.LENGTH_SHORT).show()
        }
    }

    private fun askLocation() {
        runtimePermissions.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            status.postDelayed({
                backgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }, 400)
        }
    }

    private fun askOverlay() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun askBattery() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, R.string.battery_ok, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    private fun refreshStatus() {
        val notify = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val overlay = Settings.canDrawOverlays(this)
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        status.text = getString(
            R.string.setup_status,
            if (notify) "ON" else "OFF",
            if (overlay) "ON" else "OFF",
            if (battery) "ON" else "OFF",
            if (DeviceSettings.fullScreenAlertsOn(this)) "ON" else "OFF"
        )
    }

    private fun openPortal() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
