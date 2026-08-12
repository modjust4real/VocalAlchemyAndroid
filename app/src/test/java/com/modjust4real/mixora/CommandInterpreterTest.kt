package com.modjust4real.mixora

import com.modjust4real.mixora.audio.CommandInterpreter
import com.modjust4real.mixora.data.MixParams
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandInterpreterTest {
    @Test
    fun vocalForwardRaisesVocalGain() {
        val initial = MixParams(vocalGainDb = 0f)
        val result = CommandInterpreter.apply("Vokali öne al", initial)

        assertTrue(result.understood)
        assertTrue(result.params.vocalGainDb > initial.vocalGainDb)
    }

    @Test
    fun darkerReducesPresence() {
        val initial = MixParams(presenceDb = 2f)
        val result = CommandInterpreter.apply("Biraz daha karanlık yap", initial)

        assertTrue(result.understood)
        assertTrue(result.params.presenceDb < initial.presenceDb)
    }

    @Test
    fun unknownCommandDoesNotMutateParams() {
        val initial = MixParams()
        val result = CommandInterpreter.apply("davulu marsa gönder", initial)

        assertFalse(result.understood)
        assertTrue(result.params == initial)
    }
}
