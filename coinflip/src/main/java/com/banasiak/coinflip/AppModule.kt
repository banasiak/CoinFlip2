package com.banasiak.coinflip

import com.banasiak.coinflip.common.BuildInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  fun provideBuildInfo(): BuildInfo {
    return BuildInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
  }
}