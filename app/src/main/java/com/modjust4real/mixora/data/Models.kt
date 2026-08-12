package com.modjust4real.mixora.data

import android.net.Uri
import kotlin.math.log10

data class AudioFeatures(
    val durationSeconds: Float,
    val rmsDb: Float,
    val peakDb: Float,
    val brightness: Float,
    val dynamics: Float,
    val medianPitchHz: Float
)

data class MixParams(
    val beatGainDb: Float = -3.5f,
    val vocalGainDb: Float = 1.5f,
    val highPassHz: Float = 85f,
    val presenceDb: Float = 1.5f,
    val compression: Float = 0.45f,
    val saturation: Float = 0.12f,
    val reverb: Float = 0.14f,
    val stereoWidth: Float = 1.0f,
    val tuneAmount: Float = 0.55f,
    val tuneRoot: Int = 0,
    val tuneScale: String = "Kromatik",
    val vocalOffsetMs: Float = 0f
)

data class VocalPreset(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val source: String,
    val createdAt: Long = System.currentTimeMillis(),
    val params: MixParams,
    val suggestedKey: String = "C",
    val suggestedScale: String = "Minör",
    val notes: String = ""
)

data class MixSelection(
    val beatUri: Uri? = null,
    val beatName: String = "",
    val vocalUri: Uri? = null,
    val vocalName: String = ""
)

data class RenderResult(
    val filePath: String,
    val durationSeconds: Float,
    val peakDb: Float,
    val appliedSummary: String
)

fun amplitudeToDb(value: Float): Float =
    if (value <= 0.000001f) -120f else (20f * log10(value.toDouble())).toFloat()
