package com.modjust4real.mixora.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PresetStore(context: Context) {
    private val prefs = context.getSharedPreferences("mixora_presets", Context.MODE_PRIVATE)

    fun load(): List<VocalPreset> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    add(array.getJSONObject(index).toPreset())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(preset: VocalPreset) {
        val updated = (load().filterNot { it.id == preset.id } + preset)
            .sortedByDescending { it.createdAt }
        write(updated)
    }

    fun delete(id: Long) {
        write(load().filterNot { it.id == id })
    }

    private fun write(items: List<VocalPreset>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun VocalPreset.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("source", source)
        put("createdAt", createdAt)
        put("suggestedKey", suggestedKey)
        put("suggestedScale", suggestedScale)
        put("notes", notes)
        put("params", params.toJson())
    }

    private fun MixParams.toJson() = JSONObject().apply {
        put("beatGainDb", beatGainDb)
        put("vocalGainDb", vocalGainDb)
        put("highPassHz", highPassHz)
        put("presenceDb", presenceDb)
        put("compression", compression)
        put("saturation", saturation)
        put("reverb", reverb)
        put("stereoWidth", stereoWidth)
        put("tuneAmount", tuneAmount)
        put("tuneRoot", tuneRoot)
        put("tuneScale", tuneScale)
        put("vocalOffsetMs", vocalOffsetMs)
    }

    private fun JSONObject.toPreset(): VocalPreset {
        val params = getJSONObject("params")
        return VocalPreset(
            id = optLong("id", System.currentTimeMillis()),
            name = optString("name", "Preset"),
            source = optString("source", "Ses analizi"),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            suggestedKey = optString("suggestedKey", "C"),
            suggestedScale = optString("suggestedScale", "Minör"),
            notes = optString("notes", ""),
            params = MixParams(
                beatGainDb = params.optDouble("beatGainDb", -3.5).toFloat(),
                vocalGainDb = params.optDouble("vocalGainDb", 1.5).toFloat(),
                highPassHz = params.optDouble("highPassHz", 85.0).toFloat(),
                presenceDb = params.optDouble("presenceDb", 1.5).toFloat(),
                compression = params.optDouble("compression", 0.45).toFloat(),
                saturation = params.optDouble("saturation", 0.12).toFloat(),
                reverb = params.optDouble("reverb", 0.14).toFloat(),
                stereoWidth = params.optDouble("stereoWidth", 1.0).toFloat(),
                tuneAmount = params.optDouble("tuneAmount", 0.55).toFloat(),
                tuneRoot = params.optInt("tuneRoot", 0),
                tuneScale = params.optString("tuneScale", "Kromatik"),
                vocalOffsetMs = params.optDouble("vocalOffsetMs", 0.0).toFloat()
            )
        )
    }

    private companion object {
        const val KEY = "presets"
    }
}
