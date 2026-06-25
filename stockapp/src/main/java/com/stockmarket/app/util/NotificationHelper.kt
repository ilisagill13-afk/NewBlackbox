package com.stockmarket.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.stockmarket.app.MainActivity
import com.stockmarket.app.R

object NotificationHelper {

    private const val CHANNEL_STOP_LOSS = "stop_loss_alerts"
    private const val CHANNEL_TAKE_PROFIT = "take_profit_alerts"
    private const val CHANNEL_NEAR_STOP = "near_stop_alerts"
    private const val CHANNEL_MONITOR = "price_monitor"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)

            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_STOP_LOSS, "🚨 Stop-Loss Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts when stop-loss is triggered and position is sold"
                    enableVibration(true)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_TAKE_PROFIT, "🎯 Take-Profit Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts when take-profit target is reached"
                    enableVibration(true)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_NEAR_STOP, "⚠ Near Stop Warnings", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Warning when price is approaching stop-loss level"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_MONITOR, "Price Monitor", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Background price monitoring service"
                }
            )
        }
    }

    fun notifyStopLossTriggered(
        context: Context,
        symbol: String,
        stopPrice: Double,
        proceeds: Double,
        profitLoss: Double
    ) {
        val plSign = if (profitLoss >= 0) "+" else ""
        val title = "🚨 STOP-LOSS: $symbol SOLD"
        val body = "Position closed at $${"%.2f".format(stopPrice)}\n" +
            "Proceeds: $${"%.2f".format(proceeds)}  |  P/L: $plSign$${"%.2f".format(profitLoss)}"

        sendNotification(context, notifId(symbol, 1), CHANNEL_STOP_LOSS, title, body, priority = NotificationCompat.PRIORITY_MAX)
    }

    fun notifyTakeProfitTriggered(
        context: Context,
        symbol: String,
        sellPrice: Double,
        profit: Double
    ) {
        val title = "🎯 TARGET HIT: $symbol SOLD"
        val body = "Take-profit reached at $${"%.2f".format(sellPrice)}\n" +
            "Profit locked in: +$${"%.2f".format(profit)} 💰"

        sendNotification(context, notifId(symbol, 2), CHANNEL_TAKE_PROFIT, title, body, priority = NotificationCompat.PRIORITY_MAX)
    }

    fun notifyNearStop(
        context: Context,
        symbol: String,
        currentPrice: Double,
        stopPrice: Double,
        distancePct: Double
    ) {
        val title = "⚠ WARNING: $symbol Near Stop-Loss"
        val body = "Price: $${"%.2f".format(currentPrice)}  →  Stop: $${"%.2f".format(stopPrice)}\n" +
            "Only ${"%.1f".format(distancePct)}% away from stop!"

        sendNotification(context, notifId(symbol, 3), CHANNEL_NEAR_STOP, title, body)
    }

    fun notifyTrailingStopMoved(
        context: Context,
        symbol: String,
        newStop: Double,
        currentPrice: Double
    ) {
        val title = "📈 Trailing Stop Updated: $symbol"
        val body = "Price rose to $${"%.2f".format(currentPrice)}\n" +
            "Stop-loss auto-moved up to $${"%.2f".format(newStop)} — profit protected!"

        sendNotification(context, notifId(symbol, 4), CHANNEL_NEAR_STOP, title, body)
    }

    private fun sendNotification(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        body: String,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notif)
        } catch (e: SecurityException) {
            // notification permission not granted
        }
    }

    private fun notifId(symbol: String, type: Int): Int = (symbol.hashCode() and 0x7FFF) * 10 + type
}
