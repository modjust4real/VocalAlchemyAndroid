package com.modjust4real.mixora.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

data class PcmAudio(
    val sampleRate: Int,
    val channels: Int,
    val samples: ShortArray
) {
    val frameCount: Int get() = if (channels == 0) 0 else samples.size / channels
    val durationSeconds: Float get() = frameCount.toFloat() / sampleRate.coerceAtLeast(1)
}

object AudioDecoder {
    fun decode(context: Context, uri: Uri, maxSeconds: Int = 360): PcmAudio {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("Bu dosyada okunabilir bir ses kanalı bulunamadı.")

            extractor.selectTrack(trackIndex)
            val sourceFormat = extractor.getTrackFormat(trackIndex)
            val mime = sourceFormat.getString(MediaFormat.KEY_MIME)
                ?: error("Ses biçimi tanınamadı.")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(sourceFormat, null, null, 0)
            codec.start()

            var outputRate = sourceFormat.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var outputChannels = sourceFormat.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, 1)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            val maxSamples = maxSeconds * outputRate * outputChannels
            val builder = ShortArrayBuilder(minOf(maxSamples, outputRate * outputChannels * 30))
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false

            while (!outputEnded && builder.size < maxSamples) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: error("Ses çözücü giriş tamponu açılamadı.")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        outputRate = outputFormat.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, outputRate)
                        outputChannels = outputFormat.getIntegerOr(
                            MediaFormat.KEY_CHANNEL_COUNT,
                            outputChannels
                        )
                        pcmEncoding = outputFormat.getIntegerOr(
                            MediaFormat.KEY_PCM_ENCODING,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER,
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && info.size > 0) {
                            val view = outputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                            view.position(info.offset)
                            view.limit(info.offset + info.size)
                            if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                while (view.remaining() >= 4 && builder.size < maxSamples) {
                                    val value = view.float.coerceIn(-1f, 1f)
                                    builder.add((value * Short.MAX_VALUE).toInt().toShort())
                                }
                            } else {
                                while (view.remaining() >= 2 && builder.size < maxSamples) {
                                    builder.add(view.short)
                                }
                            }
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            if (builder.size == 0) error("Ses çözüldü fakat PCM örneği üretilemedi.")
            return PcmAudio(outputRate, outputChannels, builder.toArray())
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun MediaFormat.getIntegerOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback
}

private class ShortArrayBuilder(initialCapacity: Int) {
    private var values = ShortArray(initialCapacity.coerceAtLeast(1024))
    var size: Int = 0
        private set

    fun add(value: Short) {
        if (size == values.size) {
            values = values.copyOf((values.size * 3 / 2).coerceAtLeast(values.size + 1024))
        }
        values[size++] = value
    }

    fun toArray(): ShortArray = values.copyOf(size)
}
