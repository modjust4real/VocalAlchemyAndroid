package com.modjust4real.mixora.audio

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

object MusicTheory {
    val roots = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val scales = listOf("Majör", "Minör", "Kromatik")

    private val major = setOf(0, 2, 4, 5, 7, 9, 11)
    private val minor = setOf(0, 2, 3, 5, 7, 8, 10)

    fun frequencyToMidi(frequency: Float): Float {
        if (frequency <= 0f) return -1f
        return (69f + 12f * (ln(frequency / 440f) / ln(2f)))
    }

    fun midiToFrequency(midi: Float): Float =
        (440.0 * 2.0.pow((midi - 69.0) / 12.0)).toFloat()

    fun nearestAllowedMidi(midi: Float, root: Int, scale: String): Int {
        if (midi < 0f) return -1
        if (scale == "Kromatik") return midi.roundToInt()
        val allowed = if (scale == "Majör") major else minor
        val center = midi.roundToInt()
        return (-12..12)
            .map { center + it }
            .filter { candidate -> ((candidate - root) % 12 + 12) % 12 in allowed }
            .minByOrNull { candidate -> kotlin.math.abs(candidate - midi) }
            ?: center
    }

    fun noteName(midi: Int): String {
        if (midi < 0) return "—"
        val note = roots[((midi % 12) + 12) % 12]
        return "$note${midi / 12 - 1}"
    }
}
