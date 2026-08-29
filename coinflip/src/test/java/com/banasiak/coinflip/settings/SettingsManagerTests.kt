package com.banasiak.coinflip.settings

import com.banasiak.coinflip.FakeSharedPreferences
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.common.Stats
import com.squareup.seismic.ShakeDetector
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
            Setting.SCHEMA.key to 6,
            Setting.COIN.key to "jfk",
            Setting.HEADS.key to 42L,
            "someKeyThatNoLongerExists" to "junk"
          )
        )

      // when
      SettingsManager(prefs)

      // then only the stamped schema version survives
      prefs.values shouldBeEqualTo mutableMapOf<String, Any>(Setting.SCHEMA.key to Setting.SCHEMA.default)
    }

    @Test
    fun `the wipe is committed synchronously rather than applied`() {
      // the app reads preferences immediately after construction, so the clear cannot be left in flight
      val prefs = FakeSharedPreferences(mapOf(Setting.SCHEMA.key to 6))

      SettingsManager(prefs)

      prefs.commitCount shouldBeEqualTo 1
      prefs.applyCount shouldBeEqualTo 0
    }

    @Test
    fun `a current schema leaves the stored values alone`() {
      // given
      val stored =
        mapOf<String, Any>(
          Setting.SCHEMA.key to Setting.SCHEMA.default,
          Setting.COIN.key to "jfk",
          Setting.HEADS.key to 42L
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
      settings.force shouldBeEqualTo ShakeForce.MEDIUM
    }

    @Test
    fun `stored values win over the defaults`() {
      val prefs =
        FakeSharedPreferences(
          mapOf(
            Setting.COIN.key to "jfk",
            Setting.CUSTOM_HEADS_TEXT.key to "CROWN",
            Setting.CUSTOM_TAILS_TEXT.key to "SHIP",
            Setting.ANIMATE.key to false,
            Setting.DIAGNOSTICS.key to "250"
          )
        )

      val settings = SettingsManager(prefs)

      settings.coinPrefix shouldBeEqualTo "jfk"
      settings.customHeadsText shouldBeEqualTo "CROWN"
      settings.customTailsText shouldBeEqualTo "SHIP"
      settings.animationEnabled.shouldBeFalse()
      settings.diagnosticsIterations shouldBeEqualTo 250L
    }

    @Test
    fun `a diagnostics count that is not a number falls back rather than throwing`() {
      // it is stored as a string for the old inflater's sake, so the store can hold anything at all
      val settings = SettingsManager(FakeSharedPreferences(mapOf(Setting.DIAGNOSTICS.key to "over nine thousand")))

      settings.diagnosticsIterations shouldBeEqualTo 100_000L
    }
  }

  @Nested
  inner class Update {
    @Test
    fun `booleans and strings round trip`() {
      val (prefs, settings) = manager()

      settings.update(Setting.ANIMATE, false)
      settings.update(Setting.COIN, "jfk")

      prefs.values[Setting.ANIMATE.key] shouldBeEqualTo false
      prefs.values[Setting.COIN.key] shouldBeEqualTo "jfk"
      settings.animationEnabled.shouldBeFalse()
      settings.coinPrefix shouldBeEqualTo "jfk"
    }

    @Test
    fun `a null value removes the key so the localized default applies again`() {
      // custom coin labels fall back to the localized default only while the key is absent
      val (prefs, settings) = manager(Setting.CUSTOM_HEADS_TEXT.key to "CROWN")

      settings.update(Setting.CUSTOM_HEADS_TEXT, null)

      prefs.values.containsKey(Setting.CUSTOM_HEADS_TEXT.key).shouldBeFalse()
      settings.customHeadsText.shouldBeNull()
    }
  }

  @Nested
  inner class Statistics {
    /** What an untouched store reads back as: both counters and both records at zero, no run going. */
    private fun zeroed() =
      Stats(
        counts = mapOf(Coin.Value.HEADS to 0L, Coin.Value.TAILS to 0L),
        records = mapOf(Coin.Value.HEADS to 0L, Coin.Value.TAILS to 0L),
        streakValue = Coin.Value.UNKNOWN,
        streak = 0L
      )

    @Test
    fun `stats default to zero`() {
      val (_, settings) = manager()

      settings.loadStats() shouldBeEqualTo zeroed()
    }

    @Test
    fun `counts, records and the run in progress all round trip`() {
      val (_, settings) = manager()
      val stats =
        Stats(
          counts = mapOf(Coin.Value.HEADS to 7L, Coin.Value.TAILS to 5L),
          records = mapOf(Coin.Value.HEADS to 4L, Coin.Value.TAILS to 3L),
          streakValue = Coin.Value.TAILS,
          streak = 2L
        )

      settings.persistStats(stats)

      settings.loadStats() shouldBeEqualTo stats
    }

    @Test
    fun `a missing side is persisted as zero rather than left stale`() {
      val (_, settings) = manager(Setting.HEADS.key to 7L, Setting.TAILS.key to 5L)

      settings.persistStats(Stats(counts = mapOf(Coin.Value.HEADS to 8L)))

      settings.loadStats() shouldBeEqualTo zeroed().copy(counts = mapOf(Coin.Value.HEADS to 8L, Coin.Value.TAILS to 0L))
    }

    @Test
    fun `an unrecognized streak face reads back as unknown`() {
      // a value written by a future build, or corrupted -- it must not blow up the load
      val (_, settings) = manager(Setting.STREAK_VALUE.key to "SIDEWAYS", Setting.STREAK_COUNT.key to 3L)

      settings.loadStats() shouldBeEqualTo zeroed().copy(streakValue = Coin.Value.UNKNOWN, streak = 3L)
    }

    @Test
    fun `resetting clears every counter and nothing else`() {
      val (prefs, settings) =
        manager(
          Setting.HEADS.key to 7L,
          Setting.TAILS.key to 5L,
          Setting.HEADS_RECORD.key to 4L,
          Setting.TAILS_RECORD.key to 3L,
          Setting.STREAK_VALUE.key to Coin.Value.HEADS.name,
          Setting.STREAK_COUNT.key to 2L,
          Setting.COIN.key to "jfk"
        )

      settings.resetStats()

      settings.loadStats() shouldBeEqualTo zeroed()
      prefs.values shouldBeEqualTo mutableMapOf<String, Any>(Setting.COIN.key to "jfk")
    }
  }

  @Nested
  inner class ShakeSensitivity {
    @Test
    fun `each stored force maps to a seismic threshold`() {
      manager(Setting.FORCE.key to "low").second.shakeSensitivity shouldBeEqualTo ShakeDetector.SENSITIVITY_LIGHT
      manager(Setting.FORCE.key to "medium").second.shakeSensitivity shouldBeEqualTo ShakeDetector.SENSITIVITY_MEDIUM
      manager(Setting.FORCE.key to "high").second.shakeSensitivity shouldBeEqualTo ShakeDetector.SENSITIVITY_HARD
    }

    @Test
    fun `the stored string decodes back to the choice the user made`() {
      manager(Setting.FORCE.key to "high").second.force shouldBeEqualTo ShakeForce.HIGH
      // and falls back with the sensitivity, so the control never renders with nothing selected
      manager(Setting.FORCE.key to "ludicrous").second.force shouldBeEqualTo ShakeForce.MEDIUM
    }

    @Test
    fun `an unrecognized force falls back to medium`() {
      manager(Setting.FORCE.key to "ludicrous").second.shakeSensitivity shouldBeEqualTo ShakeDetector.SENSITIVITY_MEDIUM
      manager().second.shakeSensitivity shouldBeEqualTo ShakeDetector.SENSITIVITY_MEDIUM
    }

    @Test
    fun `every force names a sensitivity of its own`() {
      // two forces on the same threshold would give the user three buttons and two outcomes
      val sensitivities = ShakeForce.entries.map { it.sensitivity }

      sensitivities.distinct().size shouldBeEqualTo ShakeForce.entries.size
    }
  }

  @Test
  fun `change listeners are notified when a value is written`() {
    // RNG hot-swaps its source off this callback, so the wiring matters as much as the write
    val (_, settings) = manager()
    val changed = mutableListOf<String?>()

    settings.registerChangeListener { _, key -> changed += key }
    settings.update(Setting.SECURE_RANDOM, true)

    changed shouldBeEqualTo listOf(Setting.SECURE_RANDOM.key)
  }

  @Nested
  inner class Favorites {
    @Test
    fun `no favorites by default`() {
      manager().second.favoriteCoins shouldBeEqualTo emptySet()
    }

    @Test
    fun `favorites round trip as a set rather than an encoded string`() {
      val (prefs, settings) = manager()

      settings.update(Setting.FAVORITES, setOf("gw", "jfk"))

      settings.favoriteCoins shouldBeEqualTo setOf("gw", "jfk")
      prefs.values[Setting.FAVORITES.key] shouldBeEqualTo setOf("gw", "jfk")
    }

    @Test
    fun `clearing the last favorite leaves an empty set`() {
      val (_, settings) = manager(Setting.FAVORITES.key to mutableSetOf("gw"))

      settings.update(Setting.FAVORITES, emptySet())

      settings.favoriteCoins shouldBeEqualTo emptySet()
    }

    @Test
    fun `mutating the set that was written does not reach the store`() {
      val outgoing = mutableSetOf("gw")
      val (_, settings) = manager()

      settings.update(Setting.FAVORITES, outgoing)
      outgoing += "jfk"

      settings.favoriteCoins shouldBeEqualTo setOf("gw")
    }

    @Test
    fun `mutating the store does not reach a set already read`() {
      // getStringSet hands back the very instance it is holding, so the read has to be a snapshot
      val stored = mutableSetOf("gw")
      val (_, settings) = manager(Setting.FAVORITES.key to stored)

      val read = settings.favoriteCoins
      stored += "jfk"

      read shouldBeEqualTo setOf("gw")
    }

    @Test
    fun `favorites survive alongside the other settings`() {
      val (_, settings) = manager(Setting.COIN.key to "jfk")

      settings.update(Setting.FAVORITES, setOf("gw"))

      settings.coinPrefix shouldBeEqualTo "jfk"
      settings.favoriteCoins shouldBeEqualTo setOf("gw")
    }
  }
}