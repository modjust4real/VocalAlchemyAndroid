package com.modjust4real.mixora.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

object PitchDetector {
    fun detect(samples: FloatArray, sampleRate: Int): Float {
        if (samples.size < 256) return 0f
        val downsample = 4
        val count = samples.size / downsample
        if (count < 128) return 0f
        val work = FloatArray(count)
        var mean = 0f
        for (i in 0 until count) {
            val value = samples[i * downsample]
            work[i] = value
            mean += value
        }
        mean /= count
        var energy = 0f
        for (i in work.indices) {
            work[i] -= mean
            energy += work[i] * work[i]
        }
        if (energy / count < 0.00004f) return 0f

        val reducedRate = sampleRate / downsample
        val minLag = max(2, reducedRate / 900)
        val maxLag = min(count / 2, reducedRate / 65)
        var bestLag = -1
        var bestCorrelation = 0f

        for (lag in minLag..maxLag) {
            var sum = 0f
            var leftEnergy = 0f
            var rightEnergy = 0f
            val limit = count - lag
            for (i in 0 until limit) {
                val a = work[i]
                val b = work[i + lag]
                sum += a * b
                leftEnergy += a * a
                rightEnergy += b * b
            }
            val normalizer = kotlin.math.sqrt(leftEnergy * rightEnergy).coerceAtLeast(0.000001f)
            val correlation = sum / normalizer
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestLag = lag
            }
        }

        if (bestLag < 0 || bestCorrelation < 0.42f) return 0f
        return reducedRate.toFloat() / bestLag
    }
}

class PitchCorrectionProcessor(
    private val sampleRate: Int,
    root: Int,
    scale: String,
    amount: Float,
    private val onPitch: ((detectedHz: Float, targetMidi: Int) -> Unit)? = null
) {
    private val analysis = FloatArray(2048)
    private var analysisIndex = 0
    private val shifter = DualHeadPitchShifter((sampleRate * 0.045f).toInt())
    private var smoothedRatio = 1f

    @Volatile var root: Int = root
    @Volatile var scale: String = scale
    @Volatile var amount: Float = amount

    fun process(input: Float): Float {
        analysis[analysisIndex++] = input
        if (analysisIndex == analysis.size) {
            val detected = PitchDetector.detect(analysis, sampleRate)
            if (detected > 0f) {
                val midi = MusicTheory.frequencyToMidi(detected)
                val target = MusicTheory.nearestAllowedMidi(midi, root, scale)
                val rawRatio = if (target >= 0) {
                    (MusicTheory.midiToFrequency(target.toFloat()) / detected).coerceIn(0.78f, 1.28f)
                } else {
                    1f
                }
                smoothedRatio = 1f + (rawRatio - 1f) * amount.coerceIn(0f, 1f)
                onPitch?.invoke(detected, target)
            } else {
                smoothedRatio = 1f
                onPitch?.invoke(0f, -1)
            }
            analysisIndex = 0
        }

        val wet = shifter.process(input, smoothedRatio)
        val mix = amount.coerceIn(0f, 1f)
        return input * (1f - mix) + wet * mix
    }
}

private class DualHeadPitchShifter(maxDelaySamples: Int) {
    private val maxDelay = maxDelaySamples.coerceAtLeast(256)
    private val ring = FloatArray(maxDelay * 2 + 8)
    private var writeIndex = 0
    private var phase = 0.25f

    fun process(input: Float, ratio: Float): Float {
        ring[writeIndex] = input
        if (abs(ratio - 1f) < 0.0015f) {
            writeIndex = (writeIndex + 1) % ring.size
            return input
        }

        phase += (1f - ratio) / maxDelay
        while (phase < 0f) phase += 1f
        while (phase >= 1f) phase -= 1f

        val phase2 = (phase + 0.5f) % 1f
        val window1 = (0.5 - 0.5 * cos(2.0 * PI * phase)).toFloat()
        val window2 = (0.5 - 0.5 * cos(2.0 * PI * phase2)).toFloat()
        val first = readDelayed(32f + phase * maxDelay)
        val second = readDelayed(32f + phase2 * maxDelay)
        writeIndex = (writeIndex + 1) % ring.size
        return (first * window1 + second * window2) / (window1 + window2).coerceAtLeast(0.01f)
    }

    private fun readDelayed(delay: Float): Float {
        var position = writeIndex - delay
        while (position < 0f) position += ring.size
        val left = position.toInt() % ring.size
        val right = (left + 1) % ring.size
        val fraction = position - position.toInt()
        return ring[left] * (1f - fraction) + ring[right] * fraction
    }
}
