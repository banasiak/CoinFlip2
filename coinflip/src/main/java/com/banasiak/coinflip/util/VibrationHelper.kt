package com.banasiak.coinflip.util

import android.os.VibrationEffect
import android.os.VibrationEffect.Composition.PRIMITIVE_SPIN
import android.os.VibrationEffect.Composition.PRIMITIVE_THUD
import android.os.Vibrator
import com.banasiak.coinflip.common.BuildInfo
import com.banasiak.coinflip.settings.SettingsManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VibrationHelper @Inject constructor(
  private val buildInfo: BuildInfo,
  private val settings: SettingsManager,
  private val vibrator: Vibrator
) {
  private val spinVibration: VibrationEffect by lazy {
    if (buildInfo.isBaklava() && vibrator.areAllPrimitivesSupported(PRIMITIVE_SPIN)) {
      val spinPrimitive =
        VibrationEffect.startComposition()
          .addPrimitive(PRIMITIVE_SPIN)
          .compose()
      VibrationEffect.createRepeatingEffect(spinPrimitive)
    } else {
      VibrationEffect.createWaveform(longArrayOf(5, 5, 5, 5), intArrayOf(0, 90, 0, 110), 0)
    }
  }

  private val thudVibration: VibrationEffect by lazy {
    if (buildInfo.isBaklava() && vibrator.areAllPrimitivesSupported(PRIMITIVE_THUD)) {
      VibrationEffect.startComposition()
        .addPrimitive(PRIMITIVE_THUD, 1.0f)
        .compose()
    } else {
      VibrationEffect.createOneShot(40, 220)
    }
  }

  fun vibrate(type: Vibration) {
    if (!settings.vibrateEnabled) return

    when (type) {
      // this is a repeating vibration for the duration of the animation and needs to be canceled when the animation completes
      Vibration.SPIN -> {
        vibrator.vibrate(spinVibration)
      }
      // this is a one-shot vibration when the animation is complete
      Vibration.THUD -> {
        vibrator.vibrate(thudVibration)
      }
    }
  }

  fun stop() {
    vibrator.cancel()
  }

  enum class Vibration {
    SPIN,
    THUD
  }
}