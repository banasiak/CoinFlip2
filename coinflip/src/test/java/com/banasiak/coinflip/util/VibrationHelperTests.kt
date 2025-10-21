package com.banasiak.coinflip.util

import android.os.VibrationEffect
import android.os.VibrationEffect.Composition.PRIMITIVE_SPIN
import android.os.VibrationEffect.Composition.PRIMITIVE_THUD
import android.os.Vibrator
import com.banasiak.coinflip.common.BuildInfo
import com.banasiak.coinflip.settings.SettingsManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VibrationHelperTests {
  private val buildInfo: BuildInfo = mockk()
  private val settings: SettingsManager = mockk()
  private val vibrator: Vibrator = mockk(relaxed = true)
  private val mockComposition: VibrationEffect.Composition = mockk()
  private val composedEffect: VibrationEffect = mockk()
  private val finalEffect: VibrationEffect = mockk()

  @BeforeEach
  fun before() {
    every { settings.vibrateEnabled } returns true
    mockkStatic(VibrationEffect::class)
    every { VibrationEffect.createWaveform(any(), any(), any()) } returns finalEffect
    every { VibrationEffect.createOneShot(any(), any()) } returns finalEffect
    every { VibrationEffect.startComposition() } returns mockComposition
    every { VibrationEffect.createRepeatingEffect(any()) } returns finalEffect
  }

  @AfterEach
  fun after() {
    unmockkStatic(VibrationEffect::class)
  }

  @Test
  fun `given vibration disabled, when vibrate, then vibrator not called`() {
    // given
    every { settings.vibrateEnabled } returns false
    val vibrationHelper = VibrationHelper(buildInfo, settings, vibrator)

    // when
    vibrationHelper.vibrate(VibrationHelper.Vibration.SPIN)

    // then
    verify(exactly = 0) { vibrator.vibrate(any() as VibrationEffect) }
  }

  @Test
  fun `given spin, is pre-baklava, when vibrate, then create waveform`() {
    // given
    every { buildInfo.isBaklava() } returns false
    val vibrationHelper = VibrationHelper(buildInfo, settings, vibrator)

    // when
    vibrationHelper.vibrate(VibrationHelper.Vibration.SPIN)

    // then
    verify(exactly = 1) { VibrationEffect.createWaveform(longArrayOf(5, 5, 5, 5), intArrayOf(0, 90, 0, 110), 0) }
    verify(exactly = 1) { vibrator.vibrate(finalEffect) }
  }

  @Test
  fun `given spin, is baklava, primitives not supported, when vibrate, then create waveform`() {
    // given
    every { buildInfo.isBaklava() } returns true
    every { vibrator.areAllPrimitivesSupported(PRIMITIVE_SPIN) } returns false
    val vibrationHelper = VibrationHelper(buildInfo, settings, vibrator)

    // when
    vibrationHelper.vibrate(VibrationHelper.Vibration.SPIN)

    // then
    verify(exactly = 1) { VibrationEffect.createWaveform(longArrayOf(5, 5, 5, 5), intArrayOf(0, 90, 0, 110), 0) }
    verify(exactly = 1) { vibrator.vibrate(finalEffect) }
  }

  @Test
  fun `given spin, is baklava, primitives supported, when vibrate, then create composition`() {
    // given
    every { buildInfo.isBaklava() } returns true
    every { vibrator.areAllPrimitivesSupported(PRIMITIVE_SPIN) } returns true
    every { mockComposition.addPrimitive(PRIMITIVE_SPIN) } returns mockComposition
    every { mockComposition.compose() } returns composedEffect
    every { VibrationEffect.createRepeatingEffect(composedEffect) } returns finalEffect
    val vibrationHelper = VibrationHelper(buildInfo, settings, vibrator)

    // when
    vibrationHelper.vibrate(VibrationHelper.Vibration.SPIN)

    // then
    verify(exactly = 1) { mockComposition.addPrimitive(PRIMITIVE_SPIN) }
    verify(exactly = 1) { VibrationEffect.createRepeatingEffect(composedEffect) }
    verify(exactly = 1) { vibrator.vibrate(finalEffect) }
  }

  @Test
  fun `given thud, is pre-baklava, when vibrate, then create one-shot`() {
    // given
    every { buildInfo.isBaklava() } returns false
    val vibrationHelper = VibrationHelper(buildInfo, settings, vibrator)

    // when
    vibrationHelper.vibrate(VibrationHelper.Vibration.THUD)

    // then
    verify(exactly = 1) { VibrationEffect.createOneShot(40, 220) }
    verify(exactly = 1) { vibrator.vibrate(finalEffect) }
  }

  @Test
  fun `given thud, is baklava, primitives not supported, when vibrate, then create one-shot`() {
    // given
    every { buildInfo.isBaklava() } returns true
    every { vibrator.areAllPrimitivesSupported(PRIMITIVE_THUD) } returns false
    val vibrationHelper = VibrationHelper(buildInfo, settings, vibrator)

    // when
    vibrationHelper.vibrate(VibrationHelper.Vibration.THUD)

    // then
    verify(exactly = 1) { VibrationEffect.createOneShot(40, 220) }
    verify(exactly = 1) { vibrator.vibrate(finalEffect) }
  }

  @Test
  fun `given thud, is baklava, primitives supported, when vibrate, then create composition`() {
    // given
    every { buildInfo.isBaklava() } returns true
    every { vibrator.areAllPrimitivesSupported(PRIMITIVE_THUD) } returns true
    every { mockComposition.addPrimitive(PRIMITIVE_THUD, 1.0f) } returns mockComposition
    every { mockComposition.compose() } returns finalEffect
    val vibrationHelper = VibrationHelper(buildInfo, settings, vibrator)

    // when
    vibrationHelper.vibrate(VibrationHelper.Vibration.THUD)

    // then
    verify(exactly = 1) { mockComposition.addPrimitive(PRIMITIVE_THUD, 1.0f) }
    verify(exactly = 1) { vibrator.vibrate(finalEffect) }
  }

  @Test
  fun `when stop, then cancel`() {
    // given
    val vibrationHelper = VibrationHelper(buildInfo, settings, vibrator)

    // when
    vibrationHelper.stop()

    // then
    verify(exactly = 1) { vibrator.cancel() }
  }
}