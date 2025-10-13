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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel) {
  val state by viewModel.stateFlow.collectAsStateWithLifecycle()
  DiagnosticsView(state, viewModel::postAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsView(state: DiagnosticsState, postAction: (DiagnosticsAction) -> Unit = { }) {
  AppTheme {
    ModalBottomSheet(
      dragHandle = { },
      onDismissRequest = { postAction(DiagnosticsAction.Back) },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
      Column(
        modifier = Modifier
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

        Spacer(modifier = Modifier.height(Dimen.large))

        val headsColor = MaterialTheme.colorScheme.secondary
        val tailsColor = MaterialTheme.colorScheme.tertiary

        Column(
          modifier = Modifier.padding(start = Dimen.medium),
          verticalArrangement = Arrangement.spacedBy(Dimen.xsmall)
        ) {
          // HEADS
          StatsRow(
            label = stringResource(R.string.heads),
            count = state.headsCount,
            ratio = state.headsRatio,
            color = headsColor
          )

          // TAILS
          StatsRow(
            label = stringResource(R.string.tails),
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
          modifier = Modifier
            .align(Alignment.End)
            .clickable(onClick = { postAction(DiagnosticsAction.Wikipedia) }),
          text = stringResource(id = R.string.wikipedia),
          style = MaterialTheme.typography.bodyMedium.copy(
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary
          )
        )
      }
    }
  }
}

@Composable
private fun StatsRow(
  label: String,
  count: String,
  ratio: String = "",
  color: Color = MaterialTheme.colorScheme.onSurface,
) {
  val titleMedium = MaterialTheme.typography.titleMedium
  val mediumStyle = remember(color, titleMedium) {
    titleMedium.copy(color = color)
  }
  val titleSmall = MaterialTheme.typography.titleSmall
  val smallStyle = remember(color, titleSmall) {
    titleSmall.copy(color = color)
  }
  Row(
    modifier = Modifier.fillMaxWidth(0.75f),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      modifier = Modifier.weight(1f),
      text = label,
      style = mediumStyle
    )
    Text(
      modifier = Modifier.weight(1f),
      text = count,
      style = smallStyle
    )
    Text(
      modifier = Modifier.weight(0.75f),
      text = ratio,
      style = smallStyle
    )
  }
}

@PreviewLightDark
@Composable
fun DiagnosticsViewPreview() {
  val state = DiagnosticsState(
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