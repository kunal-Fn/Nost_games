package com.kunal.goldtracker

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** A piece of gold the user owns. [paid] is the total amount paid in rupees. */
data class Holding(val id: Long, val name: String, val grams: Double, val karat: Int, val paid: Double)

object Holdings {
    fun load(context: Context): List<Holding> {
        val raw = prefs(context).getString("holdings", null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).map {
                val o = json.getJSONObject(it)
                Holding(o.getLong("id"), o.getString("name"), o.getDouble("grams"), o.getInt("karat"), o.getDouble("paid"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, items: List<Holding>) {
        val json = JSONArray()
        items.forEach {
            json.put(
                JSONObject().put("id", it.id).put("name", it.name).put("grams", it.grams)
                    .put("karat", it.karat).put("paid", it.paid)
            )
        }
        prefs(context).edit { putString("holdings", json.toString()) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("holdings", Context.MODE_PRIVATE)
}
