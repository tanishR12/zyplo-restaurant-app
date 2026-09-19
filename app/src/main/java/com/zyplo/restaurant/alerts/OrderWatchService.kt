package com.zyplo.restaurant.alerts

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.zyplo.restaurant.data.IncomingOrder
import com.zyplo.restaurant.data.Prefs
import com.zyplo.restaurant.overlay.OverlayBubbleService
import com.zyplo.restaurant.ui.OrderAlertActivity

class OrderWatchService : Service() {
    private var siren: SirenPlayer? = null
    private var cpuLock: PowerManager.WakeLock? = null
    private var screenLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        siren = SirenPlayer(this)
        val pm = getSystemService(PowerManager::class.java)
        cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zyplo:order-cpu").apply {
            setReferenceCounted(false)
        }
        @Suppress("DEPRECATION")
        screenLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "zyplo:order-screen"
        ).apply { setReferenceCounted(false) }
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        when (intent?.action) {
            ACTION_NEW_ORDER -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(com.zyplo.restaurant.R.string.new_order_title)
                val body = intent.getStringExtra(EXTRA_BODY) ?: getString(com.zyplo.restaurant.R.string.new_order_body)
                val json = intent.getStringExtra(EXTRA_ORDER) ?: "{}"
                handleNewOrder(IncomingOrder.parse(title, body, json))
            }
            ACTION_STOP_SIREN -> stopSiren()
        }
        return START_STICKY
    }

    private fun handleNewOrder(order: IncomingOrder) {
        Prefs.pendingOrderJson = order.rawJson
        val signature = "${order.orderId}|${order.title}|${order.body}|${order.distanceKm}"
        if (signature == Prefs.lastOrderSignature) return
        Prefs.lastOrderSignature = signature

        cpuLock?.acquire(3 * 60 * 1000L)
        runCatching { screenLock?.acquire(60_000L) }
        if (Prefs.sirenEnabled) siren?.start()

        NotificationHelper.showIncomingOrder(this, order)
        OverlayBubbleService.showOrder(this, order)

        val alert = Intent(this, OrderAlertActivity::class.java)
            .putExtra(OrderAlertActivity.EXTRA_TITLE, order.title)
            .putExtra(OrderAlertActivity.EXTRA_BODY, order.summary)
            .putExtra(OrderAlertActivity.EXTRA_ORDER, order.rawJson)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        runCatching { startActivity(alert) }
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Full-screen notification is the lock-screen path; activity still tries to wake.
        }
    }

    private fun stopSiren() {
        siren?.stop()
        if (cpuLock?.isHeld == true) cpuLock?.release()
        if (screenLock?.isHeld == true) screenLock?.release()
    }

    override fun onDestroy() {
        stopSiren()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        ServiceCompat.startForeground(
            this,
            NotificationHelper.ID_WATCH,
            NotificationHelper.watchNotification(this),
            types
        )
    }

    companion object {
        const val ACTION_NEW_ORDER = "com.zyplo.restaurant.NEW_ORDER"
        const val ACTION_STOP_SIREN = "com.zyplo.restaurant.STOP_SIREN"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_ORDER = "order"

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, OrderWatchService::class.java))
            }
        }

        fun notifyNewOrder(context: Context, title: String, body: String, json: String = "{}") {
            val order = IncomingOrder.parse(title, body, json)
            val intent = Intent(context, OrderWatchService::class.java)
                .setAction(ACTION_NEW_ORDER)
                .putExtra(EXTRA_TITLE, order.title)
                .putExtra(EXTRA_BODY, order.summary)
                .putExtra(EXTRA_ORDER, json)
            runCatching { context.startForegroundService(intent) }
        }

        fun stopSiren(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, OrderWatchService::class.java).setAction(ACTION_STOP_SIREN)
                )
            }
        }
    }
}
