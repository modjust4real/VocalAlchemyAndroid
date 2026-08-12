@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.modjust4real.mixora.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.modjust4real.mixora.audio.AudioAnalyzer
import com.modjust4real.mixora.audio.CommandInterpreter
import com.modjust4real.mixora.audio.LiveMonitorEngine
import com.modjust4real.mixora.audio.MixEngine
import com.modjust4real.mixora.audio.MusicTheory
import com.modjust4real.mixora.data.AudioFeatures
import com.modjust4real.mixora.data.MixParams
import com.modjust4real.mixora.data.MixSelection
import com.modjust4real.mixora.data.PresetStore
import com.modjust4real.mixora.data.RenderResult
import com.modjust4real.mixora.data.VocalPreset
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { HOME, MIX, PRESET, LIVE }
private enum class PresetMode { VOICE, REFERENCE }

@Composable
fun MixoraApp() {
    val context = LocalContext.current
    val store = remember { PresetStore(context) }
    var screenName by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    var presets by remember { mutableStateOf(store.load()) }
    var livePreset by remember { mutableStateOf<VocalPreset?>(null) }
    val screen = Screen.valueOf(screenName)

    BackHandler(enabled = screen != Screen.HOME) { screenName = Screen.HOME.name }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF120B1C), Ink, Color(0xFF080A10))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                onMix = { screenName = Screen.MIX.name },
                onPreset = { screenName = Screen.PRESET.name },
                onLive = { livePreset = presets.firstOrNull(); screenName = Screen.LIVE.name },
                presetCount = presets.size
            )
            Screen.MIX -> MixScreen(
                presets = presets,
                onBack = { screenName = Screen.HOME.name }
            )
            Screen.PRESET -> PresetScreen(
                presets = presets,
                onBack = { screenName = Screen.HOME.name },
                onSave = { preset ->
                    store.save(preset)
                    presets = store.load()
                },
                onDelete = { id ->
                    store.delete(id)
                    presets = store.load()
                },
                onTest = { preset ->
                    livePreset = preset
                    screenName = Screen.LIVE.name
                }
            )
            Screen.LIVE -> LiveTestScreen(
                presets = presets,
                initialPreset = livePreset,
                onBack = { screenName = Screen.HOME.name }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onMix: () -> Unit,
    onPreset: () -> Unit,
    onLive: () -> Unit,
    presetCount: Int
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Purple, PurpleDeep))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", fontWeight = FontWeight.Black, fontSize = 23.sp, color = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("MIXORA", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Pocket vocal studio", color = SoftText, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                Surface(color = Mint.copy(alpha = 0.12f), shape = CircleShape) {
                    Text("OFFLINE", color = Mint, fontSize = 10.sp, modifier = Modifier.padding(10.dp, 6.dp))
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text(
                "Vokalini duyduğun\nşeye dönüştür.",
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Beat ve vokali yükle; Mixora seviyeyi, tonu, dinamiği ve alanı analiz edip düzenlenebilir bir ilk mix hazırlasın.",
                color = SoftText,
                lineHeight = 22.sp
            )
        }
        item { WaveHero() }
        item {
            ActionCard(
                number = "01",
                title = "Beat + vokal mix",
                description = "Dosyalarını seç, otomatik zinciri çalıştır, Türkçe komutlarla revize et ve WAV al.",
                accent = Purple,
                button = "Mix stüdyosunu aç",
                testTag = "open_mix",
                onClick = onMix
            )
        }
        item {
            ActionCard(
                number = "02",
                title = "Preset oluştur",
                description = "Kendi sesinden veya sahip olduğun referans kayıttan tekrar kullanılabilir vokal profili çıkar.",
                accent = Mint,
                button = "Preset üretici",
                testTag = "open_preset",
                onClick = onPreset
            )
        }
        item {
            ActionCard(
                number = "03",
                title = "Canlı preset testi",
                description = "Kulaklıkla mikrofonunu anlık dinle; ton, gam ve autotune miktarını değiştir.",
                accent = Rose,
                button = "Mikrofon testini aç",
                testTag = "open_live",
                onClick = onLive
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TinyStat("Kayıtlı preset", presetCount.toString())
                TinyStat("İşleme", "Cihazda")
                TinyStat("Dışa aktar", "WAV 44.1k")
            }
        }
    }
}

@Composable
private fun WaveHero() {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "wavePhase"
    )
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ADAPTIVE DSP", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("44.1 / 48 kHz", color = SoftText, fontSize = 11.sp)
            }
            Canvas(Modifier.fillMaxWidth().height(92.dp).padding(top = 14.dp)) {
                val center = size.height / 2f
                val step = size.width / 38f
                repeat(38) { index ->
                    val envelope = kotlin.math.sin(index / 37f * Math.PI).toFloat()
                    val motion = 0.45f + 0.55f * kotlin.math.abs(
                        kotlin.math.sin((index * 0.53f + phase * 6.28f).toDouble()).toFloat()
                    )
                    val h = size.height * 0.44f * envelope * motion + 3f
                    drawLine(
                        brush = Brush.verticalGradient(listOf(Purple, Mint)),
                        start = Offset(index * step + step / 2f, center - h),
                        end = Offset(index * step + step / 2f, center + h),
                        strokeWidth = step * 0.44f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun MixScreen(presets: List<VocalPreset>, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selection by remember { mutableStateOf(MixSelection()) }
    var params by remember { mutableStateOf(MixParams()) }
    var renderResult by remember { mutableStateOf<RenderResult?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isRendering by remember { mutableStateOf(false) }
    var command by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf("Beat ve vokali seçerek başla.") }
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    val beatPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            selection = selection.copy(beatUri = uri, beatName = queryDisplayName(context, uri))
            renderResult = null
            status = "Beat seçildi."
        }
    }
    val vocalPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            selection = selection.copy(vocalUri = uri, vocalName = queryDisplayName(context, uri))
            renderResult = null
            status = "Vokal seçildi."
        }
    }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav")
    ) { outputUri ->
        val result = renderResult
        if (outputUri != null && result != null) {
            scope.launch {
                val copied = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(outputUri)?.use { output ->
                            File(result.filePath).inputStream().buffered().use { it.copyTo(output) }
                        } ?: error("Çıktı dosyası açılamadı.")
                    }
                }
                status = copied.fold(
                    onSuccess = { "WAV başarıyla kaydedildi." },
                    onFailure = { "Dışa aktarma hatası: ${it.message}" }
                )
            }
        }
    }

    DisposableEffect(renderResult?.filePath) {
        onDispose {
            player?.release()
            player = null
        }
    }

    fun startRender() {
        val beatUri = selection.beatUri
        val vocalUri = selection.vocalUri
        if (beatUri == null || vocalUri == null || isRendering) return
        player?.release()
        player = null
        isPlaying = false
        isRendering = true
        progress = 0f
        status = "Sesler çözümleniyor…"
        scope.launch {
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    MixEngine.render(context, beatUri, vocalUri, params) { value ->
                        scope.launch { progress = value }
                    }
                }
            }
            outcome.onSuccess {
                renderResult = it
                status = "Mix hazır. Dinleyebilir, komutla değiştirip yeniden render alabilirsin."
            }.onFailure {
                status = "Mix oluşturulamadı: ${it.message ?: "bilinmeyen hata"}"
            }
            isRendering = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("mix_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Mix stüdyosu", "Beat + vokal", onBack) }
        item {
            GlassCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("1 • SES DOSYALARI")
                    FilePickerRow(
                        label = "Beat",
                        fileName = selection.beatName,
                        accent = Purple,
                        buttonTag = "pick_beat"
                    ) { beatPicker.launch(arrayOf("audio/*")) }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    FilePickerRow(
                        label = "Vokal",
                        fileName = selection.vocalName,
                        accent = Mint,
                        buttonTag = "pick_vocal"
                    ) { vocalPicker.launch(arrayOf("audio/*")) }
                }
            }
        }
        if (presets.isNotEmpty()) {
            item {
                GlassCard {
                    Column(Modifier.padding(16.dp)) {
                        SectionLabel("KAYITLI PRESET UYGULA")
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            presets.forEach { preset ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        params = preset.params
                                        renderResult = null
                                        status = "‘${preset.name}’ parametreleri uygulandı."
                                    },
                                    label = { Text(preset.name, maxLines = 1) },
                                    modifier = Modifier.padding(end = 8.dp),
                                    colors = FilterChipDefaults.filterChipColors(containerColor = Color.White.copy(alpha = 0.04f))
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            GlassCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("2 • MIX KARAKTERİ")
                    ParameterSlider("Vokal seviyesi", params.vocalGainDb, -8f..8f, "%.1f dB") {
                        params = params.copy(vocalGainDb = it); renderResult = null
                    }
                    ParameterSlider("Beat seviyesi", params.beatGainDb, -10f..4f, "%.1f dB") {
                        params = params.copy(beatGainDb = it); renderResult = null
                    }
                    ParameterSlider("Low cut", params.highPassHz, 55f..180f, "%.0f Hz") {
                        params = params.copy(highPassHz = it); renderResult = null
                    }
                    ParameterSlider("Presence", params.presenceDb, -6f..6f, "%.1f dB") {
                        params = params.copy(presenceDb = it); renderResult = null
                    }
                    ParameterSlider("Kompresyon", params.compression, 0f..1f, "%%%d") {
                        params = params.copy(compression = it); renderResult = null
                    }
                    ParameterSlider("Saturasyon", params.saturation, 0f..0.5f, "%%%d") {
                        params = params.copy(saturation = it); renderResult = null
                    }
                    ParameterSlider("Reverb", params.reverb, 0f..0.65f, "%%%d") {
                        params = params.copy(reverb = it); renderResult = null
                    }
                    ParameterSlider("Stereo genişlik", params.stereoWidth, 0.5f..1.6f, "%%%d") {
                        params = params.copy(stereoWidth = it); renderResult = null
                    }
                    ParameterSlider("Autotune", params.tuneAmount, 0f..1f, "%%%d") {
                        params = params.copy(tuneAmount = it); renderResult = null
                    }
                    Text("Autotune tonu", color = SoftText, fontSize = 12.sp)
                    ChoiceRow(
                        values = MusicTheory.roots,
                        selected = MusicTheory.roots[params.tuneRoot.coerceIn(0, 11)]
                    ) { choice -> params = params.copy(tuneRoot = MusicTheory.roots.indexOf(choice)); renderResult = null }
                    ChoiceRow(values = MusicTheory.scales, selected = params.tuneScale) {
                        params = params.copy(tuneScale = it); renderResult = null
                    }
                }
            }
        }
        item {
            PrimaryButton(
                text = if (isRendering) "Mix hazırlanıyor…" else "Akıllı mix oluştur",
                enabled = selection.beatUri != null && selection.vocalUri != null && !isRendering,
                testTag = "render_mix",
                onClick = { startRender() }
            )
            AnimatedVisibility(isRendering) {
                Column(Modifier.padding(top = 10.dp)) {
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth(), color = Mint)
                    Text("%%%d".format((progress * 100).toInt()), color = SoftText, fontSize = 11.sp)
                }
            }
            Text(status, color = if ("hata" in status.lowercase()) Rose else SoftText, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        renderResult?.let { result ->
            item {
                GlassCard(borderColor = Mint.copy(alpha = 0.45f)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Mint.copy(alpha = 0.15f), shape = CircleShape) {
                                Text("HAZIR", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(9.dp, 5.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Text(formatDuration(result.durationSeconds), color = SoftText)
                        }
                        Text("İlk mix tamamlandı", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(result.appliedSummary, color = SoftText, fontSize = 12.sp, lineHeight = 18.sp)
                        Text("Çıkış tepe: %.1f dBFS".format(result.peakDb), color = Mint, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (isPlaying) {
                                        player?.pause(); isPlaying = false
                                    } else {
                                        if (player == null) {
                                            player = MediaPlayer.create(context, Uri.fromFile(File(result.filePath)))
                                            player?.setOnCompletionListener { isPlaying = false }
                                        }
                                        player?.start(); isPlaying = true
                                    }
                                }
                            ) { Text(if (isPlaying) "Duraklat" else "Dinle") }
                            Button(
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                                onClick = { exportPicker.launch("Mixora_${System.currentTimeMillis()}.wav") }
                            ) { Text("WAV kaydet", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            item {
                GlassCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("3 • REVİZE İSTE")
                        Text(
                            "Örnek: ‘vokali öne al’, ‘daha karanlık’, ‘autotune artır’, ‘reverb azalt’, ‘bassı temizle’.",
                            color = SoftText,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        OutlinedTextField(
                            value = command,
                            onValueChange = { command = it },
                            modifier = Modifier.fillMaxWidth().testTag("mix_command"),
                            label = { Text("Değişiklik") },
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val outcome = CommandInterpreter.apply(command, params)
                                params = outcome.params
                                status = outcome.message + if (outcome.understood) " • Yeniden mix oluştur." else ""
                                if (outcome.understood) renderResult = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = command.isNotBlank()
                        ) { Text("Komutu uygula") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun PresetScreen(
    presets: List<VocalPreset>,
    onBack: () -> Unit,
    onSave: (VocalPreset) -> Unit,
    onDelete: (Long) -> Unit,
    onTest: (VocalPreset) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var modeName by rememberSaveable { mutableStateOf(PresetMode.VOICE.name) }
    val mode = PresetMode.valueOf(modeName)
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var audioName by remember { mutableStateOf("") }
    var presetName by rememberSaveable { mutableStateOf("") }
    var referenceUrl by rememberSaveable { mutableStateOf("") }
    var linkStatus by remember { mutableStateOf("") }
    var features by remember { mutableStateOf<AudioFeatures?>(null) }
    var generated by remember { mutableStateOf<VocalPreset?>(null) }
    var status by remember { mutableStateOf("Ses seç ve analizi başlat.") }
    var analyzing by remember { mutableStateOf(false) }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            audioUri = uri
            audioName = queryDisplayName(context, uri)
            generated = null
            features = null
            status = "Dosya hazır. Analizi başlatabilirsin."
        }
    }

    fun analyze() {
        val uri = audioUri ?: return
        if (mode == PresetMode.REFERENCE && referenceUrl.isBlank()) {
            status = "Önce şarkı bağlantısını gir."
            return
        }
        analyzing = true
        status = "Vokal karakteri analiz ediliyor…"
        scope.launch {
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    val measured = AudioAnalyzer.analyze(context, uri)
                    val source = if (mode == PresetMode.VOICE) {
                        "Kendi ses analizi"
                    } else {
                        "Referans: ${referenceDescriptor(referenceUrl)}"
                    }
                    measured to AudioAnalyzer.createPreset(
                        name = presetName.ifBlank {
                            if (mode == PresetMode.VOICE) "Benim vokalim" else "Referans preset"
                        },
                        source = source,
                        features = measured
                    )
                }
            }
            outcome.onSuccess { pair ->
                features = pair.first
                generated = pair.second
                status = "Preset oluşturuldu. Kaydedebilir veya canlı test edebilirsin."
            }.onFailure {
                status = "Analiz yapılamadı: ${it.message}"
            }
            analyzing = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("preset_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Preset üretici", "Ses profili", onBack) }
        item {
            GlassCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("KAYNAK TÜRÜ")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == PresetMode.VOICE,
                            onClick = { modeName = PresetMode.VOICE.name; audioUri = null; generated = null },
                            label = { Text("Kendi sesim") }
                        )
                        FilterChip(
                            selected = mode == PresetMode.REFERENCE,
                            onClick = { modeName = PresetMode.REFERENCE.name; audioUri = null; generated = null },
                            label = { Text("Şarkı referansı") }
                        )
                    }
                    Text(
                        if (mode == PresetMode.VOICE)
                            "Temiz veya işlenmiş bir vokal kaydı yükle; sesinin parlaklık, dinamik ve perde karakterinden başlangıç zinciri çıkarılsın."
                        else
                            "Bağlantı parçayı tanımlar. YouTube/Spotify sesi indirilmez; analiz için sahip olduğun veya kullanma iznin olan referans ses dosyasını ayrıca seçersin.",
                        color = SoftText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        if (mode == PresetMode.REFERENCE) {
            item {
                GlassCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("1 • ŞARKI BAĞLANTISI")
                        OutlinedTextField(
                            value = referenceUrl,
                            onValueChange = { referenceUrl = it; linkStatus = "" },
                            label = { Text("YouTube / Spotify / web bağlantısı") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                        OutlinedButton(
                            onClick = {
                                linkStatus = if (isRecognizedAudioLink(referenceUrl)) {
                                    "Tanındı: ${referenceDescriptor(referenceUrl)}"
                                } else {
                                    "Geçerli bir http/https şarkı bağlantısı gir."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Bağlantıyı tanı") }
                        if (linkStatus.isNotBlank()) Text(linkStatus, color = if (linkStatus.startsWith("Tanındı")) Mint else Rose, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            GlassCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(if (mode == PresetMode.VOICE) "1 • VOKAL DOSYASI" else "2 • İZİNLİ REFERANS SES")
                    FilePickerRow(
                        label = if (mode == PresetMode.VOICE) "Ses kaydım" else "Referans audio",
                        fileName = audioName,
                        accent = if (mode == PresetMode.VOICE) Purple else Mint,
                        buttonTag = "pick_preset_audio"
                    ) { audioPicker.launch(arrayOf("audio/*")) }
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Preset adı") },
                        placeholder = { Text("Örn. Parlak Trap Vokal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PrimaryButton(
                        text = if (analyzing) "Analiz ediliyor…" else "Preset oluştur",
                        enabled = audioUri != null && !analyzing &&
                            (mode == PresetMode.VOICE || isRecognizedAudioLink(referenceUrl)),
                        testTag = "analyze_preset",
                        onClick = { analyze() }
                    )
                    if (analyzing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Mint)
                    Text(status, color = if ("yapılamadı" in status || "Önce" in status) Rose else SoftText, fontSize = 12.sp)
                }
            }
        }
        generated?.let { preset ->
            item {
                GlassCard(borderColor = Purple.copy(alpha = 0.55f)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(preset.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Spacer(Modifier.weight(1f))
                            Text("${preset.suggestedKey} ${preset.suggestedScale}", color = Mint, fontWeight = FontWeight.Bold)
                        }
                        Text(preset.source, color = SoftText, fontSize = 12.sp)
                        Text(preset.notes, color = SoftText, fontSize = 12.sp)
                        features?.let { FeatureGrid(it) }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Text(
                            "HPF ${preset.params.highPassHz.toInt()} Hz • Presence %.1f dB • Kompresyon %%%d • Reverb %%%d • Tune %%%d".format(
                                preset.params.presenceDb,
                                (preset.params.compression * 100).toInt(),
                                (preset.params.reverb * 100).toInt(),
                                (preset.params.tuneAmount * 100).toInt()
                            ),
                            color = Color.White,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { onTest(preset) }, modifier = Modifier.weight(1f)) {
                                Text("Canlı test")
                            }
                            Button(
                                onClick = { onSave(preset); status = "‘${preset.name}’ kaydedildi." },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)
                            ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        if (presets.isNotEmpty()) {
            item {
                SectionLabel("KAYITLI PRESETLER • ${presets.size}")
            }
            items(presets, key = { it.id }) { preset ->
                GlassCard {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(preset.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(preset.source, color = SoftText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${preset.suggestedKey} ${preset.suggestedScale}", color = Mint, fontSize = 12.sp)
                        }
                        Text(
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(preset.createdAt)),
                            color = SoftText,
                            fontSize = 10.sp
                        )
                        Row {
                            TextButton(onClick = { onTest(preset) }) { Text("Test et") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onDelete(preset.id) }) { Text("Sil", color = Rose) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun LiveTestScreen(
    presets: List<VocalPreset>,
    initialPreset: VocalPreset?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val initialRoot = initialPreset?.params?.tuneRoot ?: 0
    var root by remember(initialPreset?.id) { mutableStateOf(initialRoot) }
    var scale by remember(initialPreset?.id) { mutableStateOf(initialPreset?.params?.tuneScale ?: "Minör") }
    var amount by remember(initialPreset?.id) { mutableFloatStateOf(initialPreset?.params?.tuneAmount ?: 0.7f) }
    var detectedNote by remember { mutableStateOf("—") }
    var targetNote by remember { mutableStateOf("—") }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Başlamadan önce kablolu veya düşük gecikmeli kulaklık tak.") }
    var shouldStartAfterPermission by remember { mutableStateOf(false) }

    val engine = remember {
        LiveMonitorEngine(
            onPitch = { detected, target -> detectedNote = detected; targetNote = target },
            onError = { message -> status = message; running = false }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && shouldStartAfterPermission) {
            engine.update(root, scale, amount)
            engine.start()
            running = true
            status = "Canlı dinleme açık. Hoparlör geri beslemesini önlemek için kulaklık kullan."
        } else if (!granted) {
            status = "Canlı test için mikrofon izni gerekli."
        }
        shouldStartAfterPermission = false
    }

    LaunchedEffect(root, scale, amount) { engine.update(root, scale, amount) }
    DisposableEffect(Unit) { onDispose { engine.stop() } }

    fun toggleMonitor() {
        if (running) {
            engine.stop()
            running = false
            status = "Canlı dinleme durduruldu."
            detectedNote = "—"
            targetNote = "—"
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            engine.update(root, scale, amount)
            engine.start()
            running = true
            status = "Canlı dinleme açık. En düşük gecikme için kablolu kulaklık kullan."
        } else {
            shouldStartAfterPermission = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("live_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Canlı preset testi", "Mikrofon monitörü", onBack) }
        item {
            Surface(
                color = Rose.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Rose.copy(alpha = 0.35f))
            ) {
                Text(
                    "⚠ Hoparlörden test etmek feedback oluşturabilir. Kablolu kulaklık en düşük gecikmeyi verir; Bluetooth belirgin gecikme ekleyebilir.",
                    modifier = Modifier.padding(14.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
        if (presets.isNotEmpty()) {
            item {
                GlassCard {
                    Column(Modifier.padding(16.dp)) {
                        SectionLabel("PRESET SEÇ")
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            presets.forEach { preset ->
                                FilterChip(
                                    selected = preset.id == initialPreset?.id,
                                    onClick = {
                                        root = preset.params.tuneRoot
                                        scale = preset.params.tuneScale
                                        amount = preset.params.tuneAmount
                                        status = "‘${preset.name}’ canlı teste yüklendi."
                                    },
                                    label = { Text(preset.name, maxLines = 1) },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            GlassCard(borderColor = if (running) Mint.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.1f)) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = if (running) Mint.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f),
                        shape = CircleShape,
                        modifier = Modifier.size(154.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, if (running) Mint else Color.White.copy(alpha = 0.15f))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(if (running) targetNote else "MIC", fontSize = 38.sp, fontWeight = FontWeight.Black, color = if (running) Mint else Color.White)
                            Text(if (running) "hedef nota" else "hazır", color = SoftText, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TinyStat("Algılanan", detectedNote)
                        TinyStat("Hedef", targetNote)
                        TinyStat("Miktar", "%%%d".format((amount * 100).toInt()))
                    }
                }
            }
        }
        item {
            GlassCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("TON VE GAM")
                    ChoiceRow(MusicTheory.roots, MusicTheory.roots[root]) { root = MusicTheory.roots.indexOf(it) }
                    ChoiceRow(MusicTheory.scales, scale) { scale = it }
                    ParameterSlider("Autotune miktarı", amount, 0f..1f, "%%%d") { amount = it }
                }
            }
        }
        item {
            Button(
                onClick = { toggleMonitor() },
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("toggle_live"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) Rose else Mint,
                    contentColor = Ink
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (running) "Canlı testi durdur" else "Mikrofonu başlat", fontWeight = FontWeight.Black)
            }
            Text(status, color = SoftText, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 9.dp))
        }
        item {
            Text(
                "Canlı motor perdeyi yaklaşık 65–900 Hz aralığında algılar, seçilen gamdaki en yakın notaya yönlendirir ve miktar ayarıyla kuru/işlenmiş sesi karıştırır.",
                color = SoftText.copy(alpha = 0.8f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(10.dp)
            )
        }
    }
}

@Composable
private fun ScreenHeader(title: String, eyebrow: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color.White.copy(alpha = 0.06f),
            shape = CircleShape,
            modifier = Modifier.size(42.dp).clickable(onClick = onBack)
        ) { Box(contentAlignment = Alignment.Center) { Text("‹", fontSize = 30.sp) } }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(eyebrow.uppercase(), color = Mint, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White.copy(alpha = 0.09f),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.045f),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        content = content
    )
}

@Composable
private fun ActionCard(
    number: String,
    title: String,
    description: String,
    accent: Color,
    button: String,
    testTag: String,
    onClick: () -> Unit
) {
    GlassCard(borderColor = accent.copy(alpha = 0.23f)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(number, color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(description, color = SoftText, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().testTag(testTag),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Ink),
                shape = RoundedCornerShape(14.dp)
            ) { Text(button, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun FilePickerRow(
    label: String,
    fileName: String,
    accent: Color,
    buttonTag: String,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Text("♪", color = accent, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(
                fileName.ifBlank { "Henüz dosya seçilmedi" },
                color = SoftText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onClick, modifier = Modifier.testTag(buttonTag)) {
            Text(if (fileName.isBlank()) "Seç" else "Değiştir")
        }
    }
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = SoftText, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            val display = if (format.contains("%d")) {
                format.format((value * if (range.endInclusive <= 2f) 100 else 1).toInt())
            } else {
                format.format(value)
            }
            Text(display, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ChoiceRow(values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(value) },
                modifier = Modifier.padding(end = 7.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Purple.copy(alpha = 0.4f),
                    selectedLabelColor = Color.White,
                    containerColor = Color.White.copy(alpha = 0.035f)
                )
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, testTag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag(testTag),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Color.White)
    ) { Text(text, fontWeight = FontWeight.Black) }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Purple, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun TinyStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, color = Color.White, fontSize = 15.sp)
        Text(label, color = SoftText, fontSize = 9.sp)
    }
}

@Composable
private fun FeatureGrid(features: AudioFeatures) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TinyStat("RMS", "%.1f dB".format(features.rmsDb))
        TinyStat("Dinamik", "%.1f dB".format(features.dynamics))
        TinyStat("Perde", if (features.medianPitchHz > 0f) "%.0f Hz".format(features.medianPitchHz) else "—")
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "audio"
}

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun isRecognizedAudioLink(raw: String): Boolean {
    val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return false
    return (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
}

private fun referenceDescriptor(raw: String): String {
    val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return "Bilinmeyen bağlantı"
    val host = uri.host.orEmpty().removePrefix("www.")
    val platform = when {
        "spotify" in host -> "Spotify"
        "youtu" in host -> "YouTube"
        "soundcloud" in host -> "SoundCloud"
        else -> host.ifBlank { "Web" }
    }
    val identity = uri.getQueryParameter("v") ?: uri.lastPathSegment.orEmpty()
    return if (identity.isBlank()) platform else "$platform • ${identity.take(24)}"
}

private fun formatDuration(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
