package com.banasiak.coinflip.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.coinflip.common.Coin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
  private val coin: Coin
) : ViewModel() {

  companion object {
    private const val MAX_FLIPS = 100_000L
    private const val SMOOTH_DELAY = 5L
  }

  private var state = DiagnosticsState()
  private val _stateFlow = MutableStateFlow<DiagnosticsState>(state)
  val stateFlow: StateFlow<DiagnosticsState> = _stateFlow

  init {
    viewModelScope.launch { runDiagnostics() }
  }

  private suspend fun runDiagnostics() {
    state = state.copy(startTime = System.currentTimeMillis())
    for (i in 1..MAX_FLIPS) {
      when (val value = coin.flip().second) {
        Coin.Value.HEADS -> incrementHeads()
        Coin.Value.TAILS -> incrementTails()
        else -> {
          throw IllegalStateException("Coin.flip() returned invalid value: $value")
        }
      }

      if (state.totalCount % 100 == 0L) {
        val elapsedTime = System.currentTimeMillis() - state.startTime
        state = state.copy(elapsedTime = elapsedTime)
        _stateFlow.emit(state)
        delay(SMOOTH_DELAY)
      }
    }
  }

  private fun incrementHeads() {
    val heads = state.headsCount + 1
    val total = state.totalCount + 1
    val headsRatio = calculatePercentage(heads, total)
    val totalRatio = calculatePercentage(total, MAX_FLIPS)
    state = state.copy(headsCount = heads, headsRatio = headsRatio, totalCount = total, totalRatio = totalRatio)
  }

  private fun incrementTails() {
    val tails = state.tailsCount + 1
    val total = state.totalCount + 1
    val tailsRatio = calculatePercentage(tails, total)
    val totalRatio = calculatePercentage(total, MAX_FLIPS)
    state = state.copy(tailsCount = tails, tailsRatio = tailsRatio, totalCount = total, totalRatio = totalRatio)
  }

  private fun calculatePercentage(numerator: Long, denominator: Long): String {
    return "%.2f".format((numerator.toDouble() / denominator.toDouble()) * 100) + "%"
  }
}