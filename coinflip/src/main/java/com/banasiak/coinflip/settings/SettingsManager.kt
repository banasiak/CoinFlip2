package com.banasiak.coinflip.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.core.content.edit
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.common.Stats
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(private val prefs: SharedPreferences) {
  companion object {
    /** What a reset clears, named once so it cannot drift from what [persistStats] writes. */
    private val STAT_SETTINGS =
      listOf(
        Setting.HEADS,
        Setting.TAILS,
        Setting.HEADS_RECORD,
        Setting.TAILS_RECORD,
        Setting.STREAK_VALUE,
        Setting.STREAK_COUNT
      )
  }

  val coinPrefix get() = prefs[Setting.COIN]
  val customHeadsText get() = prefs[Setting.CUSTOM_HEADS_TEXT]
  val customTailsText get() = prefs[Setting.CUSTOM_TAILS_TEXT]
  val animationEnabled get() = prefs[Setting.ANIMATE]
  val shakeEnabled get() = prefs[Setting.SHAKE]
  val soundEnabled get() = prefs[Setting.SOUND]
  val showQuickReset get() = prefs[Setting.QUICK_RESET]
  val showStats get() = prefs[Setting.STATS]
  val showStreak get() = prefs[Setting.STREAK]
  val textEnabled get() = prefs[Setting.TEXT]
  val vibrateEnabled get() = prefs[Setting.VIBRATE]
  val diagnosticsIterations get() = prefs[Setting.DIAGNOSTICS]
  val dynamicColorsEnabled get() = prefs[Setting.DYNAMIC]
  val secureRandom get() = prefs[Setting.SECURE_RANDOM]

  /** The choice the user made; [shakeSensitivity] is the threshold seismic wants for it. */
  val force get() = prefs[Setting.FORCE]
  val shakeSensitivity get() = force.sensitivity

  // whether the custom coin's faces are ringed. Off suits an image that is already a coin.
  val customCoinRim get() = prefs[Setting.CUSTOM_COIN_RIM]

  /** Coin prefixes the user has starred. */
  val favoriteCoins get() = prefs[Setting.FAVORITES]

  init {
    validateSchema()
  }

  /** Persists a single preference. The setting carries its own type, so a mismatch will not compile. */
  fun <T> update(setting: Setting<T>, value: T) {
    prefs.edit { this[setting] = value }
  }

  fun loadStats(): Stats =
    Stats(
      counts = mapOf(Coin.Value.HEADS to prefs[Setting.HEADS], Coin.Value.TAILS to prefs[Setting.TAILS]),
      records = mapOf(Coin.Value.HEADS to prefs[Setting.HEADS_RECORD], Coin.Value.TAILS to prefs[Setting.TAILS_RECORD]),
      streakValue = prefs[Setting.STREAK_VALUE],
      streak = prefs[Setting.STREAK_COUNT]
    )

  fun persistStats(stats: Stats) {
    prefs.edit {
      this[Setting.HEADS] = stats.count(Coin.Value.HEADS)
      this[Setting.TAILS] = stats.count(Coin.Value.TAILS)
      this[Setting.HEADS_RECORD] = stats.record(Coin.Value.HEADS)
      this[Setting.TAILS_RECORD] = stats.record(Coin.Value.TAILS)
      this[Setting.STREAK_VALUE] = stats.streakValue
      this[Setting.STREAK_COUNT] = stats.streak
    }
  }

  fun resetStats() {
    prefs.edit {
      STAT_SETTINGS.forEach { remove(it.key) }
    }
  }

  /**
   * Registers for preference changes for as long as [listener] is strongly held *elsewhere*.
   * SharedPreferences keeps its listeners weakly, so one that nothing else retains is collected and
   * silently stops firing. The only caller is `RNG`, a singleton, which is why there is no
   * unregister to match.
   */
  fun registerChangeListener(listener: OnSharedPreferenceChangeListener) {
    prefs.registerOnSharedPreferenceChangeListener(listener)
  }

  @SuppressLint("ApplySharedPref")
  private fun validateSchema() {
    // the old version of the app used keys with incompatible values -- don't bother migrating them, just reset everything
    if (prefs[Setting.SCHEMA] != Setting.SCHEMA.default) {
      Timber.w("Old schema detected. Clearing all values from SharedPreferences!")
      // this needs to happen ASAP, otherwise the app may crash if it attempts to load data from a previous version
      prefs.edit(commit = true) {
        clear()
        this[Setting.SCHEMA] = Setting.SCHEMA.default
      }
    }
  }
}