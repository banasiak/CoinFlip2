package com.banasiak.coinflip.settings

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.common.CustomCoin
import com.banasiak.coinflip.common.Stats
import com.banasiak.coinflip.util.CoinImage
import com.banasiak.coinflip.util.CustomCoinStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val settings: SettingsManager,
  private val customCoins: CustomCoinStore
) : ViewModel() {
  private var state = loadState()
  private val _stateFlow = MutableStateFlow(state)
  val stateFlow = _stateFlow.asStateFlow()

  private val _effectFlow = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
  val effectFlow = _effectFlow.asSharedFlow()

  // the stats prior to a reset, retained so the user can undo the reset
  private var previousStats: Stats? = null

  // the prefix that was selected before a delete reset it, retained so the undo can put it back.
  // One field for two coins is enough because only one of them can be the selected one, but every
  // read of it is gated on the coin it belongs to so the other's delete cannot clear it
  private var deletedSelection: String? = null

  // deletes the user has asked for which have not touched the disk yet: each waits for the snackbar
  // to give up on it, so undoing costs nothing and leaves no renamed files behind. A set rather
  // than a flag because both coins can be waiting at once, and a write to one must not settle the
  // other's
  private val deletePending = mutableSetOf<CustomCoin>()

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
      is SettingsAction.PickedCustomImage -> emit(state.copy(pendingCrop = PendingCrop(action.uri, action.face)))
      is SettingsAction.PickedCustomEmoji -> onPickedCustomEmoji(action.emoji, action.face)
      is SettingsAction.CropCustomImage -> onCropCustomImage(action.crop, action.adjustment)
      SettingsAction.DismissCustomCrop -> emit(state.copy(pendingCrop = null))
      SettingsAction.CustomImageFailed -> onCustomImageFailed()
      is SettingsAction.DeleteCustomCoin -> onDeleteCustomCoin(action.coin)
      is SettingsAction.UndoDeleteCustomCoin -> onUndoDeleteCustomCoin(action.coin)
      is SettingsAction.CommitDeleteCustomCoin -> onCommitDeleteCustomCoin(action.coin)
      is SettingsAction.SetCustomRim -> persist(Setting.CUSTOM_COIN_RIM, action.value) { copy(customRim = action.value) }
    }
  }

  /** A face at roughly [targetPx], for the dialog that sets it. Null when the face is not set. */
  fun thumbnail(coin: CustomCoin, face: CustomCoin.Face, targetPx: Int): ImageBitmap? =
    customCoins.thumbnail(coin, face, targetPx)?.asImageBitmap()

  private fun onCropCustomImage(crop: IntRect, adjustment: CoinImage.Orientation) {
    val pending = state.pendingCrop ?: return
    // closed before the write, so the dialog does not sit on screen through the decode
    emit(state.copy(pendingCrop = null))
    viewModelScope.launch {
      settlePendingDelete(CustomCoin.PHOTO)
      if (customCoins.save(pending.uri, pending.face, crop, adjustment)) {
        emit(state.withCustomCoin(CustomCoin.PHOTO))
      } else {
        _effectFlow.tryEmit(SettingsEffect.ShowSnackbar(R.string.coin_crop_failed))
      }
    }
  }

  private fun onPickedCustomEmoji(emoji: String, face: CustomCoin.Face) {
    // nothing to close: the picker lives in the composition and dismisses itself
    viewModelScope.launch {
      settlePendingDelete(CustomCoin.EMOJI)
      if (customCoins.save(emoji, face)) {
        settings.setEmojiFace(face, emoji)
        emit(state.withCustomCoin(CustomCoin.EMOJI))
      } else {
        _effectFlow.tryEmit(SettingsEffect.ShowSnackbar(R.string.settings_item_custom_coin_save_failed))
      }
    }
  }

  /**
   * Runs a delete of [coin] the snackbar never settled, **before** the write that follows it.
   *
   * Both directions of that order are load-bearing, and one of them shipped wrong. Called off
   * instead of run -- which is what [clearPendingDelete] does, and why this is not that -- the face
   * that is *not* being replaced survives on disk, where `storedFaces` finds it and rebuilds the
   * coin the user had just deleted. Run after the write instead, it unlinks the file just written.
   */
  private suspend fun settlePendingDelete(coin: CustomCoin) {
    if (!deletePending.remove(coin)) return
    if (coin.prefix == deletedSelection) deletedSelection = null
    customCoins.deleteAll(coin)
  }

  private fun onCustomImageFailed() {
    emit(state.copy(pendingCrop = null))
    _effectFlow.tryEmit(SettingsEffect.ShowSnackbar(R.string.coin_crop_failed))
  }

  private fun onDeleteCustomCoin(coin: CustomCoin) {
    if (state.stateFor(coin).faces.isEmpty()) return
    deletePending += coin
    // the entry leaves the picker with the artwork, so the selection cannot be left pointing at it.
    // Assigned only when this coin is the one displaced, so deleting the other does not overwrite a
    // record still owed to an undo
    if (state.coin == coin.prefix) {
      deletedSelection = coin.prefix
      settings.update(Setting.COIN, Setting.COIN.default)
    }

    // the coin reads as gone straight away even though the files are still there; the state is what
    // the screen should show, and what is on disk stays recoverable until the snackbar times out
    emit(state.copy(coin = settings.coinPrefix, custom = state.custom + (coin to CustomCoinState())))
    _effectFlow.tryEmit(
      SettingsEffect.ShowSnackbar(
        message = deletedMessage(coin),
        actionLabel = R.string.undo,
        action = SettingsAction.UndoDeleteCustomCoin(coin),
        onDismissed = SettingsAction.CommitDeleteCustomCoin(coin)
      )
    )
  }

  private fun onUndoDeleteCustomCoin(coin: CustomCoin) {
    if (coin !in deletePending) return
    // nothing was ever unlinked, so this only has to stop the commit and put the selection back
    clearPendingDelete(coin)
    emit(state.copy(coin = settings.coinPrefix).withCustomCoin(coin))
  }

  private fun onCommitDeleteCustomCoin(coin: CustomCoin) {
    if (coin !in deletePending) return
    viewModelScope.launch {
      settlePendingDelete(coin)
      emit(state.withCustomCoin(coin))
    }
  }

  // calls the delete off, restoring the selection it had already reset
  private fun clearPendingDelete(coin: CustomCoin) {
    if (!deletePending.remove(coin)) return
    if (deletedSelection != coin.prefix) return
    settings.update(Setting.COIN, coin.prefix)
    deletedSelection = null
  }

  @StringRes
  private fun deletedMessage(coin: CustomCoin): Int =
    when (coin) {
      CustomCoin.PHOTO -> R.string.settings_item_photo_coin_deleted
      CustomCoin.EMOJI -> R.string.settings_item_emoji_coin_deleted
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

  /**
   * Leaving Settings takes the snackbar with it, so a delete still waiting on one would otherwise
   * hang forever and quietly undo itself. Walking away is a decision too, and it settles this one.
   */
  override fun onCleared() {
    // detached: viewModelScope is already cancelled by the time this runs
    deletePending.forEach { customCoins.deleteAllDetached(it) }
    deletePending.clear()
    super.onCleared()
  }

  private fun <T> persist(setting: Setting<T>, value: T, transform: SettingsState.() -> SettingsState) {
    settings.update(setting, value)
    emit(state.transform())
  }

  private fun emit(value: SettingsState) {
    state = value
    _stateFlow.tryEmit(state)
  }

  /**
   * Copies over everything one custom coin's rows draw: which faces exist, the revision behind them,
   * and the glyphs for the emoji coin. Faces and revision travel together because replacing a face
   * moves only the second, and a thumbnail keyed on the first alone would go on showing the image
   * it cached.
   */
  private fun SettingsState.withCustomCoin(coin: CustomCoin): SettingsState {
    val faces = customCoins.storedFaces(coin)
    val emoji = if (coin == CustomCoin.EMOJI) settings.emojiFaces.filterKeys { it in faces } else emptyMap()
    return copy(custom = custom + (coin to CustomCoinState(faces, customCoins.revision(coin), emoji)))
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
      force = settings.force,
      customRim = settings.customCoinRim
    ).withCustomCoin(CustomCoin.PHOTO)
      .withCustomCoin(CustomCoin.EMOJI)
      .withStats(settings.loadStats())
}