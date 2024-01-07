package com.banasiak.coinflip

import android.app.Application
import com.banasiak.coinflip.settings.SettingsManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
  @Inject lateinit var settings: SettingsManager

  override fun onCreate() {
    super.onCreate()

    val options = DynamicColorsOptions.Builder().setPrecondition { _, _ -> settings.dynamicColorsEnabled }.build()
    DynamicColors.applyToActivitiesIfAvailable(this, options)

    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
  }
}