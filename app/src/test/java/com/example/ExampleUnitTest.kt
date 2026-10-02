package com.example

import com.example.model.PlaybackTransitionConfig
import com.example.model.SleepTimerState
import com.example.model.Spatial8DConfig
import com.example.playback.AudioEffectManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Pruebas unitarias locales para Aura Music.
 * Valida la lógica de formato de temporizador, configuración 8D y efectos de audio.
 */
class ExampleUnitTest {

    @Test
    fun sleepTimerState_formattedRemaining_formatsCorrectly() {
        val timer2Min = SleepTimerState(isActive = true, totalSeconds = 120, remainingSeconds = 120)
        assertEquals("2:00", timer2Min.formattedRemaining)

        val timer65Sec = SleepTimerState(isActive = true, totalSeconds = 120, remainingSeconds = 65)
        assertEquals("1:05", timer65Sec.formattedRemaining)

        val timer9Sec = SleepTimerState(isActive = true, totalSeconds = 120, remainingSeconds = 9, isFadingOut = true)
        assertEquals("0:09", timer9Sec.formattedRemaining)
        assertTrue(timer9Sec.isFadingOut)

        val timerZero = SleepTimerState(isActive = false, totalSeconds = 0, remainingSeconds = 0)
        assertEquals("0:00", timerZero.formattedRemaining)
    }

    @Test
    fun spatial8DConfig_defaultValuesAreOptimal() {
        val config = Spatial8DConfig()
        assertFalse(config.enabled)
        assertEquals(10.0f, config.orbitSpeedSeconds, 0.001f)
        assertEquals(0.85f, config.spatialIntensity, 0.001f)
        assertEquals(0.35f, config.roomDepth, 0.001f)
    }

    @Test
    fun playbackTransitionConfig_defaultValuesAreGapless() {
        val config = PlaybackTransitionConfig()
        assertEquals(0, config.crossfadeSeconds)
        assertTrue(config.isGapless)
    }
}
