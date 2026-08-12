package com.modjust4real.mixora.audio

import android.content.Context
import android.net.Uri
import com.modjust4real.mixora.data.MixParams
import com.modjust4real.mixora.data.RenderResult
import com.modjust4real.mixora.data.amplitudeToDb
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

object MixEngine {
    private const val OUTPUT_RATE = 44_100
    private const val MAX_SECONDS = 360

    fun render(
        context: Context,
        beatUri: Uri,
        vocalUri: Uri,
        params: MixParams,
        onProgress: (Float) -> Unit = {}
    ): RenderResult {
        onProgress(0.02f)
        val beat = AudioDecoder.decode(context, beatUri, MAX_SECONDS)
        onProgress(0.12f)
        val vocal = AudioDecoder.decode(context, vocalUri, MAX_SECONDS)
        onProgress(0.22f)

        val offsetFrames = (params.vocalOffsetMs * OUTPUT_RATE / 1000f).toInt().coerceAtLeast(0)
        val beatFrames = (beat.durationSeconds * OUTPUT_RATE).toInt()
        val vocalFrames = (vocal.durationSeconds * OUTPUT_RATE).toInt() + offsetFrames
        val outputFrames = maxOf(beatFrames, vocalFrames).coerceAtMost(MAX_SECONDS * OUTPUT_RATE)
        require(outputFrames > 0) { "Mix için ses örneği bulunamadı." }

        val beatAutoDb = (-15f - estimateRmsDb(beat)).coerceIn(-8f, 8f)
        val vocalAutoDb = (-20f - estimateRmsDb(vocal)).coerceIn(-8f, 8f)
        val beatGain = dbToLinear(beatAutoDb + params.beatGainDb)
        val vocalGain = dbToLinear(vocalAutoDb + params.vocalGainDb)

        val output = File(context.cacheDir, "mixora_${System.currentTimeMillis()}.wav")
        val stream = BufferedOutputStream(FileOutputStream(output), 64 * 1024)
        writeWavHeader(stream, OUTPUT_RATE, 2, outputFrames * 4)

        val highPassL = OnePoleHighPass(params.highPassHz, OUTPUT_RATE)
        val highPassR = OnePoleHighPass(params.highPassHz, OUTPUT_RATE)
        val presenceL = PresenceFilter(params.presenceDb, OUTPUT_RATE)
        val presenceR = PresenceFilter(params.presenceDb, OUTPUT_RATE)
        val compressor = StereoCompressor(params.compression, OUTPUT_RATE)
        val pitch = PitchCorrectionProcessor(
            OUTPUT_RATE,
            params.tuneRoot,
            params.tuneScale,
            params.tuneAmount
        )
        val widener = HaasWidener(OUTPUT_RATE)
        val reverb = SimpleStereoReverb(OUTPUT_RATE)
        val bytes = ByteArray(16 * 1024)
        var byteIndex = 0
        var peak = 0f
        var lastProgress = 0

        try {
            for (frame in 0 until outputFrames) {
                val beatL = sampleAt(beat, frame, 0, OUTPUT_RATE) * beatGain
                val beatR = sampleAt(beat, frame, 1, OUTPUT_RATE) * beatGain
                val vocalFrame = frame - offsetFrames
                val rawVocalL = if (vocalFrame >= 0) sampleAt(vocal, vocalFrame, 0, OUTPUT_RATE) else 0f
                val rawVocalR = if (vocalFrame >= 0) sampleAt(vocal, vocalFrame, 1, OUTPUT_RATE) else 0f

                var vocalL = presenceL.process(highPassL.process(rawVocalL))
                var vocalR = presenceR.process(highPassR.process(rawVocalR))
                val compressed = compressor.process(vocalL, vocalR)
                vocalL = compressed.first
                vocalR = compressed.second

                val mid = (vocalL + vocalR) * 0.5f
                val side = (vocalL - vocalR) * 0.5f * params.stereoWidth
                val tunedMid = pitch.process(mid)
                val widened = widener.process(tunedMid, params.stereoWidth)
                vocalL = (widened.first + side) * vocalGain
                vocalR = (widened.second - side) * vocalGain

                val saturationDrive = 1f + params.saturation * 4f
                vocalL = softSaturate(vocalL, saturationDrive)
                vocalR = softSaturate(vocalR, saturationDrive)
                val wet = reverb.process(vocalL, vocalR)
                vocalL = vocalL * (1f - params.reverb) + wet.first * params.reverb
                vocalR = vocalR * (1f - params.reverb) + wet.second * params.reverb

                val outL = limiter(beatL + vocalL)
                val outR = limiter(beatR + vocalR)
                peak = maxOf(peak, abs(outL), abs(outR))
                val shortL = (outL.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                val shortR = (outR.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                bytes[byteIndex++] = (shortL.toInt() and 0xff).toByte()
                bytes[byteIndex++] = ((shortL.toInt() ushr 8) and 0xff).toByte()
                bytes[byteIndex++] = (shortR.toInt() and 0xff).toByte()
                bytes[byteIndex++] = ((shortR.toInt() ushr 8) and 0xff).toByte()
                if (byteIndex == bytes.size) {
                    stream.write(bytes)
                    byteIndex = 0
                }

                val percent = frame * 100 / outputFrames
                if (percent >= lastProgress + 2) {
                    lastProgress = percent
                    onProgress(0.22f + percent / 100f * 0.76f)
                }
            }
            if (byteIndex > 0) stream.write(bytes, 0, byteIndex)
        } finally {
            stream.close()
        }
        onProgress(1f)
        return RenderResult(
            filePath = output.absolutePath,
            durationSeconds = outputFrames.toFloat() / OUTPUT_RATE,
            peakDb = amplitudeToDb(peak),
            appliedSummary = "Seviye eşleme • HPF ${params.highPassHz.toInt()} Hz • " +
                "kompresyon • tonal denge • ${params.tuneScale} pitch correction • limiter"
        )
    }

    private fun sampleAt(audio: PcmAudio, outputFrame: Int, channel: Int, outputRate: Int): Float {
        if (audio.frameCount == 0) return 0f
        val position = outputFrame.toDouble() * audio.sampleRate / outputRate
        val leftFrame = position.toInt().coerceIn(0, audio.frameCount - 1)
        val rightFrame = (leftFrame + 1).coerceAtMost(audio.frameCount - 1)
        val fraction = (position - leftFrame).toFloat()
        fun value(frame: Int): Float {
            val sourceChannel = when {
                audio.channels == 1 -> 0
                channel < audio.channels -> channel
                else -> audio.channels - 1
            }
            return audio.samples[frame * audio.channels + sourceChannel] / 32768f
        }
        return value(leftFrame) * (1f - fraction) + value(rightFrame) * fraction
    }

    private fun estimateRmsDb(audio: PcmAudio): Float {
        val stride = (audio.frameCount / 150_000).coerceAtLeast(1)
        var sum = 0.0
        var count = 0
        var frame = 0
        while (frame < audio.frameCount) {
            for (channel in 0 until audio.channels) {
                val value = audio.samples[frame * audio.channels + channel] / 32768.0
                sum += value * value
                count++
            }
            frame += stride
        }
        return amplitudeToDb(sqrt(sum / count.coerceAtLeast(1)).toFloat())
    }

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    private fun softSaturate(value: Float, drive: Float): Float =
        (tanh((value * drive).toDouble()) / tanh(drive.toDouble())).toFloat()

    private fun limiter(value: Float): Float =
        (tanh((value * 1.35f).toDouble()) / tanh(1.35)).toFloat() * 0.91f

    private fun writeWavHeader(
        stream: BufferedOutputStream,
        sampleRate: Int,
        channels: Int,
        dataSize: Int
    ) {
        val byteRate = sampleRate * channels * 2
        val header = ByteArray(44)
        fun ascii(offset: Int, text: String) = text.forEachIndexed { i, c -> header[offset + i] = c.code.toByte() }
        fun littleInt(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = (value ushr 8 and 0xff).toByte()
            header[offset + 2] = (value ushr 16 and 0xff).toByte()
            header[offset + 3] = (value ushr 24 and 0xff).toByte()
        }
        fun littleShort(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = (value ushr 8 and 0xff).toByte()
        }
        ascii(0, "RIFF")
        littleInt(4, 36 + dataSize)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        littleInt(16, 16)
        littleShort(20, 1)
        littleShort(22, channels)
        littleInt(24, sampleRate)
        littleInt(28, byteRate)
        littleShort(32, channels * 2)
        littleShort(34, 16)
        ascii(36, "data")
        littleInt(40, dataSize)
        stream.write(header)
    }
}

private class OnePoleHighPass(frequency: Float, sampleRate: Int) {
    private val alpha = exp((-2.0 * PI * frequency / sampleRate)).toFloat()
    private var previousInput = 0f
    private var previousOutput = 0f

    fun process(input: Float): Float {
        val output = alpha * (previousOutput + input - previousInput)
        previousInput = input
        previousOutput = output
        return output
    }
}

private class PresenceFilter(db: Float, sampleRate: Int) {
    private val amount = (10f.pow(db / 20f) - 1f).coerceIn(-0.6f, 1f)
    private val alpha = (2f * PI.toFloat() * 3_200f / sampleRate).coerceAtMost(0.45f)
    private var low = 0f

    fun process(input: Float): Float {
        low += alpha * (input - low)
        return input + (input - low) * amount
    }
}

private class StereoCompressor(amount: Float, sampleRate: Int) {
    private val strength = amount.coerceIn(0f, 1f)
    private val release = exp(-1.0 / (sampleRate * 0.08)).toFloat()
    private var envelope = 0f

    fun process(left: Float, right: Float): Pair<Float, Float> {
        val level = maxOf(abs(left), abs(right))
        envelope = maxOf(level, envelope * release)
        val threshold = 0.24f - strength * 0.12f
        val gain = if (envelope > threshold) {
            (threshold / envelope).pow(strength * 0.72f)
        } else {
            1f
        }
        val makeup = 1f + strength * 0.28f
        return left * gain * makeup to right * gain * makeup
    }
}

private class HaasWidener(sampleRate: Int) {
    private val delay = FloatArray((sampleRate * 0.012f).toInt().coerceAtLeast(16))
    private var index = 0

    fun process(input: Float, width: Float): Pair<Float, Float> {
        val delayed = delay[index]
        delay[index] = input
        index = (index + 1) % delay.size
        val amount = ((width - 1f) / 0.6f).coerceIn(0f, 1f) * 0.34f
        return input to (input * (1f - amount) + delayed * amount)
    }
}

private class SimpleStereoReverb(sampleRate: Int) {
    private val leftDelay = FloatArray((sampleRate * 0.071f).toInt())
    private val rightDelay = FloatArray((sampleRate * 0.103f).toInt())
    private var leftIndex = 0
    private var rightIndex = 0

    fun process(left: Float, right: Float): Pair<Float, Float> {
        val wetL = leftDelay[leftIndex]
        val wetR = rightDelay[rightIndex]
        leftDelay[leftIndex] = left + wetR * 0.36f
        rightDelay[rightIndex] = right + wetL * 0.39f
        leftIndex = (leftIndex + 1) % leftDelay.size
        rightIndex = (rightIndex + 1) % rightDelay.size
        return wetL to wetR
    }
}
