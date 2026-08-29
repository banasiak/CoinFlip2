package com.banasiak.coinflip.settings

import androidx.lifecycle.ViewModel
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.common.Stats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
  private var previousStats: Stats? = null

  fun postAction(action: SettingsAction) {
    when (action) {
      is SettingsAction.SetCoin -> persist(Setting.COIN, action.value) { copy(coin = action.value) }
      is SettingsAction.ToggleFavoriteCoin -> onToggleFavoriteCoin(action.value)
      is SettingsAction.SetAnimate -> persist(Setting.ANIMATE, action.value) { copy(animate = action.value) }
      is SettingsAction.SetShake -> persist(Setting.SHAKE, action.value) { copy(shake = action.value) }
      is SettingsAction.SetSound -> persist(Setting.SOUND, action.value) { copy(sound = action.value) }
      is SettingsAction.SetText -> persist(Setting.TEXT, action.value) { copy(text = action.value) }
      is SettingsAction.SetVibrate -> persist(Setting.VIBRATE, action.value) { copy(vibrate = action.value) }
      is SettingsAction.SetStats -> persist(Setting.STATS, action.value) { copy(stats = action.value) }
      is SettingsAction.SetStreak -> persist(Setting.STREAK, action.value) { copy(streak = action.value) }
      is SettingsAction.SetQuickReset -> persist(Setting.QUICK_RESET, action.value) { copy(quickReset = action.value) }
      is SettingsAction.SetCustomHeads -> persist(Setting.CUSTOM_HEADS_TEXT, action.value) { copy(customHeads = action.value) }
      is SettingsAction.SetCustomTails -> persist(Setting.CUSTOM_TAILS_TEXT, action.value) { copy(customTails = action.value) }
      is SettingsAction.SetSecureRandom -> persist(Setting.SECURE_RANDOM, action.value) { copy(secureRandom = action.value) }
      is SettingsAction.SetForce -> persist(Setting.FORCE, action.value) { copy(force = action.value) }
      is SettingsAction.SetDynamic -> onSetDynamic(action.value)
      SettingsAction.ResetStats -> onResetStats()
      SettingsAction.UndoResetStats -> onUndoResetStats()
    }
  }

  private fun onToggleFavoriteCoin(value: String) {
    val favorites = if (value in state.favorites) state.favorites - value else state.favorites + value
    persist(Setting.FAVORITES, favorites) { copy(favorites = favorites) }
  }

  private fun onSetDynamic(value: Boolean) {
    persist(Setting.DYNAMIC, value) { copy(dynamic = value) }
    // dynamic colors can only be re-applied by recreating the activity, which the fragment does on back
    _effectFlow.tryEmit(SettingsEffect.EnableRestartOnBack)
  }

  private fun onResetStats() {
    previousStats = settings.loadStats()
    settings.resetStats()
    emit(state.copy(flipCount = 0L, headsRecord = 0L, tailsRecord = 0L))
    _effectFlow.tryEmit(
      SettingsEffect.ShowSnackbar(
        message = R.string.stats_reset_message,
        actionLabel = R.string.undo,
        action = SettingsAction.UndoResetStats
      )
    )
  }

  private fun onUndoResetStats() {
    val previous = previousStats ?: return
    settings.persistStats(previous)
    emit(state.withStats(previous))
  }

  private fun <T> persist(setting: Setting<T>, value: T, transform: SettingsState.() -> SettingsState) {
    settings.update(setting, value)
    emit(state.transform())
  }

  private fun emit(value: SettingsState) {
    state = value
    _stateFlow.tryEmit(state)
  }

  /** Copies over every figure the Statistics section shows, so a reset and its undo cannot disagree. */
  private fun SettingsState.withStats(stats: Stats): SettingsState =
    copy(
      flipCount = stats.total,
      headsRecord = stats.record(Coin.Value.HEADS),
      tailsRecord = stats.record(Coin.Value.TAILS)
    )

  private fun loadState(): SettingsState =
    SettingsState(
      coin = settings.coinPrefix,
      favorites = settings.favoriteCoins,
      animate = settings.animationEnabled,
      shake = settings.shakeEnabled,
      sound = settings.soundEnabled,
      text = settings.textEnabled,
      vibrate = settings.vibrateEnabled,
      stats = settings.showStats,
      streak = settings.showStreak,
      quickReset = settings.showQuickReset,
      customHeads = settings.customHeadsText,
      customTails = settings.customTailsText,
      dynamic = settings.dynamicColorsEnabled,
      secureRandom = settings.secureRandom,
      force = settings.force
    ).withStats(settings.loadStats())
}