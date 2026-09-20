package com.zyplo.restaurant.overlay

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.zyplo.restaurant.R
import com.zyplo.restaurant.alerts.NotificationHelper
import com.zyplo.restaurant.data.IncomingOrder
import com.zyplo.restaurant.data.Prefs
import com.zyplo.restaurant.ui.MainActivity
import kotlin.math.abs

class OverlayBubbleService : LifecycleService() {
    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundInternal()
        if (canDrawOverlays()) showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundInternal()
        when (intent?.action) {
            ACTION_SHOW_ORDER -> bindOrder(
                IncomingOrder.parse(
                    intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.new_order_title),
                    intent.getStringExtra(EXTRA_BODY) ?: getString(R.string.new_order_body),
                    intent.getStringExtra(EXTRA_ORDER) ?: "{}"
                )
            )
            ACTION_CLEAR -> bindIdle()
        }
        return START_STICKY
    }

    private fun bindOrder(order: IncomingOrder) {
        bubble?.findViewById<TextView>(R.id.bubbleBadge)?.visibility = View.VISIBLE
        bubble?.contentDescription = order.title
    }

    private fun bindIdle() {
        bubble?.findViewById<TextView>(R.id.bubbleBadge)?.visibility = View.GONE
        bubble?.contentDescription = getString(R.string.bubble_idle)
    }

    private fun startForegroundInternal() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(
            this,
            NotificationHelper.ID_OVERLAY,
            NotificationHelper.overlayNotification(this),
            type
        )
    }

    private fun canDrawOverlays(): Boolean = android.provider.Settings.canDrawOverlays(this)

    private fun showBubble() {
        if (bubble != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        bubble = LayoutInflater.from(this).inflate(R.layout.view_overlay_bubble, null)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 180
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        bubble?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params?.x ?: 0
                    startY = params?.y ?: 0
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    params?.x = startX - dx
                    params?.y = startY + dy
                    windowManager?.updateViewLayout(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) openApp()
                    true
                }
                else -> false
            }
        }
        windowManager?.addView(bubble, params)
        bindIdle()
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_ORDERS, true)
        )
    }

    override fun onDestroy() {
        bubble?.let { windowManager?.removeView(it) }
        bubble = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW_ORDER = "com.zyplo.restaurant.BUBBLE_ORDER"
        const val ACTION_CLEAR = "com.zyplo.restaurant.BUBBLE_CLEAR"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_ORDER = "order"

        fun start(context: Context) {
            if (!Prefs.overlayEnabled) return
            if (!android.provider.Settings.canDrawOverlays(context)) return
            runCatching { context.startForegroundService(Intent(context, OverlayBubbleService::class.java)) }
        }

        fun showOrder(context: Context, order: IncomingOrder) {
            if (!Prefs.overlayEnabled || !android.provider.Settings.canDrawOverlays(context)) return
            context.startForegroundService(
                Intent(context, OverlayBubbleService::class.java)
                    .setAction(ACTION_SHOW_ORDER)
                    .putExtra(EXTRA_TITLE, order.title)
                    .putExtra(EXTRA_BODY, order.body)
                    .putExtra(EXTRA_ORDER, order.rawJson)
            )
        }

        fun showOrder(context: Context, title: String) {
            showOrder(context, IncomingOrder.parse(title, title, "{}"))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayBubbleService::class.java))
        }
    }
}
