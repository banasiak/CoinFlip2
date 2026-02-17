package com.banasiak.coinflip.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen

@Composable
fun AboutScreen(viewModel: AboutViewModel) {
  val state by viewModel.stateFlow.collectAsStateWithLifecycle()
  AboutView(state, viewModel::postAction)
}

@Composable
fun AboutView(state: AboutState, postAction: (AboutAction) -> Unit = { }) {
  AppTheme {
    Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
      Column(
        modifier =
          Modifier
            .padding(
              horizontal = Dimen.large,
              vertical = Dimen.large
            )
            .verticalScroll(rememberScrollState())
      ) {
        Text(
          text = stringResource(R.string.about_fragment_title),
          style = MaterialTheme.typography.titleLarge
        )
        Text(
          modifier = Modifier.padding(top = Dimen.medium),
          text = stringResource(R.string.version, state.versionName, state.versionCode),
          style = MaterialTheme.typography.bodyLarge
        )
        Text(
          modifier = Modifier.padding(top = Dimen.large),
          text = stringResource(R.string.about_app_text),
          style = MaterialTheme.typography.bodyMedium
        )
        Text(
          modifier = Modifier.padding(top = Dimen.small),
          text = stringResource(R.string.about_mint_text),
          style = MaterialTheme.typography.bodyMedium
        )
        Text(
          modifier =
            Modifier
              .padding(top = Dimen.medium)
              .clickable(onClick = { postAction(AboutAction.Website) }),
          text = stringResource(R.string.copyright_text),
          style = MaterialTheme.typography.bodyMedium
        )
        Text(
          modifier = Modifier.padding(top = Dimen.medium),
          text = stringResource(R.string.donate_text),
          style = MaterialTheme.typography.bodyMedium
        )
        FilledTonalButton(
          modifier =
            Modifier
              .padding(top = Dimen.large)
              .align(Alignment.CenterHorizontally),
          onClick = { postAction(AboutAction.Donate) }
        ) {
          Text(stringResource(R.string.donate))
        }
        Button(
          modifier =
            Modifier
              .padding(top = Dimen.medium, bottom = Dimen.medium)
              .align(Alignment.CenterHorizontally),
          onClick = { postAction(AboutAction.RateApp) }
        ) {
          Text(stringResource(R.string.rate_app))
        }
      }
    }
  }
}

@PreviewLightDark
@Composable
fun AboutViewPreview() {
  val state = AboutState(versionName = "2024", versionCode = 99)
  AboutView(state)
}
