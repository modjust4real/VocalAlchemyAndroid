package com.modjust4real.mixora.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.tanh

class LiveMonitorEngine(
    private val onPitch: (detectedNote: String, targetNote: String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val sampleRate = 48_000
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var root = 0
    @Volatile private var scale = "Minör"
    @Volatile private var amount = 0.7f
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null

    fun update(root: Int, scale: String, amount: Float) {
        this.root = root
        this.scale = scale
        this.amount = amount
    }

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "MixoraLiveMonitor", priority = Thread.MAX_PRIORITY) {
            runCatching { audioLoop() }
                .onFailure { error ->
                    mainHandler.post {
                        onError(error.message ?: "Canlı ses başlatılamadı.")
                    }
                }
            running = false
        }
    }

    fun stop() {
        running = false
        runCatching { recorder?.stop() }
        runCatching { player?.pause() }
        worker?.join(700)
        release()
    }

    fun isRunning(): Boolean = running

    private fun audioLoop() {
        val minRecord = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val minPlay = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferBytes = maxOf(minRecord, minPlay, 4096) * 2
        val formatIn = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val formatOut = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(formatIn)
            .setBufferSizeInBytes(bufferBytes)
            .build()
        player = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(formatOut)
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        check(recorder?.state == AudioRecord.STATE_INITIALIZED) { "Mikrofon hazırlanamadı." }
        check(player?.state == AudioTrack.STATE_INITIALIZED) { "Kulaklık çıkışı hazırlanamadı." }

        val processor = PitchCorrectionProcessor(
            sampleRate = sampleRate,
            root = root,
            scale = scale,
            amount = amount
        ) { detectedHz, targetMidi ->
            val detectedMidi = MusicTheory.frequencyToMidi(detectedHz).toInt()
            mainHandler.post {
                onPitch(
                    MusicTheory.noteName(detectedMidi),
                    MusicTheory.noteName(targetMidi)
                )
            }
        }
        val input = ShortArray(256)
        val output = ShortArray(256)
        recorder?.startRecording()
        player?.play()

        while (running) {
            processor.root = root
            processor.scale = scale
            processor.amount = amount
            val read = recorder?.read(input, 0, input.size, AudioRecord.READ_BLOCKING) ?: -1
            if (read <= 0) continue
            for (i in 0 until read) {
                val raw = input[i] / 32768f
                val gated = if (abs(raw) < 0.006f) raw * 0.25f else raw
                val corrected = processor.process(gated)
                val limited = tanh((corrected * 1.15f).toDouble()).toFloat() * 0.92f
                output[i] = (limited.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
            player?.write(output, 0, read, AudioTrack.WRITE_BLOCKING)
        }
        release()
    }

    private fun release() {
        runCatching { recorder?.release() }
        runCatching { player?.release() }
        recorder = null
        player = null
    }
}
