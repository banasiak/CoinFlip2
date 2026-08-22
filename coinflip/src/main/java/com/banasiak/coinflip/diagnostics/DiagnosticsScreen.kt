package com.banasiak.coinflip.diagnostics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.extensions.formatNumber
import com.banasiak.coinflip.ui.TextInputDialog
import com.banasiak.coinflip.ui.digitsOnly
import com.banasiak.coinflip.ui.rememberEditableValue
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen
import com.banasiak.coinflip.ui.theme.Type

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

        // the iteration count is this screen's only input, so it belongs here rather than as a
        // remote control buried in Settings; changing it reruns the test
        Text(
          modifier =
            Modifier
              .clickable(role = Role.Button, onClick = { editingIterations = true })
              // padding inside the click area: a bare Text gets no minimum touch target the way a
              // Material component does, and the line box alone is barely 20dp
              .padding(vertical = Dimen.medium),
          text = "${state.iterations.formatNumber()} ${stringResource(R.string.diagnostics_iterations_summary)}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Dimen.medium))

        val headsColor = MaterialTheme.colorScheme.secondary
        val tailsColor = MaterialTheme.colorScheme.tertiary

        Column(
          modifier = Modifier.padding(start = Dimen.medium),
          verticalArrangement = Arrangement.spacedBy(Dimen.xsmall)
        ) {
          // HEADS
          StatsRow(
            label = state.labels.first ?: stringResource(R.string.heads),
            count = state.headsCount,
            ratio = state.headsRatio,
            color = headsColor
          )

          // TAILS
          StatsRow(
            label = state.labels.second ?: stringResource(R.string.tails),
            count = state.tailsCount,
            ratio = state.tailsRatio,
            color = tailsColor
          )

          // TOTAL
          StatsRow(
            label = stringResource(R.string.total),
            count = state.totalCount,
            ratio = state.totalRatio
          )

          Spacer(modifier = Modifier.height(Dimen.medium))

          // TIME
          StatsRow(
            label = stringResource(R.string.time),
            count = stringResource(R.string.seconds, state.formattedTime)
          )
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
private fun StatsRow(
  label: String,
  count: String,
  ratio: String = "",
  color: Color = MaterialTheme.colorScheme.onSurface
) {
  val labelStyle = Type.diagnosticsLabel.copy(color = color)
  val valueStyle = Type.diagnosticsValue.copy(color = color)
  Row(
    modifier = Modifier.fillMaxWidth(0.85f),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      modifier = Modifier.weight(1f),
      text = label,
      style = labelStyle
    )
    // when ratio is empty (e.g. TIME row), span both count and ratio columns to prevent text wrapping
    Text(
      modifier = Modifier.weight(if (ratio.isEmpty()) 1.75f else 1f),
      text = count,
      style = valueStyle,
      maxLines = 1
    )
    if (ratio.isNotEmpty()) {
      Text(
        modifier = Modifier.weight(0.75f),
        text = ratio,
        style = valueStyle,
        maxLines = 1
      )
    }
  }
}

@PreviewLightDark
@Composable
fun DiagnosticsViewPreview() {
  val state =
    DiagnosticsState(
      heads = 10,
      tails = 5,
      total = 15,
      headsCount = "10",
      headsRatio = "[66.6%]",
      tailsCount = "5",
      tailsRatio = "[33.3%]",
      totalCount = "15",
      totalRatio = "[100%]",
      formattedTime = "99"
    )
  DiagnosticsView(state)
}