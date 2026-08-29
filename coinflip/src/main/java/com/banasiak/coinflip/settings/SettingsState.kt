package com.banasiak.coinflip.settings

import androidx.annotation.StringRes

data class SettingsState(
  val coin: String = Setting.COIN.default,
  val animate: Boolean = Setting.ANIMATE.default,
  val shake: Boolean = Setting.SHAKE.default,
  val sound: Boolean = Setting.SOUND.default,
  val text: Boolean = Setting.TEXT.default,
  val vibrate: Boolean = Setting.VIBRATE.default,
  val stats: Boolean = Setting.STATS.default,
  val streak: Boolean = Setting.STREAK.default,
  val quickReset: Boolean = Setting.QUICK_RESET.default,
  val customHeads: String? = Setting.CUSTOM_HEADS_TEXT.default,
  val customTails: String? = Setting.CUSTOM_TAILS_TEXT.default,
  val dynamic: Boolean = Setting.DYNAMIC.default,
  val secureRandom: Boolean = Setting.SECURE_RANDOM.default,
  val force: ShakeForce = Setting.FORCE.default,
  val favorites: Set<String> = Setting.FAVORITES.default,
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
  data class SetForce(val value: ShakeForce) : SettingsAction()
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