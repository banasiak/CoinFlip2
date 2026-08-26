package com.banasiak.coinflip.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.core.content.edit
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.common.Stats
import com.squareup.seismic.ShakeDetector
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(private val prefs: SharedPreferences) {
  companion object {
    // coin prefixes are lowercase identifiers, so a comma can never appear inside one
    private const val FAVORITES_DELIMITER = ","
  }

  val coinPrefix get() = prefs.getString(Settings.COIN.key, Settings.COIN.default as String)!! // pinky swear
  val customHeadsText get() = prefs.getString(Settings.CUSTOM_HEADS_TEXT.key, Settings.CUSTOM_HEADS_TEXT.default as String?)
  val customTailsText get() = prefs.getString(Settings.CUSTOM_TAILS_TEXT.key, Settings.CUSTOM_TAILS_TEXT.default as String?)
  val animationEnabled get() = prefs.getBoolean(Settings.ANIMATE.key, Settings.ANIMATE.default as Boolean)
  val shakeEnabled get() = prefs.getBoolean(Settings.SHAKE.key, Settings.SHAKE.default as Boolean)
  val soundEnabled get() = prefs.getBoolean(Settings.SOUND.key, Settings.SOUND.default as Boolean)
  val showQuickReset get() = prefs.getBoolean(Settings.QUICK_RESET.key, Settings.QUICK_RESET.default as Boolean)
  val showStats get() = prefs.getBoolean(Settings.STATS.key, Settings.STATS.default as Boolean)
  val showStreak get() = prefs.getBoolean(Settings.STREAK.key, Settings.STREAK.default as Boolean)
  val textEnabled get() = prefs.getBoolean(Settings.TEXT.key, Settings.TEXT.default as Boolean)
  val vibrateEnabled get() = prefs.getBoolean(Settings.VIBRATE.key, Settings.VIBRATE.default as Boolean)
  val diagnosticsIterations get() = prefs.getString(Settings.DIAGNOSTICS.key, Settings.DIAGNOSTICS.default as String)!!.toLong()
  val dynamicColorsEnabled get() = prefs.getBoolean(Settings.DYNAMIC.key, Settings.DYNAMIC.default as Boolean)
  val shakeSensitivity get() = prefs.getString(Settings.FORCE.key, Settings.FORCE.default as String).toSensitivity()
  val forceValue get() = prefs.getString(Settings.FORCE.key, Settings.FORCE.default as String)!!
  val secureRandom get() = prefs.getBoolean(Settings.SECURE_RANDOM.key, Settings.SECURE_RANDOM.default as Boolean)

  /** Coin prefixes the user has starred, stored as one delimited string so [update] keeps its simple contract. */
  val favoriteCoins: Set<String>
    get() =
      prefs.getString(Settings.FAVORITES.key, Settings.FAVORITES.default as String)
        .orEmpty()
        .split(FAVORITES_DELIMITER)
        .filter { it.isNotBlank() }
        .toSet()

  init {
    validateSchema()
  }

  /** Persists a single preference value, mirroring the keys/types written by the old PreferenceFragmentCompat. */
  fun update(setting: Settings, value: Any?) {
    prefs.edit {
      when (value) {
        is Boolean -> putBoolean(setting.key, value)
        is String -> putString(setting.key, value)
        null -> putString(setting.key, null)
        else -> throw IllegalArgumentException("Unsupported preference type for ${setting.key}: $value")
      }
    }
  }

  fun persistFavoriteCoins(values: Set<String>) {
    update(Settings.FAVORITES, values.joinToString(FAVORITES_DELIMITER))
  }

  fun loadStats(): Stats =
    Stats(
      counts =
        mapOf(
          Coin.Value.HEADS to prefs.getLong(Settings.HEADS.key, Settings.HEADS.default as Long),
          Coin.Value.TAILS to prefs.getLong(Settings.TAILS.key, Settings.TAILS.default as Long)
        ),
      records =
        mapOf(
          Coin.Value.HEADS to prefs.getLong(Settings.HEADS_RECORD.key, Settings.HEADS_RECORD.default as Long),
          Coin.Value.TAILS to prefs.getLong(Settings.TAILS_RECORD.key, Settings.TAILS_RECORD.default as Long)
        ),
      streakValue = prefs.getString(Settings.STREAK_VALUE.key, Settings.STREAK_VALUE.default as String).toCoinValue(),
      streak = prefs.getLong(Settings.STREAK_COUNT.key, Settings.STREAK_COUNT.default as Long)
    )

  fun persistStats(stats: Stats) {
    prefs.edit {
      putLong(Settings.HEADS.key, stats.count(Coin.Value.HEADS))
      putLong(Settings.TAILS.key, stats.count(Coin.Value.TAILS))
      putLong(Settings.HEADS_RECORD.key, stats.record(Coin.Value.HEADS))
      putLong(Settings.TAILS_RECORD.key, stats.record(Coin.Value.TAILS))
      putString(Settings.STREAK_VALUE.key, stats.streakValue.name)
      putLong(Settings.STREAK_COUNT.key, stats.streak)
    }
  }

  fun resetStats() {
    prefs.edit {
      remove(Settings.HEADS.key)
        .remove(Settings.TAILS.key)
        .remove(Settings.HEADS_RECORD.key)
        .remove(Settings.TAILS_RECORD.key)
        .remove(Settings.STREAK_VALUE.key)
        .remove(Settings.STREAK_COUNT.key)
    }
  }

  fun registerChangeListener(listener: OnSharedPreferenceChangeListener) {
    prefs.registerOnSharedPreferenceChangeListener(listener)
  }

  @SuppressLint("ApplySharedPref")
  private fun validateSchema() {
    // the old version of the app used keys with incompatible values -- don't bother migrating them, just reset everything
    val schemaVersion = prefs.getInt(Settings.SCHEMA.key, Settings.SCHEMA.default as Int)
    if (schemaVersion != Settings.SCHEMA.default) {
      Timber.w("Old schema detected. Clearing all values from SharedPreferences!")
      // this needs to happen ASAP, otherwise the app may crash if it attempts to load data from a previous version
      prefs.edit(commit = true) {
        clear()
        putInt(Settings.SCHEMA.key, Settings.SCHEMA.default)
      }
    }
  }

  /** The streak's face round-trips as its enum name; anything unrecognized means no run is in progress. */
  private fun String?.toCoinValue(): Coin.Value = Coin.Value.entries.firstOrNull { it.name == this } ?: Coin.Value.UNKNOWN

  private fun String?.toSensitivity(): Int =
    when (this) {
      "low" -> ShakeDetector.SENSITIVITY_LIGHT
      "medium" -> ShakeDetector.SENSITIVITY_MEDIUM
      "high" -> ShakeDetector.SENSITIVITY_HARD
      else -> ShakeDetector.SENSITIVITY_MEDIUM
    }

  enum class Settings(val key: String, val default: Any?) {
    COIN("coin", "gw"), // George Washington dollar
    CUSTOM_HEADS_TEXT("customHeadsText", null),
    CUSTOM_TAILS_TEXT("customTailsText", null),
    ANIMATE("animate", true),
    SHAKE("shake", true),
    SOUND("sound", true),
    STATS("stats", true),
    TEXT("text", true),
    VIBRATE("vibrate", true),
    DIAGNOSTICS("diagnostics", "100000"), // unfortunately the preference stores this as a string
    DYNAMIC("dynamic", false),
    QUICK_RESET("quickReset", false),
    FORCE("force", "medium"),
    SECURE_RANDOM("secureRandom", false),
    FAVORITES("favoriteCoins", ""),
    STREAK("streak", false),
    HEADS("headsCount", 0L),
    TAILS("tailsCount", 0L),
    HEADS_RECORD("headsRecord", 0L),
    TAILS_RECORD("tailsRecord", 0L),
    STREAK_VALUE("streakValue", ""), // Coin.Value.name; "" reads back as UNKNOWN
    STREAK_COUNT("streakCount", 0L),

    // adding a key needs no bump -- validateSchema() only wipes on a mismatch, and an absent key reads its default
    SCHEMA("schemaVersion", 7) // the old version of the app was '6'
  }
}