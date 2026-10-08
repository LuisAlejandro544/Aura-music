package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Aura Music", appName)
  }

  @Test
  fun `audio effect manager controls 8D parameters correctly`() {
    val manager = com.example.playback.AudioEffectManager()
    org.junit.Assert.assertFalse(manager.spatial8DConfig.value.enabled)

    manager.set8DEnabled(true)
    org.junit.Assert.assertTrue(manager.spatial8DConfig.value.enabled)

    manager.set8DOrbitSpeed(14f)
    assertEquals(14f, manager.spatial8DConfig.value.orbitSpeedSeconds, 0.001f)

    manager.set8DSpatialIntensity(0.9f)
    assertEquals(0.9f, manager.spatial8DConfig.value.spatialIntensity, 0.001f)

    manager.set8DRoomDepth(0.45f)
    assertEquals(0.45f, manager.spatial8DConfig.value.roomDepth, 0.001f)

    manager.set8DMode16D(true)
    org.junit.Assert.assertTrue(manager.spatial8DConfig.value.is16DMode)

    manager.setVocalClarityEnabled(true)
    manager.setVocalClarityStrength(0.8f)
    org.junit.Assert.assertTrue(manager.vocalClarityConfig.value.enabled)
    assertEquals(0.8f, manager.vocalClarityConfig.value.strength, 0.001f)
  }

  @Test
  fun `equalizer is disabled by default and supports per-track or global scope`() {
    val manager = com.example.playback.AudioEffectManager()
    org.junit.Assert.assertFalse(manager.isEnabled.value)
    assertEquals(
        com.example.model.EqualizerScopeMode.GLOBAL_ALL_TRACKS,
        manager.eqScopeMode.value
    )

    // Modo global: el cambio persiste al pasar de canción
    manager.setEnabled(true)
    val rockPreset = com.example.model.EqualizerPreset.PRESETS[1]
    manager.applyPreset(rockPreset)
    org.junit.Assert.assertTrue(manager.isEnabled.value)
    assertEquals(rockPreset.name, manager.currentPreset.value.name)

    manager.onTrackChanged(101L)
    org.junit.Assert.assertTrue(manager.isEnabled.value)
    assertEquals(rockPreset.name, manager.currentPreset.value.name)

    // Modo solo para esta canción: los nuevos cambios se revierten al pasar a la siguiente pista
    manager.setEqScopeMode(com.example.model.EqualizerScopeMode.CURRENT_TRACK_ONLY, 101L)
    val popPreset = com.example.model.EqualizerPreset.PRESETS[2]
    manager.applyPreset(popPreset)
    manager.setBassBoost(750)
    assertEquals(popPreset.name, manager.currentPreset.value.name)
    assertEquals(750, manager.bassBoostLevel.value)

    manager.onTrackChanged(102L)
    assertEquals(
        com.example.model.EqualizerScopeMode.GLOBAL_ALL_TRACKS,
        manager.eqScopeMode.value
    )
    assertEquals(rockPreset.name, manager.currentPreset.value.name)
    assertEquals(rockPreset.bassBoost, manager.bassBoostLevel.value)
  }
}
