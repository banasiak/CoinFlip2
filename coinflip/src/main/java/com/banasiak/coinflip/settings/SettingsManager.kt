package com.banasiak.coinflip.settings

import android.content.SharedPreferences
import com.squareup.seismic.ShakeDetector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(private val prefs: SharedPreferences) {
  val coin get() = prefs.getString("coin", "gw")!!
  val shake get() = prefs.getBoolean("shake", true)
  val sound get() = prefs.getBoolean("sound", true)
  val stats get() = prefs.getBoolean("stats", true)
  val vibrate get() = prefs.getBoolean("vibrate", true)
  val diagnostics get() = prefs.getString("diagnostics", "10000")!!.toLong()
  val force get() = prefs.getString("force", "medium").toSensitivity()

  private fun String?.toSensitivity(): Int {
    return when (this) {
      "low" -> ShakeDetector.SENSITIVITY_LIGHT
      "medium" -> ShakeDetector.SENSITIVITY_MEDIUM
      "high" -> ShakeDetector.SENSITIVITY_HARD
      else -> ShakeDetector.SENSITIVITY_MEDIUM
    }
  }
}