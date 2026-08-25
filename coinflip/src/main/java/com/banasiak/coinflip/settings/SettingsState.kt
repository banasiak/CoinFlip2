package com.banasiak.coinflip.settings

import androidx.annotation.StringRes
import com.banasiak.coinflip.settings.SettingsManager.Settings

data class SettingsState(
  val coin: String = Settings.COIN.default as String,
  val animate: Boolean = Settings.ANIMATE.default as Boolean,
  val shake: Boolean = Settings.SHAKE.default as Boolean,
  val sound: Boolean = Settings.SOUND.default as Boolean,
  val text: Boolean = Settings.TEXT.default as Boolean,
  val vibrate: Boolean = Settings.VIBRATE.default as Boolean,
  val stats: Boolean = Settings.STATS.default as Boolean,
  val streak: Boolean = Settings.STREAK.default as Boolean,
  val quickReset: Boolean = Settings.QUICK_RESET.default as Boolean,
  val customHeads: String? = Settings.CUSTOM_HEADS_TEXT.default as String?,
  val customTails: String? = Settings.CUSTOM_TAILS_TEXT.default as String?,
  val dynamic: Boolean = Settings.DYNAMIC.default as Boolean,
  val secureRandom: Boolean = Settings.SECURE_RANDOM.default as Boolean,
  val force: String = Settings.FORCE.default as String,
  val favorites: Set<String> = emptySet(),
  // how many flips are on record, so a destructive reset can say what there is to lose
  val flipCount: Long = 0,
  // the longest run each face has managed; the reset takes these too, so they are shown beside the count
  val headsRecord: Long = 0,
  val tailsRecord: Long = 0
)

sealed class SettingsAction {
  data class SetCoin(val value: String) : SettingsAction()

  /** Stars or unstars a coin in the picker; the same action does both. */
  data class ToggleFavoriteCoin(val value: String) : SettingsAction()
  data class SetAnimate(val value: Boolean) : SettingsAction()
  data class SetShake(val value: Boolean) : SettingsAction()
  data class SetSound(val value: Boolean) : SettingsAction()
  data class SetText(val value: Boolean) : SettingsAction()
  data class SetVibrate(val value: Boolean) : SettingsAction()
  data class SetStats(val value: Boolean) : SettingsAction()
  data class SetStreak(val value: Boolean) : SettingsAction()
  data class SetQuickReset(val value: Boolean) : SettingsAction()

  // null clears the override, so the label falls back to the localized default
  data class SetCustomHeads(val value: String?) : SettingsAction()
  data class SetCustomTails(val value: String?) : SettingsAction()
  data class SetDynamic(val value: Boolean) : SettingsAction()
  data class SetSecureRandom(val value: Boolean) : SettingsAction()
  data class SetForce(val value: String) : SettingsAction()
  data object ResetStats : SettingsAction()
  data object UndoResetStats : SettingsAction()
}

sealed class SettingsEffect {
  data class ShowSnackbar(
    @param:StringRes val message: Int,
    @param:StringRes val actionLabel: Int? = null,
    val action: SettingsAction? = null
  ) : SettingsEffect()

  data object EnableRestartOnBack : SettingsEffect()
}