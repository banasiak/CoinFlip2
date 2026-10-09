package com.banasiak.coinflip.diagnostics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.extensions.formatNumber
import com.banasiak.coinflip.ui.TextInputDialog
import com.banasiak.coinflip.ui.digitsOnly
import com.banasiak.coinflip.ui.rememberEditableValue
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen

// tabular figures: every digit is the same width, so the longest count and a full share are the
// widest strings a card shows, which is what its styles are fitted to, and a number ticking every
// batch holds its width
private const val TABULAR_FIGURES = "tnum"

// the value style is fitted to the width these leave inside a card
private val CARD_GAP = Dimen.small
private val CARD_PADDING = Dimen.medium

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel) {
  val state by viewModel.stateFlow.collectAsStateWithLifecycle()
  DiagnosticsView(state, viewModel::postAction)
}

@Composable
fun DiagnosticsView(state: DiagnosticsState, postAction: (DiagnosticsAction) -> Unit = { }) {
  // saveable so an open dialog survives configuration change and process death
  var editingIterations by rememberSaveable { mutableStateOf(false) }

  AppTheme(dynamicColor = state.dynamicColors) {
    Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
      Column(
        modifier =
          Modifier
            .verticalScroll(rememberScrollState())
            .padding(
              horizontal = Dimen.medium,
              vertical = Dimen.large
            )
      ) {
        Text(
          text = stringResource(R.string.diagnostics_fragment_title),
          style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(Dimen.medium))

        OutlinedButton(onClick = { editingIterations = true }) {
          Text("${state.iterations.formatNumber()} ${stringResource(R.string.diagnostics_iterations_summary)}")
        }

        Spacer(modifier = Modifier.height(Dimen.small))

        LinearProgressIndicator(
          // iterations is 0 before a run is set up, and NaN survives coerceIn
          progress = { if (state.iterations > 0) (state.total.toFloat() / state.iterations).coerceIn(0f, 1f) else 0f },
          modifier = Modifier.fillMaxWidth().height(Dimen.small),
          // the default secondaryContainer is a shade of the heads color in this palette
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
          drawStopIndicator = {}
        )

        Spacer(modifier = Modifier.height(Dimen.medium))

        val headsColor = MaterialTheme.colorScheme.secondary
        val tailsColor = MaterialTheme.colorScheme.tertiary
        val runColor = MaterialTheme.colorScheme.primary
        // STREAK -- an observation rather than a test, so it is shown without a reference value:
        // the longest run varies too widely between healthy runs for one to mean anything
        val streak = stringResource(R.string.streak)
        val headsStreak = stringResource(R.string.streak_face, streak, state.headsStreak.formatNumber())
        val tailsStreak = stringResource(R.string.streak_face, streak, state.tailsStreak.formatNumber())

        BoxWithConstraints {
          val width = (maxWidth - CARD_GAP) / 2 - CARD_PADDING * 2
          val widestCount = state.iterations.formatNumber()
          // fitted once, to the widest value a card can show: auto-sizing each value as it ticks more
          // than doubled a run's frame time
          val valueStyle =
            rememberFittedStyle(
              texts = listOf(widestCount, formatRatio(1, 1)),
              style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
              width = width
            )
          // the face cards' counts are details, and a detail may wrap: unfitted, a count breaks
          // mid-number at 320dp and a large font scale
          val detailStyle =
            rememberFittedStyle(
              texts = listOf(widestCount),
              style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
              width = width
            )

          Column(verticalArrangement = Arrangement.spacedBy(Dimen.small)) {
            StatRow {
              StatCard(
                caption = state.labels.first ?: stringResource(R.string.heads),
                value = state.headsRatio,
                valueStyle = valueStyle,
                detailStyle = detailStyle,
                details = listOf(state.headsCount, headsStreak),
                color = headsColor
              )
              StatCard(
                caption = state.labels.second ?: stringResource(R.string.tails),
                value = state.tailsRatio,
                valueStyle = valueStyle,
                detailStyle = detailStyle,
                details = listOf(state.tailsCount, tailsStreak),
                color = tailsColor
              )
            }
            StatRow {
              StatCard(
                caption = stringResource(R.string.total),
                value = state.totalCount,
                valueStyle = valueStyle,
                detailStyle = detailStyle,
                details = listOf(stringResource(R.string.seconds, state.formattedTime)),
                color = runColor
              )
              // CHANGES -- how often the result differed from the flip before it. The shares on the
              // face cards only test the balance: a generator that simply alternates reports 50/50 and
              // passes, while this reads 100%. A sticky one reads near zero.
              StatCard(
                caption = stringResource(R.string.changes),
                value = state.changesCount,
                valueStyle = valueStyle,
                detailStyle = detailStyle,
                details = listOf(state.changesRatio),
                color = runColor
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(Dimen.large))

        Text(
          modifier = Modifier.align(Alignment.CenterHorizontally),
          text = stringResource(R.string.rng),
          style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(Dimen.small))

        Text(
          modifier =
            Modifier
              .align(Alignment.End)
              .clickable(onClick = { postAction(DiagnosticsAction.Wikipedia) }),
          text = stringResource(id = R.string.wikipedia),
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontStyle = FontStyle.Italic,
              color = MaterialTheme.colorScheme.primary
            )
        )
      }
    }

    if (editingIterations) {
      IterationsDialog(
        initialValue = state.iterations,
        onConfirm = { postAction(DiagnosticsAction.SetIterations(it)) },
        onDismiss = { editingIterations = false }
      )
    }
  }
}

@Composable
private fun IterationsDialog(initialValue: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
  var value by rememberEditableValue(initialValue.toString(), selectAll = true)
  val iterations = value.text.toLongOrNull() ?: 0L

  TextInputDialog(
    title = stringResource(R.string.diagnostics_iterations_dialog),
    confirmEnabled = iterations in 1L..MAX_ITERATIONS,
    onConfirm = { onConfirm(iterations) },
    onDismiss = onDismiss
  ) { focusRequester ->
    OutlinedTextField(
      value = value,
      modifier = Modifier.focusRequester(focusRequester),
      onValueChange = { value = digitsOnly(it) },
      singleLine = true,
      // states the bound, so a value the decoder cannot take reads as out of range rather than
      // as an OK button that mysteriously refuses to light up
      supportingText = { Text(stringResource(R.string.diagnostics_iterations_range, MAX_ITERATIONS.formatNumber())) },
      isError = value.text.isNotEmpty() && iterations !in 1L..MAX_ITERATIONS,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
  }
}

@Composable
private fun StatRow(content: @Composable RowScope.() -> Unit) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
    content = content
  )
}

@Composable
private fun RowScope.StatCard(
  caption: String,
  value: String,
  valueStyle: TextStyle,
  detailStyle: TextStyle,
  details: List<String>,
  color: Color
) {
  Card(
    modifier = Modifier.weight(1f),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Column(modifier = Modifier.padding(CARD_PADDING)) {
      Text(
        text = caption,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = value,
        style = valueStyle,
        color = color,
        maxLines = 1,
        softWrap = false
      )
      details.forEach {
        Text(
          text = it,
          style = detailStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

/**
 * Returns [style], shrunk until every one of [texts] fits [width] on one line. Never larger than
 * [style] itself.
 */
@Composable
private fun rememberFittedStyle(texts: List<String>, style: TextStyle, width: Dp): TextStyle {
  val measurer = rememberTextMeasurer()
  val limit = with(LocalDensity.current) { width.roundToPx() }
  return remember(measurer, texts, style, limit) {
    fun widest(candidate: TextStyle) = texts.maxOf { measurer.measure(it, candidate, softWrap = false, maxLines = 1).size.width }
    var fitted = style
    var measured = widest(fitted)
    // text width scales with font size, so this converges in a step or two; the 0.99 guarantees
    // progress where rounding would otherwise leave it a pixel over forever
    while (measured > limit && limit > 0) {
      fitted = fitted.copy(fontSize = fitted.fontSize * (limit.toFloat() / measured) * 0.99f)
      measured = widest(fitted)
    }
    fitted
  }
}

// worst case for width: every count at its widest, and custom labels longer than a card
private fun previewState() =
  DiagnosticsState(
    heads = 4_999_812,
    tails = 5_000_188,
    total = 10_000_000,
    changes = 5_000_421,
    changesCount = "5,000,421",
    changesRatio = "50.00%",
    headsStreak = 23,
    tailsStreak = 21,
    headsCount = "4,999,812",
    headsRatio = "49.99%",
    tailsCount = "5,000,188",
    tailsRatio = "50.00%",
    totalCount = "10,000,000",
    formattedTime = "412.402",
    iterations = 10_000_000,
    labels = Pair("SUPERCALIFRAGILISTIC HEADS", "SUPERCALIFRAGILISTIC TAILS")
  )

@PreviewLightDark
@Composable
fun DiagnosticsViewPreview() {
  DiagnosticsView(previewState())
}

@Preview(name = "320dp", widthDp = 320)
@Preview(name = "360dp", widthDp = 360)
@Preview(name = "411dp", widthDp = 411)
@Preview(name = "640dp", widthDp = 640)
@Composable
fun DiagnosticsViewWidthPreview() {
  DiagnosticsView(previewState())
}