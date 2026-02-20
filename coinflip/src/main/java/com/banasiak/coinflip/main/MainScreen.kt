package com.banasiak.coinflip.main

import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.ui.DurationAnimationDrawable
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen
import com.banasiak.coinflip.util.AnimationHelper

@Composable
fun MainScreen(viewModel: MainViewModel) {
  val state by viewModel.stateFlow.collectAsStateWithLifecycle()
  MainView(state, viewModel::postAction)
}

@Composable
fun MainView(state: MainState, postAction: (MainAction) -> Unit = {}) {
  AppTheme {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      bottomBar = { MainNavigationBar(postAction) }
    ) { innerPadding ->
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .clickable(interactionSource = null, indication = null) {
              postAction(MainAction.TapCoin)
            },
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        CoinContainer(state)

        val resultColor =
          when (state.result.value) {
            Coin.Value.HEADS -> MaterialTheme.colorScheme.secondary
            Coin.Value.TAILS -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
          }
        Text(
          text = state.result.customLabel ?: stringResource(state.result.value.string),
          style =
            MaterialTheme.typography.displayMedium.copy(
              color = resultColor,
              fontWeight = FontWeight.Bold,
              fontSize = 72.sp
            ),
          textAlign = TextAlign.Center,
          modifier =
            Modifier
              .fillMaxWidth()
              .alpha(if (state.resultVisible) 1f else 0f)
        )

        Text(
          text = stringResource(state.instructionsText),
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(top = Dimen.medium)
        )

        if (state.statsVisible) {
          val headsLabel = state.labels.first ?: stringResource(R.string.heads)
          val tailsLabel = state.labels.second ?: stringResource(R.string.tails)
          val headsCount = (state.stats[Coin.Value.HEADS] ?: 0).toString()
          val tailsCount = (state.stats[Coin.Value.TAILS] ?: 0).toString()
          val headsStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.secondary)
          val tailsStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.tertiary)

          Row(modifier = Modifier.fillMaxWidth().padding(top = Dimen.large)) {
            Row(
              modifier = Modifier.weight(1f),
              horizontalArrangement = Arrangement.End,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(text = headsLabel, style = headsStyle)
              Spacer(modifier = Modifier.width(Dimen.medium))
              Text(text = headsCount, style = headsStyle)
              Spacer(modifier = Modifier.width(Dimen.medium))
            }
            Row(
              modifier = Modifier.weight(1f),
              horizontalArrangement = Arrangement.Start,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Spacer(modifier = Modifier.width(Dimen.medium))
              Text(text = tailsLabel, style = tailsStyle)
              Spacer(modifier = Modifier.width(Dimen.medium))
              Text(text = tailsCount, style = tailsStyle)
            }
          }
        }

        if (state.resetVisible) {
          Button(
            onClick = { postAction(MainAction.ResetStats) },
            modifier = Modifier.padding(vertical = Dimen.medium)
          ) {
            Text(stringResource(R.string.reset_stats))
          }
        }
      }
    }
  }
}

@Composable
private fun CoinContainer(state: MainState) {
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .aspectRatio(1f),
    contentAlignment = Alignment.Center
  ) {
    if (state.coinImageType == CoinImageType.PLACEHOLDER) {
      BoxWithConstraints(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(Dimen.xlarge),
        contentAlignment = Alignment.Center
      ) {
        val fontSize = with(LocalDensity.current) { maxWidth.toSp() }
        Text(
          text = "?",
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
          fontSize = fontSize,
          lineHeight = fontSize,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxSize()
        )
      }
    } else {
      AndroidView(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(Dimen.xlarge),
        factory = { context ->
          ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = context.getString(R.string.coin_animation)
          }
        },
        update = { imageView ->
          when (state.coinImageType) {
            CoinImageType.ANIMATION -> {
              imageView.setImageDrawable(null)
              if (imageView.background != state.animation) {
                imageView.background = state.animation
                (imageView.background as? DurationAnimationDrawable)?.apply {
                  stop()
                  start()
                }
              }
            }
            CoinImageType.IMAGE -> {
              imageView.setImageDrawable(state.animation?.getLastFrame())
              imageView.background = null
            }
            CoinImageType.PLACEHOLDER -> {}
          }
        }
      )
    }
  }
}

@Composable
private fun MainNavigationBar(postAction: (MainAction) -> Unit) {
  val itemColors =
    NavigationBarItemDefaults.colors(
      indicatorColor = Color.Transparent,
      selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
      unselectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
      selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
      unselectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
  NavigationBar {
    NavigationBarItem(
      icon = { Icon(painterResource(R.drawable.diagnostics), contentDescription = null) },
      label = { Text(stringResource(R.string.diagnostics_menu_title)) },
      selected = false,
      onClick = { postAction(MainAction.TapDiagnostics) },
      colors = itemColors
    )
    NavigationBarItem(
      icon = { Icon(painterResource(R.drawable.settings), contentDescription = null) },
      label = { Text(stringResource(R.string.settings_menu_title)) },
      selected = false,
      onClick = { postAction(MainAction.TapSettings) },
      colors = itemColors
    )
    NavigationBarItem(
      icon = { Icon(painterResource(R.drawable.about), contentDescription = null) },
      label = { Text(stringResource(R.string.about_menu_title)) },
      selected = false,
      onClick = { postAction(MainAction.TapAbout) },
      colors = itemColors
    )
  }
}

@PreviewLightDark
@Composable
private fun MainViewPreview() {
  MainView(
    state =
      MainState(
        coinImageType = CoinImageType.PLACEHOLDER,
        statsVisible = true,
        stats = mapOf(Coin.Value.HEADS to 51L, Coin.Value.TAILS to 49L)
      )
  )
}

@PreviewLightDark
@Composable
private fun MainViewWithResultPreview() {
  MainView(
    state =
      MainState(
        coinImageType = CoinImageType.PLACEHOLDER,
        result =
          Coin.Result(
            Coin.Value.HEADS,
            AnimationHelper.Permutation.HEADS_HEADS,
            null
          ),
        resultVisible = true,
        statsVisible = true,
        resetVisible = true,
        stats = mapOf(Coin.Value.HEADS to 51L, Coin.Value.TAILS to 49L)
      )
  )
}