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
  }
}
