package com.kunal.goldtracker

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

private const val GRAMS_PER_TROY_OUNCE = 31.1034768

data class SpotPrice(val inrPerGram: Double, val source: String)

/** Estimated Indian prices in rupees per gram. */
data class GoldPrices(val k24: Double, val k22: Double, val k18: Double) {
    fun forKarat(karat: Int): Double = when (karat) {
        22 -> k22
        18 -> k18
        else -> k24
    }
}

object GoldRepository {

    /** Fetches spot, stores it and runs the alert check. Used by both the screen and the worker. */
    suspend fun refresh(context: Context, prefs: Prefs) = withContext(Dispatchers.IO) {
        prefs.saveSpot(fetchSpot())
        Alerts.check(context, prefs)
    }

    fun toIndianPrices(spotInrPerGram: Double, s: PriceSettings): GoldPrices {
        var k24 = spotInrPerGram * (1 + s.dutyPercent / 100) * (1 + s.adjustPercent / 100)
        if (s.includeGst) k24 *= 1.03
        return GoldPrices(k24 = k24, k22 = k24 * 22 / 24, k18 = k24 * 18 / 24)
    }

    private fun fetchSpot(): SpotPrice {
        val errors = mutableListOf<String>()
        try {
            return fromGoldApi()
        } catch (e: Exception) {
            errors += "gold-api: ${e.message}"
        }
        try {
            return fromCurrencyApi()
        } catch (e: Exception) {
            errors += "currency-api: ${e.message}"
        }
        throw IOException(errors.joinToString("; "))
    }

    // Live international spot (USD/oz) converted at the latest USD/INR rate.
    private fun fromGoldApi(): SpotPrice {
        val usdPerOz = JSONObject(get("https://api.gold-api.com/price/XAU")).getDouble("price")
        val usdInr = JSONObject(get("https://open.er-api.com/v6/latest/USD"))
            .getJSONObject("rates").getDouble("INR")
        return SpotPrice(usdPerOz * usdInr / GRAMS_PER_TROY_OUNCE, "gold-api.com + open.er-api.com")
    }

    // Fallback that quotes XAU directly in INR, but only updates once a day.
    private fun fromCurrencyApi(): SpotPrice {
        val inrPerOz = JSONObject(
            get("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/xau.json")
        ).getJSONObject("xau").getDouble("inr")
        return SpotPrice(inrPerOz / GRAMS_PER_TROY_OUNCE, "currency-api (daily)")
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "GoldTracker-Android")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/** ₹1,23,456 style formatting. */
fun formatInr(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }.format(value)
