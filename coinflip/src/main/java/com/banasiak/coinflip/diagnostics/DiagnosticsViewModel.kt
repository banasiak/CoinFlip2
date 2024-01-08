package com.banasiak.coinflip.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.settings.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
  settings: SettingsManager,
  private val coin: Coin
) : ViewModel() {
  companion object {
    private const val SMOOTH_DELAY = 5L
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
      when (val value = coin.flip().value) {
        Coin.Value.HEADS -> incrementHeads()
        Coin.Value.TAILS -> incrementTails()
        else -> {
          throw IllegalStateException("Coin.flip() returned invalid value: $value")
        }
      }

      if (state.total % 100 == 0L || state.total == state.iterations) {
        val elapsedTime = System.currentTimeMillis() - state.startTime
        state =
          state.copy(
            headsCount = formatCount(state.heads),
            tailsCount = formatCount(state.tails),
            totalCount = formatCount(state.total),
            elapsedTime = elapsedTime,
            formattedTime = formatTime(elapsedTime)
          )
        _stateFlow.emit(state)
        delay(SMOOTH_DELAY) // this short delay smooths out the UI animation and make it looks nicer
      }
    }
  }

  private fun incrementHeads() {
    val heads = state.heads + 1
    val total = state.total + 1
    val headsRatio = formatRatio(heads, total)
    val totalRatio = formatRatio(total, state.iterations)
    state = state.copy(heads = heads, headsRatio = headsRatio, total = total, totalRatio = totalRatio)
  }

  private fun incrementTails() {
    val tails = state.tails + 1
    val total = state.total + 1
    val tailsRatio = formatRatio(tails, total)
    val totalRatio = formatRatio(total, state.iterations)
    state = state.copy(tails = tails, tailsRatio = tailsRatio, total = total, totalRatio = totalRatio)
  }

  private fun formatRatio(numerator: Long, denominator: Long): String {
    return "[" + "%.2f".format((numerator.toDouble() / denominator.toDouble()) * 100) + "%]"
  }

  private fun formatCount(count: Long): String {
    return "%,d".format(count)
  }

  private fun formatTime(elapsed: Long): String {
    return "%.3f".format(elapsed.toDouble() / 1000)
  }
}