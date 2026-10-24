package com.kunal.goldtracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Alerts {
    private const val CHANNEL_ID = "price_alerts"
    private const val ID_UPPER = 1
    private const val ID_LOWER = 2

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Gold price alerts", NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Compares the last stored price against the limits and notifies once per crossing. */
    fun check(context: Context, prefs: Prefs) {
        if (!prefs.alertsEnabled || prefs.lastSpotInrPerGram <= 0) return
        val karat = prefs.alertKarat
        val per10g = GoldRepository.toIndianPrices(prefs.lastSpotInrPerGram, prefs.priceSettings)
            .forKarat(karat) * 10

        val upper = prefs.upperLimit
        if (upper > 0) {
            if (per10g >= upper) {
                if (!prefs.upperFired) {
                    notify(context, ID_UPPER, "${karat}K gold above ${formatInr(upper)}",
                        "Now ${formatInr(per10g)} per 10 g")
                    prefs.upperFired = true
                }
            } else {
                prefs.upperFired = false
            }
        }

        val lower = prefs.lowerLimit
        if (lower > 0) {
            if (per10g <= lower) {
                if (!prefs.lowerFired) {
                    notify(context, ID_LOWER, "${karat}K gold below ${formatInr(lower)}",
                        "Now ${formatInr(per10g)} per 10 g")
                    prefs.lowerFired = true
                }
            } else {
                prefs.lowerFired = false
            }
        }
    }

    private fun notify(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(context)
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gold)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
