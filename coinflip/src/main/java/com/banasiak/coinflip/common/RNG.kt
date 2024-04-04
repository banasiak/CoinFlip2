package com.banasiak.coinflip.common

import android.content.SharedPreferences
import com.banasiak.coinflip.settings.SettingsManager
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class RNG @Inject constructor(
  private val random: Random,
  private val secureRandom: SecureRandom,
  private val settings: SettingsManager
) : SharedPreferences.OnSharedPreferenceChangeListener {
  var useSecureRandom: Boolean = false
    private set(value) {
      field = value
      Timber.i("useSecureRandom: $value")
    }

  init {
    settings.registerChangeListener(this)
    useSecureRandom = settings.secureRandom
  }

  fun nextBoolean(): Boolean {
    return if (useSecureRandom) {
      secureRandom.nextBoolean()
    } else {
      random.nextBoolean()
    }
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    // cache this value when the setting changes, so we don't have unnecessary SharedPreferences I/O
    if (key == SettingsManager.Settings.SECURE_RANDOM.key) {
      useSecureRandom = settings.secureRandom
    }
  }
}