package com.banasiak.coinflip

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.hardware.SensorManager
import android.os.Vibrator
import androidx.preference.PreferenceManager
import com.banasiak.coinflip.common.BuildInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import kotlin.random.Random

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
  @Provides
  fun provideBuildInfo(@ApplicationContext context: Context): BuildInfo {
    return BuildInfo(context.packageName, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
  }

  @Provides
  fun provideClock(): Clock {
    return Clock.systemUTC()
  }

  @Provides
  fun provideRandom(): Random {
    return Random.Default
  }

  @Provides
  fun provideResources(@ApplicationContext context: Context): Resources {
    return context.resources
  }

  @Provides
  fun provideSensorManager(@ApplicationContext context: Context): SensorManager {
    return context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  }

  @Provides
  fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
    return PreferenceManager.getDefaultSharedPreferences(context)
  }

  @Suppress("DEPRECATION") // VibratorManager requires API 31
  @Provides
  fun provideVibrator(@ApplicationContext context: Context): Vibrator {
    return context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
  }
}