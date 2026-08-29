package com.banasiak.coinflip.diagnostics

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.coinflip.BuildConfig
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.extensions.formatMilliseconds
import com.banasiak.coinflip.extensions.formatNumber
import com.banasiak.coinflip.extensions.restore
import com.banasiak.coinflip.extensions.save
import com.banasiak.coinflip.settings.Setting
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.util.SoundHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
  private val dispatcher: CoroutineDispatcher,
  private val savedState: SavedStateHandle
) : ViewModel(), LifecycleEventObserver {
  companion object {
    private const val BATCH_SIZE = 100L
    private const val SMOOTH_DELAY = 5L
    private const val TURBO_MODE_THRESHOLD = 1_000_000L
    private const val WIKIPEDIA_URL = "https://w.wiki/3kSY"
  }

  // the flip loop publishes this from `dispatcher` while onPause() reads it on the main thread
  @Volatile
  private var state =
    savedState.restore()
      ?: DiagnosticsState(
        iterations = settings.diagnosticsIterations,
        turboMode = settings.diagnosticsIterations >= TURBO_MODE_THRESHOLD,
        dynamicColors = settings.dynamicColorsEnabled
      )
  private val _stateFlow = MutableStateFlow(state)
  val stateFlow = _stateFlow.asStateFlow()

  private val _effectFlow = MutableSharedFlow<DiagnosticsEffect>(extraBufferCapacity = 1)
  val effectFlow = _effectFlow.asSharedFlow()

  private var diagnosticsJob: Job? = null

  fun postAction(action: DiagnosticsAction) {
    when (action) {
      DiagnosticsAction.Back -> {
        _effectFlow.tryEmit(DiagnosticsEffect.NavBack)
      }
      is DiagnosticsAction.SetIterations -> {
        onSetIterations(action.value)
      }
      DiagnosticsAction.Start -> {
        if (diagnosticsJob?.isActive != true) {
          diagnosticsJob =
            viewModelScope.launch {
              // sequenced before the loop so the loop is the only writer of `state` while it runs
              showTurboModeNotice()
              runDiagnostics()
            }
        }
      }

      DiagnosticsAction.Wikipedia -> {
        _effectFlow.tryEmit(DiagnosticsEffect.LaunchUrl(WIKIPEDIA_URL))
      }
    }
  }

  /** Persists a new iteration count and restarts the test with it. */
  private fun onSetIterations(value: Long) {
    if (value !in 1L..MAX_ITERATIONS) return
    settings.update(Setting.DIAGNOSTICS, value)

    val running = diagnosticsJob
    diagnosticsJob =
      viewModelScope.launch {
        // the loop publishes state from `dispatcher`, so let it stop before resetting -- otherwise
        // its final batch lands on top of the run we are about to start
        running?.cancelAndJoin()
        state =
          DiagnosticsState(
            iterations = value,
            turboMode = value >= TURBO_MODE_THRESHOLD,
            dynamicColors = state.dynamicColors
          )
        _stateFlow.emit(state)
        showTurboModeNotice()
        runDiagnostics()
      }
  }

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    Timber.d("Lifecycle onStateChanged(): $event")
    when (event) {
      Lifecycle.Event.ON_START -> {
        postAction(DiagnosticsAction.Start)
      }
      Lifecycle.Event.ON_PAUSE -> {
        onPause()
      }
      else -> { }
    }
  }

  private fun onPause() {
    savedState.save(state)
  }

  private suspend fun runDiagnostics() {
    // load custom labels
    state = state.copy(labels = Pair(settings.customHeadsText, settings.customTailsText))

    // the state may have been restored, only resume the loop if it hasn't finished running, otherwise just update the UI
    if (state.finished) {
      _stateFlow.emit(state)
      return
    }

    // don't update the start time if state has been restored, then you can see how long the loop was "paused" for in wall-clock time
    if (state.startTime == 0L) state = state.copy(startTime = clock.millis())

    // run the flips off the main thread, accumulating in locals; state is published once per batch
    // so the UI animates and onPause() can still persist (batch-granular) partial progress
    withContext(dispatcher) {
      // resume the loop where we left off
      var heads = state.heads
      var tails = state.tails
      var total = state.total
      // primitive locals, not Stats.afterFlip() -- that allocates two maps per call, and this loop
      // runs up to ten million times with the resulting benchmark shown on the same screen
      var changes = state.changes
      var runValue = state.runValue
      var run = state.currentRun
      var headsStreak = state.headsStreak
      var tailsStreak = state.tailsStreak

      while (total < state.iterations) {
        val value = coin.flip().value
        when (value) {
          Coin.Value.HEADS -> heads++
          Coin.Value.TAILS -> tails++
          else -> throw IllegalStateException("Coin.flip() returned invalid value: $value")
        }
        total++

        if (value == runValue) {
          run++
        } else {
          // runValue is UNKNOWN only on the first flip of a test, which follows nothing and so
          // changes nothing; on a resume it holds the face the run carried across the pause
          if (runValue != Coin.Value.UNKNOWN) changes++
          runValue = value
          run = 1
        }
        if (value == Coin.Value.HEADS) {
          if (run > headsStreak) headsStreak = run
        } else {
          if (run > tailsStreak) tailsStreak = run
        }

        if (total % BATCH_SIZE == 0L || total == state.iterations) {
          val elapsedTime = clock.millis() - state.startTime
          state =
            state.copy(
              heads = heads,
              tails = tails,
              total = total,
              changes = changes,
              changesCount = changes.formatNumber(),
              changesRatio = formatRatio(changes, state.iterations),
              runValue = runValue,
              currentRun = run,
              headsStreak = headsStreak,
              tailsStreak = tailsStreak,
              headsCount = heads.formatNumber(),
              tailsCount = tails.formatNumber(),
              totalCount = total.formatNumber(),
              headsRatio = formatRatio(heads, state.iterations),
              tailsRatio = formatRatio(tails, state.iterations),
              totalRatio = formatRatio(total, state.iterations),
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
    }
    printBenchmark()
    state = state.copy(finished = true)
  }

  private suspend fun showTurboModeNotice() {
    if (state.turboMode && !state.turboModeShown) {
      Timber.i("turbo mode activated!")
      soundHelper.playSound(SoundHelper.Sound.POWERUP)
      _effectFlow.emit(DiagnosticsEffect.ShowToast(R.string.turbo_mode))
      state = state.copy(turboModeShown = true) // otherwise this starts to get annoying...
    }
  }

  private fun printBenchmark() {
    if (!BuildConfig.DEBUG) return
    try {
      Timber.i("Diagnostic test complete.")
      Timber.i("SecureRandom: ${coin.isSecure()} | Turbo Mode: ${state.turboMode}")
      Timber.i("Iterations: ${state.iterations} | Time: ${state.formattedTime} seconds")
      Timber.i("HEADS: ${state.headsRatio} | TAILS: ${state.tailsRatio}")
      Timber.i("Benchmark: ${state.iterations / (state.elapsedTime / 1000)} iterations/second")
    } catch (e: Exception) {
      // really don't care about divide by zero exceptions (or anything else that might go wrong with this)
    }
  }

  private fun formatRatio(numerator: Long, denominator: Long): String =
    "[" +
      "%.2f".format(
        (numerator.toDouble() / denominator.toDouble()) * 100
      ) + "%]"
}