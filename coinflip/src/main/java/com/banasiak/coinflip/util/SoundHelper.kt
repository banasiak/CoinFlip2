package com.banasiak.coinflip.util

import android.content.Context
import android.media.SoundPool
import androidx.annotation.RawRes
import com.banasiak.coinflip.R
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.settings.SoundStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundHelper @Inject constructor(
  @ApplicationContext context: Context,
  private val settings: SettingsManager,
  private val soundPool: SoundPool
) {
  // both sets load up front: SoundPool loads asynchronously, so a set loaded on switching would play
  // nothing until it finished
  private val modernSounds = Sound.entries.associateWith { soundPool.load(context, it.modern, 1) }
  private val classicSounds = Sound.entries.associateWith { soundPool.load(context, it.classic, 1) }
  private val spinSound = soundPool.load(context, R.raw.spin, 1)

  // the stream the whir is playing on, or 0 for none: SoundPool numbers its streams from 1
  private var spinStream = 0

  fun playSound(sound: Sound) {
    if (!settings.soundEnabled) return

    val sounds = if (settings.soundStyle == SoundStyle.CLASSIC) classicSounds else modernSounds
    soundPool.play(sounds.getValue(sound), 1.0f, 1.0f, sound.priority, 0, 1.0f)
  }

  /**
   * Starts the whir of the coin in the air. Its swells are timed to the flip animation from the
   * animation's first frame, so call this as the animation starts, and [stopSpin] as the coin lands.
   * The classic set never had a whir, so with it chosen this does nothing.
   */
  fun startSpin() {
    if (!settings.soundEnabled || settings.soundStyle == SoundStyle.CLASSIC) return
    stopSpin()
    spinStream = soundPool.play(spinSound, 1.0f, 1.0f, SPIN_PRIORITY, 0, 1.0f)
  }

  /** Stops the whir [startSpin] began. Safe to call when nothing is playing. */
  fun stopSpin() {
    if (spinStream != 0) soundPool.stop(spinStream)
    spinStream = 0
  }

  companion object {
    private const val SPIN_PRIORITY = 0
  }

  enum class Sound(@param:RawRes val modern: Int, @param:RawRes val classic: Int, val priority: Int) {
    COIN(R.raw.coin, R.raw.classic_coin, 0),
    POWERUP(R.raw.powerup, R.raw.classic_powerup, 0),
    ONEUP(R.raw.oneup, R.raw.classic_oneup, 1), // always make sure this special sound plays :)
    STREAK(R.raw.streak, R.raw.classic_streak, 1)
  }
}