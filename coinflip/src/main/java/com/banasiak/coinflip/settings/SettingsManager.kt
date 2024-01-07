package com.banasiak.coinflip.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.banasiak.coinflip.common.Coin
import com.squareup.seismic.ShakeDetector
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(private val prefs: SharedPreferences) {
  val coinPrefix get() = prefs.getString(Settings.COIN.key, Settings.COIN.default as String)!!
  val shakeEnabled get() = prefs.getBoolean(Settings.SHAKE.key, Settings.SHAKE.default as Boolean)
  val soundEnabled get() = prefs.getBoolean(Settings.SOUND.key, Settings.SOUND.default as Boolean)
  val showStats get() = prefs.getBoolean(Settings.STATS.key, Settings.STATS.default as Boolean)
  val vibrateEnabled get() = prefs.getBoolean(Settings.VIBRATE.key, Settings.VIBRATE.default as Boolean)
  val diagnosticsIterations get() = prefs.getString(Settings.DIAGNOSTICS.key, Settings.DIAGNOSTICS.default as String)!!.toLong()
  val dynamicColorsEnabled get() = prefs.getBoolean(Settings.DYNAMIC.key, Settings.DYNAMIC.default as Boolean)
  val shakeSensitivity get() = prefs.getString(Settings.FORCE.key, Settings.FORCE.default as String).toSensitivity()

  init {
    validateSchema()
  }

  fun loadStats(): Map<Coin.Value, Long> {
    val map: MutableMap<Coin.Value, Long> = mutableMapOf()
    map[Coin.Value.HEADS] = prefs.getLong(Settings.HEADS.key, Settings.HEADS.default as Long)
    map[Coin.Value.TAILS] = prefs.getLong(Settings.TAILS.key, Settings.TAILS.default as Long)
    return map
  }

  fun persistStats(stats: Map<Coin.Value, Long>) {
    val editor = prefs.edit()
    editor.putLong(Settings.HEADS.key, stats.getOrDefault(Coin.Value.HEADS, 0))
    editor.putLong(Settings.TAILS.key, stats.getOrDefault(Coin.Value.TAILS, 0))
    editor.apply()
  }

  fun resetStats() {
    prefs.edit().remove(Settings.HEADS.key).remove(Settings.TAILS.key).apply()
  }

  @SuppressLint("ApplySharedPref")
  private fun validateSchema() {
    // the old version of the app used keys with incompatible values -- don't bother migrating them, just reset everything
    val schemaVersion = prefs.getInt(Settings.SCHEMA.key, Settings.SCHEMA.default as Int)
    if (schemaVersion != Settings.SCHEMA.default) {
      Timber.w("Old schema detected. Clearing all values from SharedPreferences!")
      val editor = prefs.edit()
      editor.clear()
      editor.putInt(Settings.SCHEMA.key, Settings.SCHEMA.default)
      editor.commit() // this needs to happen ASAP, otherwise the app may crash if it attempts to load data from a previous version
    }
  }

  private fun String?.toSensitivity(): Int {
    return when (this) {
      "low" -> ShakeDetector.SENSITIVITY_LIGHT
      "medium" -> ShakeDetector.SENSITIVITY_MEDIUM
      "high" -> ShakeDetector.SENSITIVITY_HARD
      else -> ShakeDetector.SENSITIVITY_MEDIUM
    }
  }

  enum class Settings(val key: String, val default: Any) {
    COIN("coin", "gw"), // George Washington dollar
    SHAKE("shake", true),
    SOUND("sound", true),
    STATS("stats", true),
    VIBRATE("vibrate", true),
    DIAGNOSTICS("diagnostics", "100000"), // unfortunately the preference stores this as a string
    DYNAMIC("dynamic", true),
    FORCE("force", "medium"),
    HEADS("headsCount", 0L),
    TAILS("tailsCount", 0L),
    SCHEMA("schemaVersion", 7) // the old version of the app was '6'
  }
}