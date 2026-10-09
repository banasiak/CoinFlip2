package com.banasiak.coinflip.about

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen
import com.banasiak.coinflip.ui.theme.cardColor

private val APP_ICON_SIZE = 56.dp

@Composable
fun AboutScreen(viewModel: AboutViewModel) {
  val state by viewModel.stateFlow.collectAsStateWithLifecycle()
  AboutView(state, viewModel::postAction)
}

@Composable
fun AboutView(state: AboutState, postAction: (AboutAction) -> Unit = { }) {
  AppTheme(dynamicColor = state.dynamicColors) {
    Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
      Column(
        modifier =
          Modifier
            .padding(
              horizontal = Dimen.medium,
              vertical = Dimen.large
            )
            .verticalScroll(rememberScrollState())
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          AppIcon(modifier = Modifier.size(APP_ICON_SIZE))
          Spacer(modifier = Modifier.width(Dimen.medium))
          Column {
            Text(
              text = stringResource(R.string.about_fragment_title),
              style = MaterialTheme.typography.titleLarge
            )
            Text(
              text = stringResource(R.string.version, state.versionName, state.versionCode),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
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
        Card(
          modifier = Modifier.padding(top = Dimen.large).fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.cardColor)
        ) {
          LinkRow(
            icon = R.drawable.star,
            title = stringResource(R.string.rate_app),
            onClick = { postAction(AboutAction.RateApp) }
          )
          LinkRow(
            icon = R.drawable.favorite,
            title = stringResource(R.string.donate),
            supporting = stringResource(R.string.donate_text),
            onClick = { postAction(AboutAction.Donate) }
          )
          LinkRow(
            icon = R.drawable.code_blocks,
            title = stringResource(R.string.source_code),
            onClick = { postAction(AboutAction.SourceCode) }
          )
        }
      }
    }
  }
}

// the launcher icon is adaptive, which painterResource cannot draw, so it is rebuilt from its two
// layers. A launcher's mask shows only the middle 72dp of the 108dp foreground, hence the 1.5 scale
@Composable
private fun AppIcon(modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(R.mipmap.ic_launcher_foreground),
    contentDescription = null,
    modifier =
      modifier
        .clip(CircleShape)
        .background(colorResource(R.color.ic_launcher_background))
        .graphicsLayer(scaleX = 1.5f, scaleY = 1.5f)
  )
}

@Composable
private fun LinkRow(
  @DrawableRes icon: Int,
  title: String,
  onClick: () -> Unit,
  supporting: String? = null
) {
  ListItem(
    headlineContent = { Text(title) },
    modifier = Modifier.clickable(onClick = onClick),
    supportingContent = supporting?.let { { Text(it) } },
    leadingContent = { Icon(painter = painterResource(icon), contentDescription = null) },
    trailingContent = { Icon(painter = painterResource(R.drawable.open_in_new), contentDescription = null) },
    colors =
      ListItemDefaults.colors(
        containerColor = Color.Transparent,
        leadingIconColor = MaterialTheme.colorScheme.primary
      )
  )
}

@PreviewLightDark
@Composable
fun AboutViewPreview() {
  val state = AboutState(versionName = "2024", versionCode = 99)
  AboutView(state)
}