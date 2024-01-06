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

  val coin get() = prefs.getString("coin", "gw")!!
  val shake get() = prefs.getBoolean("shake", true)
  val sound get() = prefs.getBoolean("sound", true)
  val stats get() = prefs.getBoolean("stats", true)
  val vibrate get() = prefs.getBoolean("vibrate", true)
  val diagnostics get() = prefs.getString("diagnostics", "10000")!!.toLong()
  val force get() = prefs.getString("force", "medium").toSensitivity()

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