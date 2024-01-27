package com.banasiak.coinflip.about

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banasiak.coinflip.R

@Composable
fun AboutScreen(viewModel: AboutViewModel) {
  val state by viewModel.stateFlow.collectAsStateWithLifecycle()
  AboutView(state, viewModel::postAction)
}

@Composable
fun AboutView(state: AboutState, postAction: (AboutAction) -> Unit = { }) {
  MaterialTheme {
    Surface {
      Text(
        text = stringResource(R.string.version, state.versionName, state.versionCode),
        Modifier.clickable { postAction(AboutAction.RateApp) }
      )
    }
  }
}

@Preview
@Composable
fun AboutViewPreview() {
  val state = AboutState(versionName = "2024", versionCode = 99)
  AboutView(state)
}