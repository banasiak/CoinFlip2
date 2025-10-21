package com.banasiak.coinflip.common

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

data class BuildInfo(
  val apiLevel: Int,
  val packageName: String,
  val versionName: String,
  val versionCode: Int
) {
  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
  fun isSnowCone() = apiLevel >= Build.VERSION_CODES.S

  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
  fun isUpsideDownCake() = apiLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
  fun isBaklava() = apiLevel >= Build.VERSION_CODES.BAKLAVA
}