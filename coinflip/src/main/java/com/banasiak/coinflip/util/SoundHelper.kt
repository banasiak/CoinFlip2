package com.banasiak.coinflip.util

import android.content.Context
import android.media.SoundPool
import androidx.annotation.RawRes
import com.banasiak.coinflip.R
import com.banasiak.coinflip.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundHelper @Inject constructor(
  @ApplicationContext context: Context,
  private val settings: SettingsManager,
  private val soundPool: SoundPool
) {
  private val coinSound = soundPool.load(context, Sound.COIN.resId, 1)
  private val powerUpSound = soundPool.load(context, Sound.POWERUP.resId, 1)
  private val oneUpSound = soundPool.load(context, Sound.ONEUP.resId, 1)

  fun playSound(sound: Sound) {
    if (!settings.soundEnabled) return

    val soundId =
      when (sound) {
        Sound.COIN -> coinSound
        Sound.POWERUP -> powerUpSound
        Sound.ONEUP -> oneUpSound
      }
    soundPool.play(soundId, 1.0f, 1.0f, sound.priority, 0, 1.0f)
  }

  enum class Sound(@param:RawRes val resId: Int, val priority: Int) {
    COIN(R.raw.coin, 0),
    POWERUP(R.raw.powerup, 0),
    ONEUP(R.raw.oneup, 1) // always make sure this special sound plays :)
  }
}