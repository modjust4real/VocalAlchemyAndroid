package com.modjust4real.mixora.audio

import android.content.Context
import android.net.Uri
import com.modjust4real.mixora.data.AudioFeatures
import com.modjust4real.mixora.data.MixParams
import com.modjust4real.mixora.data.VocalPreset
import com.modjust4real.mixora.data.amplitudeToDb
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

object AudioAnalyzer {
    fun analyze(context: Context, uri: Uri, maxSeconds: Int = 60): AudioFeatures {
        val audio = AudioDecoder.decode(context, uri, maxSeconds)
        val stride = (audio.sampleRate / 12_000).coerceAtLeast(1)
        var squared = 0.0
        var derivative = 0.0
        var peak = 0f
        var previous = 0f
        var count = 0

        var frame = 0
        while (frame < audio.frameCount) {
            val value = monoAt(audio, frame)
            squared += value * value
            derivative += (value - previous) * (value - previous)
            peak = maxOf(peak, abs(value))
            previous = value
            count++
            frame += stride
        }

        val rms = sqrt(squared / count.coerceAtLeast(1)).toFloat()
        val derivativeRms = sqrt(derivative / count.coerceAtLeast(1)).toFloat()
        val brightness = (derivativeRms / (rms + 0.0001f)).coerceIn(0f, 1.5f) / 1.5f
        val pitches = mutableListOf<Float>()
        val window = FloatArray(2048)
        val windowCount = 16
        for (index in 0 until windowCount) {
            val start = ((audio.frameCount - window.size).coerceAtLeast(1) *
                index.toFloat() / windowCount).roundToInt()
            for (i in window.indices) {
                window[i] = monoAt(audio, (start + i).coerceAtMost(audio.frameCount - 1))
            }
            val pitch = PitchDetector.detect(window, audio.sampleRate)
            if (pitch in 65f..900f) pitches += pitch
        }
        val medianPitch = pitches.sorted().let { sorted ->
            if (sorted.isEmpty()) 0f else sorted[sorted.size / 2]
        }
        val rmsDb = amplitudeToDb(rms)
        val peakDb = amplitudeToDb(peak)
        return AudioFeatures(
            durationSeconds = audio.durationSeconds,
            rmsDb = rmsDb,
            peakDb = peakDb,
            brightness = brightness,
            dynamics = (peakDb - rmsDb).coerceIn(0f, 30f),
            medianPitchHz = medianPitch
        )
    }

    fun createPreset(
        name: String,
        source: String,
        features: AudioFeatures
    ): VocalPreset {
        val root = if (features.medianPitchHz > 0f) {
            MusicTheory.frequencyToMidi(features.medianPitchHz).roundToInt().mod(12)
        } else {
            0
        }
        val highPass = if (features.medianPitchHz in 1f..155f) 72f else 98f
        val presence = when {
            features.brightness < 0.18f -> 2.8f
            features.brightness > 0.55f -> -1.2f
            else -> 1.2f
        }
        val compression = ((features.dynamics - 7f) / 15f).coerceIn(0.25f, 0.82f)
        val vocalGain = ((-18f - features.rmsDb) * 0.35f).coerceIn(-2f, 5f)
        val params = MixParams(
            vocalGainDb = vocalGain,
            highPassHz = highPass,
            presenceDb = presence,
            compression = compression,
            saturation = if (features.brightness < 0.35f) 0.18f else 0.1f,
            reverb = 0.12f,
            stereoWidth = 1.08f,
            tuneAmount = 0.58f,
            tuneRoot = root,
            tuneScale = "Minör"
        )
        return VocalPreset(
            name = name.ifBlank { "Yeni vokal preset" },
            source = source,
            params = params,
            suggestedKey = MusicTheory.roots[root],
            suggestedScale = "Minör",
            notes = "RMS %.1f dB • Dinamik %.1f dB • Parlaklık %%%d".format(
                features.rmsDb,
                features.dynamics,
                (features.brightness * 100).roundToInt()
            )
        )
    }

    private fun monoAt(audio: PcmAudio, frame: Int): Float {
        if (audio.frameCount == 0) return 0f
        val safeFrame = frame.coerceIn(0, audio.frameCount - 1)
        var sum = 0f
        for (channel in 0 until audio.channels) {
            sum += audio.samples[safeFrame * audio.channels + channel] / 32768f
        }
        return sum / audio.channels.coerceAtLeast(1)
    }
}
