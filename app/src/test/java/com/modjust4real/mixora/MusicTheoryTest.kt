package com.modjust4real.mixora

import com.modjust4real.mixora.audio.MusicTheory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicTheoryTest {
    @Test
    fun aFourIsDetectedAsMidi69() {
        assertEquals(69f, MusicTheory.frequencyToMidi(440f), 0.01f)
        assertEquals(440f, MusicTheory.midiToFrequency(69f), 0.01f)
    }

    @Test
    fun cMinorRejectsMajorThird() {
        val target = MusicTheory.nearestAllowedMidi(64f, root = 0, scale = "Minör")
        assertTrue(target == 63 || target == 65)
    }

    @Test
    fun noteNameIncludesOctave() {
        assertEquals("A4", MusicTheory.noteName(69))
    }
}
