package com.okeybot.calibration

import android.content.Context
import android.graphics.RectF
import org.json.JSONObject

enum class Region { RACK, DRAW_PILE, DISCARD, INDICATOR }
/** Regions are normalized screen fractions and remain valid across density/resolution changes. */
class CalibrationStore(context: Context) {
    private val prefs = context.getSharedPreferences("calibration", Context.MODE_PRIVATE)
    fun put(region: Region, rect: RectF) { prefs.edit().putString(region.name, JSONObject().put("l", rect.left).put("t", rect.top).put("r", rect.right).put("b", rect.bottom).toString()).apply() }
    fun get(region: Region): RectF? = prefs.getString(region.name, null)?.let { runCatching { JSONObject(it).let { j -> RectF(j.getDouble("l").toFloat(), j.getDouble("t").toFloat(), j.getDouble("r").toFloat(), j.getDouble("b").toFloat()) } }.getOrNull() }
    fun all(): Map<Region, RectF> = Region.values().mapNotNull { r -> get(r)?.let { r to it } }.toMap()
    fun complete() = Region.values().all { get(it) != null }
}
