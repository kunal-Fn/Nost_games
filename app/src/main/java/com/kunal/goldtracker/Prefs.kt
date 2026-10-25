package com.kunal.goldtracker

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

data class PriceSettings(
    val dutyPercent: Double,
    val adjustPercent: Double,
    val includeGst: Boolean,
)

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("gold_prefs", Context.MODE_PRIVATE)

    // Alert settings. Limits are in rupees per 10 g; 0 means that limit is off.
    var alertsEnabled: Boolean
        get() = sp.getBoolean("alerts_enabled", false)
        set(v) = sp.edit { putBoolean("alerts_enabled", v) }
    var upperLimit: Double
        get() = getDouble("upper_limit", 0.0)
        set(v) = putDouble("upper_limit", v)
    var lowerLimit: Double
        get() = getDouble("lower_limit", 0.0)
        set(v) = putDouble("lower_limit", v)
    var alertKarat: Int
        get() = sp.getInt("alert_karat", 24)
        set(v) = sp.edit { putInt("alert_karat", v) }
    var trendKarat: Int
        get() = sp.getInt("trend_karat", 24)
        set(v) = sp.edit { putInt("trend_karat", v) }
    var widgetKarat: Int
        get() = sp.getInt("widget_karat", 24)
        set(v) = sp.edit { putInt("widget_karat", v) }
    var intervalMinutes: Int
        get() = sp.getInt("interval_minutes", 30)
        set(v) = sp.edit { putInt("interval_minutes", v) }

    // Set once an alert has fired, cleared when the price moves back, so we notify once per crossing.
    var upperFired: Boolean
        get() = sp.getBoolean("upper_fired", false)
        set(v) = sp.edit { putBoolean("upper_fired", v) }
    var lowerFired: Boolean
        get() = sp.getBoolean("lower_fired", false)
        set(v) = sp.edit { putBoolean("lower_fired", v) }

    // How spot is turned into an Indian rate. Duty is 10% BCD + 5% AIDC since 13 May 2026.
    // GST is off by default because jewellers and IBJA quote rates before GST.
    var priceSettings: PriceSettings
        get() = PriceSettings(
            dutyPercent = getDouble("duty_percent", 15.0),
            adjustPercent = getDouble("adjust_percent", 0.0),
            includeGst = sp.getBoolean("include_gst", false),
        )
        set(v) {
            putDouble("duty_percent", v.dutyPercent)
            putDouble("adjust_percent", v.adjustPercent)
            sp.edit { putBoolean("include_gst", v.includeGst) }
        }

    // Last fetched spot price, stored raw so changing duty/GST recalculates without a refetch.
    val lastSpotInrPerGram: Double get() = getDouble("last_spot", 0.0)
    val lastUpdated: Long get() = sp.getLong("last_updated", 0L)
    val lastSource: String get() = sp.getString("last_source", "") ?: ""
    var lastPrevClose: Double
        get() = getDouble("last_prev_close", 0.0)
        set(v) = putDouble("last_prev_close", v)

    fun saveSpot(spot: SpotPrice) {
        putDouble("last_spot", spot.inrPerGram)
        sp.edit {
            putLong("last_updated", System.currentTimeMillis())
            putString("last_source", spot.source)
        }
    }

    private fun getDouble(key: String, default: Double): Double =
        Double.fromBits(sp.getLong(key, default.toRawBits()))

    private fun putDouble(key: String, value: Double) =
        sp.edit { putLong(key, value.toRawBits()) }
}
