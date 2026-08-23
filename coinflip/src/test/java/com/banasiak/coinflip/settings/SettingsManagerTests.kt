package com.banasiak.coinflip.settings

import com.banasiak.coinflip.FakeSharedPreferences
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.settings.SettingsManager.Settings
import com.squareup.seismic.ShakeDetector
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SettingsManagerTests {
  private fun manager(vararg stored: Pair<String, Any>) = FakeSharedPreferences(stored.toMap()).let { it to SettingsManager(it) }

  @Nested
  inner class SchemaValidation {
    @Test
    fun `an old schema wipes every stored value`() {
      // given a store written by the previous version of the app
      val prefs =
        FakeSharedPreferences(
          mapOf(
            Settings.SCHEMA.key to 6,
            Settings.COIN.key to "jfk",
            Settings.HEADS.key to 42L,
            "someKeyThatNoLongerExists" to "junk"
          )
        )

      // when
      SettingsManager(prefs)

      // then only the stamped schema version survives
      prefs.values shouldBeEqualTo mutableMapOf<String, Any>(Settings.SCHEMA.key to Settings.SCHEMA.default as Int)
    }

    @Test
    fun `the wipe is committed synchronously rather than applied`() {
      // the app reads preferences immediately after construction, so the clear cannot be left in flight
      val prefs = FakeSharedPreferences(mapOf(Settings.SCHEMA.key to 6))

      SettingsManager(prefs)

      prefs.commitCount shouldBeEqualTo 1
      prefs.applyCount shouldBeEqualTo 0
    }

    @Test
    fun `a current schema leaves the stored values alone`() {
      // given
      val stored =
        mapOf<String, Any>(
          Settings.SCHEMA.key to Settings.SCHEMA.default as Int,
          Settings.COIN.key to "jfk",
          Settings.HEADS.key to 42L
        )
      val prefs = FakeSharedPreferences(stored)

      // when
      SettingsManager(prefs)

      // then
      prefs.values shouldBeEqualTo stored.toMutableMap()
    }

    @Test
    fun `a fresh install is not treated as a migration`() {
      // an absent schema key reads back as the current default, so nothing is cleared and nothing is written
      val prefs = FakeSharedPreferences()

      SettingsManager(prefs)

      prefs.values.shouldBeEqualTo(mutableMapOf<String, Any>())
      prefs.commitCount shouldBeEqualTo 0
    }
  }

  @Nested
  inner class Defaults {
    private val settings = SettingsManager(FakeSharedPreferences())

    @Test
    fun `every accessor falls back to the declared default`() {
      settings.coinPrefix shouldBeEqualTo "gw"
      settings.customHeadsText.shouldBeNull()
      settings.customTailsText.shouldBeNull()
      settings.animationEnabled.shouldBeTrue()
      settings.shakeEnabled.shouldBeTrue()
      settings.soundEnabled.shouldBeTrue()
      settings.showStats.shouldBeTrue()
      settings.textEnabled.shouldBeTrue()
      settings.vibrateEnabled.shouldBeTrue()
      settings.showQuickReset.shouldBeFalse()
      settings.dynamicColorsEnabled.shouldBeFalse()
      settings.secureRandom.shouldBeFalse()
      settings.diagnosticsIterations shouldBeEqualTo 100_000L
      settings.forceValue shouldBeEqualTo "medium"
    }

    @Test
    fun `stored values win over the defaults`() {
      val prefs =
        FakeSharedPreferences(
          mapOf(
            Settings.COIN.key to "jfk",
            Settings.CUSTOM_HEADS_TEXT.key to "CROWN",
            Settings.CUSTOM_TAILS_TEXT.key to "SHIP",
            Settings.ANIMATE.key to false,
            Settings.DIAGNOSTICS.key to "250"
          )
        )

      val settings = SettingsManager(prefs)

      settings.coinPrefix shouldBeEqualTo "jfk"
      settings.customHeadsText shouldBeEqualTo "CROWN"
      settings.customTailsText shouldBeEqualTo "SHIP"
      settings.animationEnabled.shouldBeFalse()
      settings.diagnosticsIterations shouldBeEqualTo 250L
    }
  }

  @Nested
  inner class Update {
    @Test
    fun `booleans and strings round trip`() {
      val (prefs, settings) = manager()

      settings.update(Settings.ANIMATE, false)
      settings.update(Settings.COIN, "jfk")

      prefs.values[Settings.ANIMATE.key] shouldBeEqualTo false
      prefs.values[Settings.COIN.key] shouldBeEqualTo "jfk"
      settings.animationEnabled.shouldBeFalse()
      settings.coinPrefix shouldBeEqualTo "jfk"
    }

    @Test
    fun `a null value removes the key so the localized default applies again`() {
      // custom coin labels fall back to the localized default only while the key is absent
      val (prefs, settings) = manager(Settings.CUSTOM_HEADS_TEXT.key to "CROWN")

      settings.update(Settings.CUSTOM_HEADS_TEXT, null)

      prefs.values.containsKey(Settings.CUSTOM_HEADS_TEXT.key).shouldBeFalse()
      settings.customHeadsText.shouldBeNull()
    }

    @Test
    fun `an unsupported type is rejected without writing anything`() {
      val (prefs, settings) = manager()

      val error = assertThrows<IllegalArgumentException> { settings.update(Settings.DIAGNOSTICS, 250L) }

      error.message!! shouldContain Settings.DIAGNOSTICS.key
      prefs.values.shouldBeEqualTo(mutableMapOf<String, Any>())
    }
  }

  @Nested
  inner class Stats {
    @Test
    fun `stats default to zero`() {
      val (_, settings) = manager()

      settings.loadStats() shouldBeEqualTo mapOf(Coin.Value.HEADS to 0L, Coin.Value.TAILS to 0L)
    }

    @Test
    fun `stats round trip`() {
      val (_, settings) = manager()

      settings.persistStats(mapOf(Coin.Value.HEADS to 7L, Coin.Value.TAILS to 5L))

      settings.loadStats() shouldBeEqualTo mapOf(Coin.Value.HEADS to 7L, Coin.Value.TAILS to 5L)
    }

    @Test
    fun `a missing side is persisted as zero rather than left stale`() {
      val (_, settings) = manager(Settings.HEADS.key to 7L, Settings.TAILS.key to 5L)

      settings.persistStats(mapOf(Coin.Value.HEADS to 8L))

      settings.loadStats() shouldBeEqualTo mapOf(Coin.Value.HEADS to 8L, Coin.Value.TAILS to 0L)
    }

    @Test
    fun `resetting clears both counters and nothing else`() {
      val (prefs, settings) =
        manager(
          Settings.HEADS.key to 7L,
          Settings.TAILS.key to 5L,
          Settings.COIN.key to "jfk"
        )

      settings.resetStats()

      settings.loadStats() shouldBeEqualTo mapOf(Coin.Value.HEADS to 0L, Coin.Value.TAILS to 0L)
      prefs.values shouldBeEqualTo mutableMapOf<String, Any>(Settings.COIN.key to "jfk")
    }
  }

  @Nested
  inner class ShakeSensitivity {
    @Test
    fun `each stored force maps to a seismic threshold`() {
      manager(Settings.FORCE.key to "low").second.shakeSensitivity shouldBeEqualTo ShakeDetector.SENSITIVITY_LIGHT
      manager(Settings.FORCE.key to "medium").second.shakeSensitivity shouldBeEqualTo ShakeDetector.SENSITIVITY_MEDIUM
      manager(Settings.FORCE.key to "high").second.shakeSensitivity shouldBeEqualTo ShakeDetector.SENSITIVITY_HARD
    }

    @Test
    fun `an unrecognized force falls back to medium`() {
      manager(Settings.FORCE.key to "ludicrous").second.shakeSensitivity shouldBeEqualTo ShakeDetector.SENSITIVITY_MEDIUM
      manager().second.shakeSensitivity shouldBeEqualTo ShakeDetector.SENSITIVITY_MEDIUM
    }

    @Test
    fun `the raw force value is exposed separately for the segmented control`() {
      manager(Settings.FORCE.key to "high").second.forceValue shouldBeEqualTo "high"
    }
  }

  @Test
  fun `change listeners are notified when a value is written`() {
    // RNG hot-swaps its source off this callback, so the wiring matters as much as the write
    val (_, settings) = manager()
    val changed = mutableListOf<String?>()

    settings.registerChangeListener { _, key -> changed += key }
    settings.update(Settings.SECURE_RANDOM, true)

    changed shouldBeEqualTo listOf(Settings.SECURE_RANDOM.key)
  }

  @Nested
  inner class Favorites {
    @Test
    fun `no favorites by default`() {
      manager().second.favoriteCoins shouldBeEqualTo emptySet()
    }

    @Test
    fun `favorites round trip`() {
      val (prefs, settings) = manager()

      settings.persistFavoriteCoins(setOf("gw", "jfk"))

      settings.favoriteCoins shouldBeEqualTo setOf("gw", "jfk")
      // one delimited string, so update() keeps its Boolean/String/null contract
      prefs.values[Settings.FAVORITES.key] shouldBeEqualTo "gw,jfk"
    }

    @Test
    fun `clearing the last favorite leaves an empty set rather than a blank entry`() {
      val (_, settings) = manager(Settings.FAVORITES.key to "gw")

      settings.persistFavoriteCoins(emptySet())

      settings.favoriteCoins shouldBeEqualTo emptySet()
    }

    @Test
    fun `a stray delimiter does not produce a blank favorite`() {
      // guards against an empty string surviving a split and matching no coin
      manager(Settings.FAVORITES.key to ",gw,,jfk,").second.favoriteCoins shouldBeEqualTo setOf("gw", "jfk")
    }

    @Test
    fun `favorites survive alongside the other settings`() {
      val (_, settings) = manager(Settings.COIN.key to "jfk")

      settings.persistFavoriteCoins(setOf("gw"))

      settings.coinPrefix shouldBeEqualTo "jfk"
      settings.favoriteCoins shouldBeEqualTo setOf("gw")
    }
  }
}