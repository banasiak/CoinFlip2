package com.banasiak.coinflip

import android.content.Context
import android.hardware.SensorManager
import com.banasiak.coinflip.common.BuildInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  fun provideBuildInfo(): BuildInfo {
    return BuildInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
  }

  @Provides
  fun provideSensorManager(@ApplicationContext context: Context): SensorManager {
    return context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  }
}