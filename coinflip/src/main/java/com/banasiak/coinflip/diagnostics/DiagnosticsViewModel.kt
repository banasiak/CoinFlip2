package com.banasiak.coinflip.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.util.formatMilliseconds
import com.banasiak.coinflip.util.formatNumber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
  settings: SettingsManager,
  private val coin: Coin
) : ViewModel() {
  companion object {
    private const val SMOOTH_DELAY = 5L
    private const val SMOOTH_DELAY_THRESHOLD = 100_000L
    private const val WIKIPEDIA_URL = "https://en.wikipedia.org/wiki/Random_number_generation"
  }

  private var state = DiagnosticsState(iterations = settings.diagnosticsIterations)
  private val _stateFlow = MutableStateFlow<DiagnosticsState>(state)
  val stateFlow: StateFlow<DiagnosticsState> = _stateFlow

  private val _effectFlow = MutableSharedFlow<DiagnosticsEffect>(extraBufferCapacity = 1)
  val effectFlow: SharedFlow<DiagnosticsEffect> = _effectFlow

  init {
    viewModelScope.launch { runDiagnostics() }
  }

  fun postAction(action: DiagnosticsAction) {
    when (action) {
      DiagnosticsAction.Wikipedia -> _effectFlow.tryEmit(DiagnosticsEffect.LaunchUrl(WIKIPEDIA_URL))
    }
  }

  private suspend fun runDiagnostics() {
    state = state.copy(startTime = System.currentTimeMillis())
    for (i in 1..state.iterations) {
      state =
        when (val value = coin.flip().value) {
          Coin.Value.HEADS -> state.copy(heads = state.heads + 1, total = state.total + 1)
          Coin.Value.TAILS -> state.copy(tails = state.tails + 1, total = state.total + 1)
          else -> {
            throw IllegalStateException("Coin.flip() returned invalid value: $value")
          }
        }

      if (state.total % 100 == 0L || state.total == state.iterations) {
        val elapsedTime = System.currentTimeMillis() - state.startTime
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

        if (state.iterations <= SMOOTH_DELAY_THRESHOLD) {
          // this short delay smooths out the UI animation and make it looks nicer for small values
          delay(SMOOTH_DELAY)
        } else {
          // but don't delay UI updates if the user has chosen to run more than the default number of iterations
          yield()
        }
      }
    }
  }

  private fun formatRatio(numerator: Long, denominator: Long): String {
    return "[" + "%.2f".format((numerator.toDouble() / denominator.toDouble()) * 100) + "%]"
  }
}