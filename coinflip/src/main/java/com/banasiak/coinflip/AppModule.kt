package com.banasiak.coinflip

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.preference.PreferenceManager
import com.banasiak.coinflip.common.BuildInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.SecureRandom
import java.time.Clock
import kotlin.random.Random

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
  @Provides
  fun provideBuildInfo(@ApplicationContext context: Context): BuildInfo {
    return BuildInfo(
      Build.VERSION.SDK_INT,
      context.packageName,
      BuildConfig.VERSION_NAME,
      BuildConfig.VERSION_CODE
    )
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
  fun provideSecureRandom(): SecureRandom {
    return SecureRandom.getInstanceStrong()
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

  @Provides
  fun provideSoundPool(@ApplicationContext context: Context, buildInfo: BuildInfo): SoundPool {
    val maxStreams = 2 // coin + power-up
    return if (buildInfo.isUpsideDownCake()) {
      SoundPool.Builder()
        .setContext(context)
        .setMaxStreams(maxStreams)
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        .build()
    } else {
      @Suppress("DEPRECATION")
      SoundPool(maxStreams, AudioManager.STREAM_MUSIC, 100)
    }
  }

  @Provides
  fun provideVibrator(@ApplicationContext context: Context, buildInfo: BuildInfo): Vibrator {
    return if (buildInfo.isSnowCone()) {
      val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
      return vibratorManager.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
  }
}