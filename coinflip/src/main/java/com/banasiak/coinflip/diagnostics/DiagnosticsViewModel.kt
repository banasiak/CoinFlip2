package com.banasiak.coinflip.diagnostics

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.util.SoundHelper
import com.banasiak.coinflip.util.formatMilliseconds
import com.banasiak.coinflip.util.formatNumber
import com.banasiak.coinflip.util.restore
import com.banasiak.coinflip.util.save
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import timber.log.Timber
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
  private val clock: Clock,
  private val coin: Coin,
  private val settings: SettingsManager,
  private val soundHelper: SoundHelper,
  private val savedState: SavedStateHandle
) : ViewModel(), LifecycleEventObserver {
  companion object {
    private const val SMOOTH_DELAY = 5L
    private const val TURBO_MODE_THRESHOLD = 1_000_000L
    private const val WIKIPEDIA_URL = "https://en.m.wikipedia.org/wiki/Random_number_generation"
  }

  private var state =
    savedState.restore()
      ?: DiagnosticsState(
        iterations = settings.diagnosticsIterations,
        turboMode = settings.diagnosticsIterations >= TURBO_MODE_THRESHOLD
      )
  private val _stateFlow = MutableStateFlow<DiagnosticsState>(state)
  val stateFlow: StateFlow<DiagnosticsState> = _stateFlow

  private val _effectFlow = MutableSharedFlow<DiagnosticsEffect>(extraBufferCapacity = 1, replay = 1)
  val effectFlow: SharedFlow<DiagnosticsEffect> = _effectFlow

  init {
    viewModelScope.launch { runDiagnostics() }
  }

  fun postAction(action: DiagnosticsAction) {
    when (action) {
      DiagnosticsAction.Wikipedia -> _effectFlow.tryEmit(DiagnosticsEffect.LaunchUrl(WIKIPEDIA_URL))
    }
  }

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    Timber.d("Lifecycle onStateChanged(): $event")
    when (event) {
      Lifecycle.Event.ON_PAUSE -> onPause()
      else -> { }
    }
  }

  private fun onPause() {
    savedState.save(state)
  }

  private suspend fun runDiagnostics() {
    // the state may have been restored, only resume the loop if it hasn't finished running, otherwise just update the UI
    if (state.finished) {
      _stateFlow.emit(state)
      return
    }

    // let's goooooooooooooooooooooooo!
    if (state.turboMode) {
      Timber.d("turbo mode activated!")
      if (settings.soundEnabled) {
        soundHelper.playSound(SoundHelper.Sound.POWERUP)
      }
      _effectFlow.emit(DiagnosticsEffect.ShowToast(R.string.turbo_mode))
    }

    // don't update the start time if state has been restored, then you can see how long the loop was "paused" for in wall-clock time
    if (state.startTime == 0L) state = state.copy(startTime = clock.millis())

    // resume the loop where we left off
    for (i in state.total..state.iterations) {
      state =
        when (val value = coin.flip().value) {
          Coin.Value.HEADS -> state.copy(heads = state.heads + 1, total = i + 1)
          Coin.Value.TAILS -> state.copy(tails = state.tails + 1, total = i + 1)
          else -> {
            throw IllegalStateException("Coin.flip() returned invalid value: $value")
          }
        }

      if (state.total % 100 == 0L || state.total == state.iterations) {
        val elapsedTime = clock.millis() - state.startTime
        state =
          state.copy(
            headsCount = state.heads.formatNumber(),
            tailsCount = state.tails.formatNumber(),
            totalCount = state.total.formatNumber(),
            headsRatio = formatRatio(state.heads, state.iterations),
            tailsRatio = formatRatio(state.tails, state.iterations),
            totalRatio = formatRatio(state.total, state.iterations),
            elapsedTime = elapsedTime,
            formattedTime = elapsedTime.formatMilliseconds()
          )
        _stateFlow.emit(state)

        if (state.turboMode) {
          // don't delay UI updates if the user has chosen to run an objectively large number of iterations
          yield()
        } else {
          // otherwise, this short delay smooths out the UI animation and make it looks nicer for small values
          delay(SMOOTH_DELAY)
        }
      }
    }
    state = state.copy(finished = true)
  }

  private fun formatRatio(numerator: Long, denominator: Long): String {
    return "[" + "%.2f".format((numerator.toDouble() / denominator.toDouble()) * 100) + "%]"
  }
}