package com.banasiak.coinflip.settings

import androidx.lifecycle.ViewModel
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.settings.SettingsManager.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val settings: SettingsManager
) : ViewModel() {
  private var state = loadState()
  private val _stateFlow = MutableStateFlow(state)
  val stateFlow = _stateFlow.asStateFlow()

  private val _effectFlow = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
  val effectFlow = _effectFlow.asSharedFlow()

  // the stats prior to a reset, retained so the user can undo the reset
  private var previousStats: Map<Coin.Value, Long>? = null

  fun postAction(action: SettingsAction) {
    when (action) {
      is SettingsAction.SetCoin -> persist(Settings.COIN, action.value) { copy(coin = action.value) }
      is SettingsAction.SetAnimate -> persist(Settings.ANIMATE, action.value) { copy(animate = action.value) }
      is SettingsAction.SetShake -> persist(Settings.SHAKE, action.value) { copy(shake = action.value) }
      is SettingsAction.SetSound -> persist(Settings.SOUND, action.value) { copy(sound = action.value) }
      is SettingsAction.SetText -> persist(Settings.TEXT, action.value) { copy(text = action.value) }
      is SettingsAction.SetVibrate -> persist(Settings.VIBRATE, action.value) { copy(vibrate = action.value) }
      is SettingsAction.SetStats -> persist(Settings.STATS, action.value) { copy(stats = action.value) }
      is SettingsAction.SetQuickReset -> persist(Settings.QUICK_RESET, action.value) { copy(quickReset = action.value) }
      is SettingsAction.SetCustomHeads -> persist(Settings.CUSTOM_HEADS_TEXT, action.value) { copy(customHeads = action.value) }
      is SettingsAction.SetCustomTails -> persist(Settings.CUSTOM_TAILS_TEXT, action.value) { copy(customTails = action.value) }
      is SettingsAction.SetSecureRandom -> persist(Settings.SECURE_RANDOM, action.value) { copy(secureRandom = action.value) }
      is SettingsAction.SetForce -> persist(Settings.FORCE, action.value) { copy(force = action.value) }
      is SettingsAction.SetDiagnostics -> onSetDiagnostics(action.value)
      is SettingsAction.SetDynamic -> onSetDynamic(action.value)
      SettingsAction.ResetStats -> onResetStats()
      SettingsAction.UndoResetStats -> previousStats?.let { settings.persistStats(it) }
    }
  }

  private fun onSetDiagnostics(value: String) {
    // don't allow the user to set a value that can't be safely converted to a Long in the future
    val number = value.toLongOrNull()
    if (number == null || number == 0L) {
      Timber.w("Not persisting invalid iteration count: $value")
      _effectFlow.tryEmit(SettingsEffect.ShowSnackbar(R.string.invalid_iterations))
      return
    }
    persist(Settings.DIAGNOSTICS, value) { copy(diagnostics = value) }
  }

  private fun onSetDynamic(value: Boolean) {
    persist(Settings.DYNAMIC, value) { copy(dynamic = value) }
    // dynamic colors can only be re-applied by recreating the activity, which the fragment does on back
    _effectFlow.tryEmit(SettingsEffect.EnableRestartOnBack)
  }

  private fun onResetStats() {
    previousStats = settings.loadStats()
    settings.resetStats()
    _effectFlow.tryEmit(
      SettingsEffect.ShowSnackbar(
        message = R.string.stats_reset_message,
        actionLabel = R.string.undo,
        action = SettingsAction.UndoResetStats
      )
    )
  }

  private fun persist(setting: Settings, value: Any?, transform: SettingsState.() -> SettingsState) {
    settings.update(setting, value)
    state = state.transform()
    _stateFlow.tryEmit(state)
  }

  private fun loadState(): SettingsState {
    return SettingsState(
      coin = settings.coinPrefix,
      animate = settings.animationEnabled,
      shake = settings.shakeEnabled,
      sound = settings.soundEnabled,
      text = settings.textEnabled,
      vibrate = settings.vibrateEnabled,
      stats = settings.showStats,
      quickReset = settings.showQuickReset,
      customHeads = settings.customHeadsText,
      customTails = settings.customTailsText,
      diagnostics = settings.diagnosticsIterations.toString(),
      dynamic = settings.dynamicColorsEnabled,
      secureRandom = settings.secureRandom,
      force = settings.forceValue
    )
  }
}