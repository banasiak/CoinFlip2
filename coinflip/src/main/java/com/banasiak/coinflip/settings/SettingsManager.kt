package com.banasiak.coinflip.settings

import android.content.SharedPreferences
import com.squareup.seismic.ShakeDetector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(prefs: SharedPreferences) {
  val animation = prefs.getBoolean("animation", true)
  val shake = prefs.getBoolean("shake", true)
  val sound = prefs.getBoolean("sound", true)
  val text = prefs.getBoolean("text", true)
  val vibrate = prefs.getBoolean("vibrate", true)
  val coin = prefs.getString("coin", "gw")!!
  val diagnostics = prefs.getString("diagnostics", "10000")!!.toLong()
  val force = prefs.getInt("force", 2).toSensitivity()
  val stats = prefs.getBoolean("stats", true)

  private fun Int.toSensitivity(): Int {
    return when (this) {
      1 -> ShakeDetector.SENSITIVITY_LIGHT
      2 -> ShakeDetector.SENSITIVITY_MEDIUM
      3 -> ShakeDetector.SENSITIVITY_HARD
      else -> ShakeDetector.SENSITIVITY_MEDIUM
    }
  }
}