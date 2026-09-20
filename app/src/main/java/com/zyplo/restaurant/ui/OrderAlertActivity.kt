package com.zyplo.restaurant.ui

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.zyplo.restaurant.R
import com.zyplo.restaurant.alerts.NotificationHelper
import com.zyplo.restaurant.alerts.OrderWatchService
import com.zyplo.restaurant.data.IncomingOrder
import com.zyplo.restaurant.data.RestaurantApi
import com.zyplo.restaurant.overlay.OverlayBubbleService

class OrderAlertActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(this, null)
        }
        setContentView(R.layout.activity_order_alert)
        val order = IncomingOrder.parse(
            intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.new_order_title),
            intent.getStringExtra(EXTRA_BODY) ?: getString(R.string.new_order_body),
            intent.getStringExtra(EXTRA_ORDER) ?: "{}"
        )
        findViewById<TextView>(R.id.alertTitle).text = order.title
        findViewById<TextView>(R.id.alertBody).text = order.summary
        findViewById<Button>(R.id.btnAccept).setOnClickListener { decide(order, "accept") }
        findViewById<Button>(R.id.btnReject).setOnClickListener { decide(order, "reject") }
        findViewById<Button>(R.id.btnOpenOrder).setOnClickListener {
            OrderWatchService.stopSiren(this)
            openPortal(order, null)
        }
    }

    private fun decide(order: IncomingOrder, action: String) {
        OrderWatchService.stopSiren(this)
        NotificationManagerCompat.from(this).cancel(NotificationHelper.ID_ORDER)
        Thread {
            val ok = order.orderId?.let { RestaurantApi.updateOrder(it, action) } ?: false
            runOnUiThread {
                Toast.makeText(
                    this,
                    getString(
                        if (ok) {
                            if (action == "accept") R.string.order_accepted else R.string.order_rejected
                        } else R.string.order_action_failed
                    ),
                    Toast.LENGTH_LONG
                ).show()
                openPortal(order, action)
            }
        }.start()
    }

    private fun openPortal(order: IncomingOrder, action: String?) {
        OverlayBubbleService.start(this)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(MainActivity.EXTRA_OPEN_ORDERS, true)
                .putExtra(MainActivity.EXTRA_ORDER_ACTION, action)
                .putExtra(MainActivity.EXTRA_ORDER_ID, order.orderId)
        )
        finish()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_ORDER = "order"
    }
}
