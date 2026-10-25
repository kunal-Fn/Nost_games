package com.kunal.goldtracker

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset

/** Spot price in rupees per gram at a point in time. */
data class PricePoint(val time: Long, val spotInrPerGram: Double)

// stepDays thins out the longer ranges so 6M needs ~60 downloads instead of ~180.
enum class TrendRange(val label: String, val days: Long, val stepDays: Long) {
    DAY("1D", 1, 0),
    WEEK("1W", 7, 1),
    MONTH("1M", 30, 1),
    THREE_MONTHS("3M", 91, 2),
    SIX_MONTHS("6M", 182, 3),
}

object History {
    private const val SAMPLE_KEEP_MS = 48 * 60 * 60 * 1000L

    /** Stores the app's own price checks; this is the only source for the 1D chart. */
    fun recordSample(context: Context, spotInrPerGram: Double) {
        val now = System.currentTimeMillis()
        val kept = readSamples(context).filter { now - it.time < SAMPLE_KEEP_MS } +
            PricePoint(now, spotInrPerGram)
        val json = JSONArray()
        kept.forEach { json.put(JSONArray().put(it.time).put(it.spotInrPerGram)) }
        samplePrefs(context).edit { putString("samples", json.toString()) }
    }

    suspend fun load(context: Context, range: TrendRange): List<PricePoint> =
        if (range == TrendRange.DAY) {
            val since = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            readSamples(context).filter { it.time >= since }
        } else {
            daily(context, range)
        }

    /** Latest daily close: today's snapshot (published around 00:00 UTC), else yesterday's. */
    suspend fun previousClose(context: Context): Double? = withContext(Dispatchers.IO) {
        val today = LocalDate.now(ZoneOffset.UTC)
        dayPrice(context, today) ?: dayPrice(context, today.minusDays(1))
    }

    private suspend fun daily(context: Context, range: TrendRange): List<PricePoint> =
        withContext(Dispatchers.IO) {
            val today = LocalDate.now(ZoneOffset.UTC)
            val dates = (range.days downTo 0 step range.stepDays).map { today.minusDays(it) }.toMutableList()
            if (dates.last() != today) dates += today
            coroutineScope {
                dates.chunked(8).flatMap { chunk ->
                    chunk.map { date -> async { date to dayPrice(context, date) } }.awaitAll()
                }
            }.mapNotNull { (date, price) ->
                price?.let { PricePoint(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), it) }
            }
        }

    // Past days never change, so they are cached on the phone and downloaded only once.
    private fun dayPrice(context: Context, date: LocalDate): Double? {
        val cache = context.getSharedPreferences("daily_cache", Context.MODE_PRIVATE)
        val key = date.toString()
        if (cache.contains(key)) return Double.fromBits(cache.getLong(key, 0L))

        val urls = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$key/v1/currencies/xau.json",
            "https://$key.currency-api.pages.dev/v1/currencies/xau.json",
        )
        for (url in urls) {
            try {
                val perGram = JSONObject(httpGet(url)).getJSONObject("xau").getDouble("inr") / GRAMS_PER_TROY_OUNCE
                if (date.isBefore(LocalDate.now(ZoneOffset.UTC))) {
                    cache.edit { putLong(key, perGram.toRawBits()) }
                }
                return perGram
            } catch (e: Exception) {
                // try the mirror, then give up on this day
            }
        }
        return null
    }

    private fun readSamples(context: Context): List<PricePoint> {
        val raw = samplePrefs(context).getString("samples", null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).map {
                val item = json.getJSONArray(it)
                PricePoint(item.getLong(0), item.getDouble(1))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun samplePrefs(context: Context) =
        context.applicationContext.getSharedPreferences("samples", Context.MODE_PRIVATE)
}
