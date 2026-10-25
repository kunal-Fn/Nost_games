package com.kunal.goldtracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

/** Home-screen widget showing the 24K rate per 10 g and the change from the previous close. */
class PriceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, buildViews(context))
    }

    companion object {
        /** Called after every refresh and when price settings change. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PriceWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildViews(context))
        }

        private fun buildViews(context: Context): RemoteViews {
            val prefs = Prefs(context)
            val views = RemoteViews(context.packageName, R.layout.widget_price)
            val openApp = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)

            val spot = prefs.lastSpotInrPerGram
            if (spot <= 0) {
                views.setTextViewText(R.id.widget_price, "Open app to load")
                views.setTextViewText(R.id.widget_change, "")
                views.setTextViewText(R.id.widget_updated, "")
                return views
            }

            val settings = prefs.priceSettings
            val per10g = GoldRepository.toIndianPrices(spot, settings).k24 * 10
            views.setTextViewText(R.id.widget_price, formatInr(per10g))

            val prevSpot = prefs.lastPrevClose
            if (prevSpot > 0) {
                val prev10g = GoldRepository.toIndianPrices(prevSpot, settings).k24 * 10
                val change = per10g - prev10g
                val rising = change >= 0
                val text = "${if (rising) "▲" else "▼"} ${formatInr(abs(change))} " +
                    "(${if (rising) "+" else "-"}${"%.2f".format(abs(change) / prev10g * 100)}%)"
                views.setTextViewText(R.id.widget_change, text)
                views.setTextColor(R.id.widget_change, if (rising) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
            } else {
                views.setTextViewText(R.id.widget_change, "")
            }
            views.setTextViewText(
                R.id.widget_updated,
                "Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(prefs.lastUpdated))}",
            )
            return views
        }
    }
}
