package com.zyplo.restaurant.alerts

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.zyplo.restaurant.data.Prefs
import com.zyplo.restaurant.overlay.OverlayBubbleService
import com.zyplo.restaurant.ui.OrderAlertActivity
import org.json.JSONObject

class OrderWatchService : Service() {
    private var siren: SirenPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        siren = SirenPlayer(this)
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zyplo:order-watch").apply {
            setReferenceCounted(false)
        }
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        when (intent?.action) {
            ACTION_NEW_ORDER -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(com.zyplo.restaurant.R.string.new_order_title)
                val body = intent.getStringExtra(EXTRA_BODY) ?: getString(com.zyplo.restaurant.R.string.new_order_body)
                val json = intent.getStringExtra(EXTRA_ORDER) ?: "{}"
                handleNewOrder(title, body, json)
            }
            ACTION_STOP_SIREN -> stopSiren()
        }
        return START_STICKY
    }

    private fun handleNewOrder(title: String, body: String, json: String) {
        Prefs.pendingOrderJson = json
        val signature = "$title|$body|$json"
        if (signature == Prefs.lastOrderSignature) return
        Prefs.lastOrderSignature = signature

        wakeLock?.acquire(3 * 60 * 1000L)
        if (Prefs.sirenEnabled) {
            siren?.start()
        }
        NotificationHelper.showIncomingOrder(this, title, body, json)
        OverlayBubbleService.showOrder(this, title)

        val alert = Intent(this, OrderAlertActivity::class.java)
            .putExtra(OrderAlertActivity.EXTRA_TITLE, title)
            .putExtra(OrderAlertActivity.EXTRA_BODY, body)
            .putExtra(OrderAlertActivity.EXTRA_ORDER, json)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            this,
            77,
            alert,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { pi.send() }
        startActivity(alert)
    }

    private fun stopSiren() {
        siren?.stop()
        if (wakeLock?.isHeld == true) wakeLock?.release()
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
            val intent = Intent(context, OrderWatchService::class.java)
            context.startForegroundService(intent)
        }

        fun notifyNewOrder(context: Context, title: String, body: String, json: String = "{}") {
            val parsed = runCatching { JSONObject(json) }.getOrNull()
            val resolvedTitle = parsed?.optString("title").takeUnless { it.isNullOrBlank() } ?: title
            val resolvedBody = parsed?.optString("body").takeUnless { it.isNullOrBlank() } ?: body
            val intent = Intent(context, OrderWatchService::class.java)
                .setAction(ACTION_NEW_ORDER)
                .putExtra(EXTRA_TITLE, resolvedTitle)
                .putExtra(EXTRA_BODY, resolvedBody)
                .putExtra(EXTRA_ORDER, json)
            context.startForegroundService(intent)
        }

        fun stopSiren(context: Context) {
            val intent = Intent(context, OrderWatchService::class.java).setAction(ACTION_STOP_SIREN)
            context.startForegroundService(intent)
        }
    }
}
