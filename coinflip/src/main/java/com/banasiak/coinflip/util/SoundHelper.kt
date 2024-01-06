package com.banasiak.coinflip.util

import android.content.Context
import android.media.AudioManager
import android.media.SoundPool
import androidx.annotation.RawRes
import com.banasiak.coinflip.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundHelper @Inject constructor(
  @ApplicationContext context: Context,
  private val audioManager: AudioManager
) {
  @Suppress("DEPRECATION") // SoundPool.Builder requires API 34
  private val soundPool = SoundPool(1, AudioManager.STREAM_MUSIC, 100)
  private val coinSound = soundPool.load(context, Sound.COIN.resId, 1)
  private val oneUpSound = soundPool.load(context, Sound.ONEUP.resId, 1)

  fun playSound(sound: Sound) {
    // use the "media volume" audio level so the user can manually adjust the volume using the system volume controls
    val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
    val soundId =
      when (sound) {
        Sound.COIN -> coinSound
        Sound.ONEUP -> oneUpSound
      }
    soundPool.play(soundId, volume, volume, 1, 0, 1f)
  }

  enum class Sound(@RawRes val resId: Int) {
    COIN(R.raw.coin),
    ONEUP(R.raw.oneup)
  }
}