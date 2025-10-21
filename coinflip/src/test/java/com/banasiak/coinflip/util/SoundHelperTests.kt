package com.banasiak.coinflip.util

import android.content.Context
import android.media.SoundPool
import com.banasiak.coinflip.settings.SettingsManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SoundHelperTests {
  private val context: Context = mockk(relaxed = true)
  private val settings: SettingsManager = mockk()
  private val soundPool: SoundPool = mockk(relaxed = true)

  private val coinSoundId = 1
  private val powerupSoundId = 2
  private val oneupSoundId = 3

  @BeforeEach
  fun before() {
    every { settings.soundEnabled } returns true
    every { soundPool.load(context, SoundHelper.Sound.COIN.resId, 1) } returns coinSoundId
    every { soundPool.load(context, SoundHelper.Sound.POWERUP.resId, 1) } returns powerupSoundId
    every { soundPool.load(context, SoundHelper.Sound.ONEUP.resId, 1) } returns oneupSoundId
  }

  @Test
  fun `given sound enabled, when play coin sound, then sound pool plays coin sound`() {
    // given
    val soundHelper = SoundHelper(context, settings, soundPool)

    // when
    soundHelper.playSound(SoundHelper.Sound.COIN)

    // then
    verify(exactly = 1) { soundPool.play(coinSoundId, 1.0f, 1.0f, SoundHelper.Sound.COIN.priority, 0, 1.0f) }
  }

  @Test
  fun `given sound enabled, when play powerup sound, then sound pool plays powerup sound`() {
    // given
    val soundHelper = SoundHelper(context, settings, soundPool)

    // when
    soundHelper.playSound(SoundHelper.Sound.POWERUP)

    // then
    verify(exactly = 1) { soundPool.play(powerupSoundId, 1.0f, 1.0f, SoundHelper.Sound.POWERUP.priority, 0, 1.0f) }
  }

  @Test
  fun `given sound enabled, when play oneup sound, then sound pool plays oneup sound`() {
    // given
    val soundHelper = SoundHelper(context, settings, soundPool)

    // when
    soundHelper.playSound(SoundHelper.Sound.ONEUP)

    // then
    verify(exactly = 1) { soundPool.play(oneupSoundId, 1.0f, 1.0f, SoundHelper.Sound.ONEUP.priority, 0, 1.0f) }
  }

  @Test
  fun `given sound disabled, when play sound, then sound pool does not play`() {
    // given
    every { settings.soundEnabled } returns false
    val soundHelper = SoundHelper(context, settings, soundPool)

    // when
    soundHelper.playSound(SoundHelper.Sound.COIN)

    // then
    verify(exactly = 0) { soundPool.play(any(), any(), any(), any(), any(), any()) }
  }
}