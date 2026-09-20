package com.zyplo.restaurant.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zyplo.restaurant.R
import com.zyplo.restaurant.data.IncomingOrder
import com.zyplo.restaurant.ui.MainActivity
import com.zyplo.restaurant.ui.OrderAlertActivity

object NotificationHelper {
    const val CHANNEL_ORDERS = "zyplo_orders_lockscreen"
    const val CHANNEL_WATCH = "zyplo_watch"
    const val CHANNEL_LOCATION = "zyplo_location"
    const val CHANNEL_OVERLAY = "zyplo_overlay"

    const val ID_WATCH = 4101
    const val ID_LOCATION = 4102
    const val ID_OVERLAY = 4103
    const val ID_ORDER = 4200

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ORDERS,
                context.getString(R.string.channel_orders),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_orders_desc)
                enableVibration(true)
                setSound(alarmUri, alarmAttrs)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WATCH,
                context.getString(R.string.channel_watch),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LOCATION,
                context.getString(R.string.channel_location),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OVERLAY,
                context.getString(R.string.channel_overlay),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    fun watchNotification(context: Context): Notification {
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.watch_title))
            .setContentText(context.getString(R.string.watch_text))
            .setOngoing(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun locationNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_LOCATION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.location_title))
            .setContentText(context.getString(R.string.location_text))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun overlayNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.overlay_title))
            .setContentText(context.getString(R.string.overlay_text))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun showIncomingOrder(context: Context, order: IncomingOrder) {
        val fullScreen = PendingIntent.getActivity(
            context,
            88,
            Intent(context, OrderAlertActivity::class.java)
                .putExtra(OrderAlertActivity.EXTRA_TITLE, order.title)
                .putExtra(OrderAlertActivity.EXTRA_BODY, order.summary)
                .putExtra(OrderAlertActivity.EXTRA_ORDER, order.rawJson)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            context,
            89,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_ORDERS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ORDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(order.title)
            .setContentText(order.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(order.summary))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(fullScreen, true)
            .setTimeoutAfter(3 * 60 * 1000L)
            .build()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { NotificationManagerCompat.from(context).notify(ID_ORDER, notification) }
    }

    fun showIncomingOrder(context: Context, title: String, body: String, orderJson: String) {
        showIncomingOrder(context, IncomingOrder.parse(title, body, orderJson))
    }
}
