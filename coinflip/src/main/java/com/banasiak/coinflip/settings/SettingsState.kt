package com.banasiak.coinflip.settings

import androidx.annotation.StringRes

data class SettingsState(
  val coin: String = "gw",
  val animate: Boolean = true,
  val shake: Boolean = true,
  val sound: Boolean = true,
  val text: Boolean = true,
  val vibrate: Boolean = true,
  val stats: Boolean = true,
  val quickReset: Boolean = false,
  val customHeads: String? = null,
  val customTails: String? = null,
  val diagnostics: String = "100000",
  val dynamic: Boolean = false,
  val secureRandom: Boolean = false,
  val force: String = "medium"
)

sealed class SettingsAction {
  data class SetCoin(val value: String) : SettingsAction()
  data class SetAnimate(val value: Boolean) : SettingsAction()
  data class SetShake(val value: Boolean) : SettingsAction()
  data class SetSound(val value: Boolean) : SettingsAction()
  data class SetText(val value: Boolean) : SettingsAction()
  data class SetVibrate(val value: Boolean) : SettingsAction()
  data class SetStats(val value: Boolean) : SettingsAction()
  data class SetQuickReset(val value: Boolean) : SettingsAction()
  data class SetCustomHeads(val value: String) : SettingsAction()
  data class SetCustomTails(val value: String) : SettingsAction()
  data class SetDiagnostics(val value: String) : SettingsAction()
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