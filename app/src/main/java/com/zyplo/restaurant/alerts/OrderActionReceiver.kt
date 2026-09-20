package com.zyplo.restaurant.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.zyplo.restaurant.R
import com.zyplo.restaurant.data.IncomingOrder
import com.zyplo.restaurant.data.RestaurantApi
import com.zyplo.restaurant.overlay.OverlayBubbleService
import com.zyplo.restaurant.ui.MainActivity

class OrderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                handle(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_ACCEPT = "com.zyplo.restaurant.ACCEPT_ORDER"
        const val ACTION_REJECT = "com.zyplo.restaurant.REJECT_ORDER"

        fun handle(context: Context, intent: Intent) {
            val action = when (intent.action) {
                ACTION_ACCEPT -> "accept"
                ACTION_REJECT -> "reject"
                else -> return
            }
            val json = intent.getStringExtra(OrderWatchService.EXTRA_ORDER) ?: "{}"
            val order = IncomingOrder.parse(
                intent.getStringExtra(OrderWatchService.EXTRA_TITLE) ?: context.getString(R.string.new_order_title),
                intent.getStringExtra(OrderWatchService.EXTRA_BODY) ?: "",
                json
            )
            OrderWatchService.stopSiren(context)
            NotificationManagerCompat.from(context).cancel(NotificationHelper.ID_ORDER)
            OverlayBubbleService.start(context)
            val ok = order.orderId?.let { RestaurantApi.updateOrder(it, action) } ?: false
            android.os.Handler(context.mainLooper).post {
                Toast.makeText(
                    context,
                    context.getString(if (ok) {
                        if (action == "accept") R.string.order_accepted else R.string.order_rejected
                    } else R.string.order_action_failed),
                    Toast.LENGTH_LONG
                ).show()
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .putExtra(MainActivity.EXTRA_OPEN_ORDERS, true)
                        .putExtra(MainActivity.EXTRA_ORDER_ACTION, action)
                        .putExtra(MainActivity.EXTRA_ORDER_ID, order.orderId)
                )
            }
        }
    }
}
