package com.banasiak.coinflip.settings

import android.content.SharedPreferences
import com.banasiak.coinflip.common.Coin
import com.squareup.seismic.ShakeDetector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(private val prefs: SharedPreferences) {
  companion object {
    private const val HEADS_COUNT = "headsCount"
    private const val TAILS_COUNT = "tailsCount"
  }

  val coinPrefix get() = prefs.getString("coin", "gw")!!
  val shakeEnabled get() = prefs.getBoolean("shake", true)
  val soundEnabled get() = prefs.getBoolean("sound", true)
  val showStats get() = prefs.getBoolean("stats", true)
  val vibrateEnabled get() = prefs.getBoolean("vibrate", true)
  val diagnosticsIterations get() = prefs.getString("diagnostics", "10000")!!.toLong()
  val dynamicColorsEnabled get() = prefs.getBoolean("dynamic", true)
  val shakeSensitivity get() = prefs.getString("force", "medium").toSensitivity()

  fun loadStats(): Map<Coin.Value, Long> {
    val map: MutableMap<Coin.Value, Long> = mutableMapOf()
    map[Coin.Value.HEADS] = prefs.getLong(HEADS_COUNT, 0)
    map[Coin.Value.TAILS] = prefs.getLong(TAILS_COUNT, 0)
    return map
  }

  fun persistStats(stats: Map<Coin.Value, Long>) {
    val editor = prefs.edit()
    editor.putLong(HEADS_COUNT, stats.getOrDefault(Coin.Value.HEADS, 0))
    editor.putLong(TAILS_COUNT, stats.getOrDefault(Coin.Value.TAILS, 0))
    editor.apply()
  }

  fun resetStats() {
    prefs.edit().remove(HEADS_COUNT).remove(TAILS_COUNT).apply()
  }

  private fun String?.toSensitivity(): Int {
    return when (this) {
      "low" -> ShakeDetector.SENSITIVITY_LIGHT
      "medium" -> ShakeDetector.SENSITIVITY_MEDIUM
      "high" -> ShakeDetector.SENSITIVITY_HARD
      else -> ShakeDetector.SENSITIVITY_MEDIUM
    }
  }
}