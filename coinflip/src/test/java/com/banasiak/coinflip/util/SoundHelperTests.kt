package com.banasiak.coinflip.util

import android.content.Context
import android.media.SoundPool
import com.banasiak.coinflip.R
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.settings.SoundStyle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SoundHelperTests {
  private val context: Context = mockk(relaxed = true)
  private val settings: SettingsManager = mockk()
  private val soundPool: SoundPool = mockk(relaxed = true)

  private val coinSoundId = 1
  private val powerupSoundId = 2
  private val oneupSoundId = 3
  private val streakSoundId = 4
  private val spinSoundId = 5
  private val spinStreamId = 42
  private val classicCoinSoundId = 11
  private val classicStreakSoundId = 14

  @BeforeEach
  fun before() {
    every { settings.soundEnabled } returns true
    every { settings.soundStyle } returns SoundStyle.MODERN
    every { soundPool.load(context, SoundHelper.Sound.COIN.modern, 1) } returns coinSoundId
    every { soundPool.load(context, SoundHelper.Sound.POWERUP.modern, 1) } returns powerupSoundId
    every { soundPool.load(context, SoundHelper.Sound.ONEUP.modern, 1) } returns oneupSoundId
    every { soundPool.load(context, SoundHelper.Sound.STREAK.modern, 1) } returns streakSoundId
    every { soundPool.load(context, SoundHelper.Sound.COIN.classic, 1) } returns classicCoinSoundId
    every { soundPool.load(context, SoundHelper.Sound.STREAK.classic, 1) } returns classicStreakSoundId
    every { soundPool.load(context, R.raw.spin, 1) } returns spinSoundId
    every { soundPool.play(spinSoundId, any(), any(), any(), any(), any()) } returns spinStreamId
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
  fun `given sound enabled, when play streak sound, then sound pool plays streak sound`() {
    // given
    val soundHelper = SoundHelper(context, settings, soundPool)

    // when
    soundHelper.playSound(SoundHelper.Sound.STREAK)

    // then
    verify(exactly = 1) { soundPool.play(streakSoundId, 1.0f, 1.0f, SoundHelper.Sound.STREAK.priority, 0, 1.0f) }
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

  @Test
  fun `given the classic style, when play sounds, then sound pool plays the classic set`() {
    // given
    every { settings.soundStyle } returns SoundStyle.CLASSIC
    val soundHelper = SoundHelper(context, settings, soundPool)

    // when
    soundHelper.playSound(SoundHelper.Sound.COIN)
    soundHelper.playSound(SoundHelper.Sound.STREAK)

    // then
    verify(exactly = 1) { soundPool.play(classicCoinSoundId, 1.0f, 1.0f, SoundHelper.Sound.COIN.priority, 0, 1.0f) }
    verify(exactly = 1) { soundPool.play(classicStreakSoundId, 1.0f, 1.0f, SoundHelper.Sound.STREAK.priority, 0, 1.0f) }
    verify(exactly = 0) { soundPool.play(coinSoundId, any(), any(), any(), any(), any()) }
  }

  @Test
  fun `given a style changed after loading, when play sound, then the new style plays at once`() {
    // given: both sets are loaded up front, so switching never waits on a load
    val soundHelper = SoundHelper(context, settings, soundPool)
    every { settings.soundStyle } returns SoundStyle.CLASSIC

    // when
    soundHelper.playSound(SoundHelper.Sound.COIN)

    // then
    verify(exactly = 1) { soundPool.play(classicCoinSoundId, 1.0f, 1.0f, SoundHelper.Sound.COIN.priority, 0, 1.0f) }
  }

  @Test
  fun `given the classic style, when start spin, then sound pool does not play`() {
    // given: the classic set never had a whir
    every { settings.soundStyle } returns SoundStyle.CLASSIC
    val soundHelper = SoundHelper(context, settings, soundPool)

    // when
    soundHelper.startSpin()

    // then
    verify(exactly = 0) { soundPool.play(spinSoundId, any(), any(), any(), any(), any()) }
  }

  @Test
  fun `given sound enabled, when start spin, then sound pool plays the spin once`() {
    // given
    val soundHelper = SoundHelper(context, settings, soundPool)

    // when
    soundHelper.startSpin()

    // then: once, not looped -- it is timed to a single flip and stopped at the landing
    verify(exactly = 1) { soundPool.play(spinSoundId, 1.0f, 1.0f, 0, 0, 1.0f) }
  }

  @Test
  fun `given sound disabled, when start spin, then sound pool does not play`() {
    // given
    every { settings.soundEnabled } returns false
    val soundHelper = SoundHelper(context, settings, soundPool)

    // when
    soundHelper.startSpin()

    // then
    verify(exactly = 0) { soundPool.play(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `given a spin playing, when stop spin, then sound pool stops its stream`() {
    // given
    val soundHelper = SoundHelper(context, settings, soundPool)
    soundHelper.startSpin()

    // when
    soundHelper.stopSpin()

    // then
    verify(exactly = 1) { soundPool.stop(spinStreamId) }
  }

  @Test
  fun `given a spin already stopped, when stop spin again, then nothing more is stopped`() {
    // given: the landing stops it, and so does a flip cancelled mid-air; both can happen to one flip
    val soundHelper = SoundHelper(context, settings, soundPool)
    soundHelper.startSpin()
    soundHelper.stopSpin()

    // when
    soundHelper.stopSpin()

    // then
    verify(exactly = 1) { soundPool.stop(any()) }
  }

  @Test
  fun `given nothing playing, when stop spin, then sound pool stops nothing`() {
    // given
    val soundHelper = SoundHelper(context, settings, soundPool)

    // when
    soundHelper.stopSpin()

    // then
    verify(exactly = 0) { soundPool.stop(any()) }
  }

  @Test
  fun `given a spin playing, when start spin again, then the first is stopped before the second plays`() {
    // given
    val soundHelper = SoundHelper(context, settings, soundPool)
    soundHelper.startSpin()

    // when
    soundHelper.startSpin()

    // then
    verifyOrder {
      soundPool.play(spinSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
      soundPool.stop(spinStreamId)
      soundPool.play(spinSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
    }
  }
}