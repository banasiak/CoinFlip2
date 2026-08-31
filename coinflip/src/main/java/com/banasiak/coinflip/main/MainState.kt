package com.banasiak.coinflip.main

import android.os.Parcelable
import androidx.annotation.StringRes
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.common.Stats
import com.banasiak.coinflip.ui.DurationAnimationDrawable
import com.banasiak.coinflip.util.AnimationHelper
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * How long a run has to be before it is drawn. A run of one is just "the last flip" — every flip
 * would carry a `×1` that says nothing — so the line reads exactly as it always did until a result
 * actually repeats. [MainState.streakCount] still holds the true run at one; this is a display rule.
 */
const val MIN_DRAWN_STREAK = 2L

@Parcelize
data class MainState(
  @IgnoredOnParcel val animation: DurationAnimationDrawable? = null,
  val coinImageType: CoinImageType = CoinImageType.PLACEHOLDER,
  val drawnCoin: String? = null,
  val dynamicColors: Boolean = false,
  @param:StringRes val instructionsText: Int = R.string.instructions_tap_shake,
  val labels: Pair<String?, String?> = Pair(null, null),
  val paused: Boolean = false,
  val resetVisible: Boolean = false,
  val result: Coin.Result = Coin.Result(Coin.Value.UNKNOWN, AnimationHelper.Permutation.UNKNOWN, null),
  val resultVisible: Boolean = false,
  val shakeEnabled: Boolean = false,
  val shakeSensitivity: Int = 0,
  val stats: Stats = Stats(),
  val statsVisible: Boolean = true,
  val streakVisible: Boolean = false,
  val headsCount: Long = 0,
  val tailsCount: Long = 0,
  val streakCount: Long = 0
) : Parcelable

sealed class MainAction {
  data object OnPause : MainAction()
  data class SetRimColors(val heads: Int, val tails: Int) : MainAction()
  data object OnResume : MainAction()
  data object ResetStats : MainAction()
  data object Shake : MainAction()
  data object TapAbout : MainAction()
  data object TapCoin : MainAction()
  data object TapDiagnostics : MainAction()
  data object TapSettings : MainAction()
}

sealed class MainEffect {
  data object FlipCoin : MainEffect()
  data object ToAbout : MainEffect()
  data object ToDiagnostics : MainEffect()
  data object ToSettings : MainEffect()
  data object ShowRateDialog : MainEffect()
}

enum class CoinImageType {
  ANIMATION,
  IMAGE,
  PLACEHOLDER
}