package com.modjust4real.mixora.audio

import com.modjust4real.mixora.data.MixParams
import java.util.Locale

data class CommandResult(
    val params: MixParams,
    val message: String,
    val understood: Boolean
)

object CommandInterpreter {
    fun apply(input: String, current: MixParams): CommandResult {
        val text = input.lowercase(Locale("tr", "TR")).trim()
        if (text.isBlank()) return CommandResult(current, "Bir değişiklik yaz.", false)

        var result = current
        val changes = mutableListOf<String>()

        fun hasAny(vararg tokens: String) = tokens.any { it in text }

        if (hasAny("vokali öne", "vokal öne", "sesi öne", "vokal yükselt")) {
            result = result.copy(vocalGainDb = (result.vocalGainDb + 1.5f).coerceAtMost(8f))
            changes += "vokal +1.5 dB"
        }
        if (hasAny("vokali geri", "vokal geri", "sesi kıs", "vokal kıs")) {
            result = result.copy(vocalGainDb = (result.vocalGainDb - 1.5f).coerceAtLeast(-8f))
            changes += "vokal -1.5 dB"
        }
        if (hasAny("daha parlak", "parlaklaştır", "tiz artır")) {
            result = result.copy(presenceDb = (result.presenceDb + 1.5f).coerceAtMost(6f))
            changes += "presence +1.5 dB"
        }
        if (hasAny("daha karanlık", "tiz azalt", "parlaklığı azalt")) {
            result = result.copy(presenceDb = (result.presenceDb - 1.5f).coerceAtLeast(-6f))
            changes += "presence -1.5 dB"
        }
        if (hasAny("autotune artır", "autotune arttır", "tune artır", "tune arttır")) {
            result = result.copy(tuneAmount = (result.tuneAmount + 0.15f).coerceAtMost(1f))
            changes += "autotune artırıldı"
        }
        if (hasAny("autotune azalt", "tune azalt", "daha doğal")) {
            result = result.copy(tuneAmount = (result.tuneAmount - 0.15f).coerceAtLeast(0f))
            changes += "autotune azaltıldı"
        }
        if (hasAny("reverb artır", "reverb arttır", "daha ıslak", "daha geniş mekan")) {
            result = result.copy(reverb = (result.reverb + 0.08f).coerceAtMost(0.65f))
            changes += "reverb artırıldı"
        }
        if (hasAny("reverb azalt", "daha kuru", "reverb kıs")) {
            result = result.copy(reverb = (result.reverb - 0.08f).coerceAtLeast(0f))
            changes += "reverb azaltıldı"
        }
        if (hasAny("bassı temizle", "bası temizle", "dip sesi temizle", "çamuru temizle")) {
            result = result.copy(highPassHz = (result.highPassHz + 20f).coerceAtMost(180f))
            changes += "high-pass ${result.highPassHz.toInt()} Hz"
        }
        if (hasAny("vokali genişlet", "sesi genişlet")) {
            result = result.copy(stereoWidth = (result.stereoWidth + 0.12f).coerceAtMost(1.6f))
            changes += "vokal genişletildi"
        }
        if (hasAny("vokali daralt", "sesi daralt", "vokali ortala")) {
            result = result.copy(stereoWidth = (result.stereoWidth - 0.12f).coerceAtLeast(0.5f))
            changes += "vokal daraltıldı"
        }
        if (hasAny("daha sıkı", "kompresyon artır", "kompresyon arttır")) {
            result = result.copy(compression = (result.compression + 0.1f).coerceAtMost(1f))
            changes += "kompresyon artırıldı"
        }

        return if (changes.isEmpty()) {
            CommandResult(
                current,
                "Komutu anlayamadım. Örnek: ‘vokali öne al’ veya ‘reverb azalt’.",
                false
            )
        } else {
            CommandResult(result, changes.joinToString(" • "), true)
        }
    }
}
