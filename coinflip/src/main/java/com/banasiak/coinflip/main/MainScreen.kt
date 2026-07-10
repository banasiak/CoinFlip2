package com.banasiak.coinflip.main

import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.ui.DurationAnimationDrawable
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen
import com.banasiak.coinflip.util.AnimationHelper

@Composable
fun MainScreen(
  viewModel: MainViewModel,
  onNavigate: (Int) -> Unit = { },
  onShowRate: () -> Unit = { }
) {
  val state by viewModel.stateFlow.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current
  var flipToken by remember { mutableIntStateOf(0) }

  LaunchedEffect(viewModel, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      viewModel.effectFlow.collect { effect ->
        when (effect) {
          MainEffect.FlipCoin -> flipToken++
          MainEffect.ToAbout -> onNavigate(R.id.toAbout)
          MainEffect.ToDiagnostics -> onNavigate(R.id.toDiagnostics)
          MainEffect.ToSettings -> onNavigate(R.id.toSettings)
          MainEffect.ShowRateDialog -> onShowRate()
        }
      }
    }
  }

  MainView(state, viewModel::postAction, flipToken)
}

@Composable
fun MainView(
  state: MainState,
  postAction: (MainAction) -> Unit = { },
  flipToken: Int = 0
) {
  AppTheme(dynamicColor = state.dynamicColors) {
    Scaffold(
      bottomBar = { MainNavigationBar(postAction) }
    ) { contentPadding ->
      Column(
        modifier =
          Modifier
            .padding(contentPadding)
            .fillMaxSize()
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null
            ) { postAction(MainAction.TapCoin) },
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        CoinImage(state, flipToken)
        ResultText(state)
        InstructionsText(state)
        if (state.statsVisible) {
          StatsRow(state)
        }
        if (state.resetVisible) {
          Button(
            modifier = Modifier.padding(vertical = Dimen.medium),
            onClick = { postAction(MainAction.ResetStats) }
          ) {
            Text(stringResource(R.string.reset_stats))
          }
        }
      }
    }
  }
}

@Composable
private fun CoinImage(state: MainState, flipToken: Int) {
  val imageViewRef = remember { mutableStateOf<ImageView?>(null) }

  BoxWithConstraints(
    modifier =
      Modifier
        .fillMaxWidth()
        .aspectRatio(1f),
    contentAlignment = Alignment.Center
  ) {
    when (state.coinImageType) {
      CoinImageType.PLACEHOLDER -> {
        Text(
          text = "?",
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
          fontSize = (maxWidth.value * 0.7f).sp
        )
      }
      else -> {
        AndroidView(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(Dimen.xlarge),
          factory = { context ->
            ImageView(context).apply {
              scaleType = ImageView.ScaleType.CENTER_INSIDE
              contentDescription = context.getString(R.string.coin_animation)
            }.also { imageViewRef.value = it }
          },
          update = { imageView ->
            when (state.coinImageType) {
              CoinImageType.IMAGE -> {
                imageView.setImageDrawable(state.animation?.getLastFrame())
                imageView.background = null
              }
              // CoinImageType.ANIMATION is driven imperatively by the flipToken LaunchedEffect below
              else -> { }
            }
          }
        )
      }
    }
  }

  // (re)start the frame animation only when a flip actually occurs, so unrelated
  // recompositions (e.g. the result text appearing) never restart it mid-flip
  LaunchedEffect(flipToken) {
    if (flipToken == 0) return@LaunchedEffect
    val imageView = imageViewRef.value ?: return@LaunchedEffect
    val animation = state.animation
    if (state.coinImageType == CoinImageType.ANIMATION && animation is DurationAnimationDrawable) {
      imageView.setImageDrawable(null)
      imageView.background = animation
      animation.stop()
      animation.start()
    }
  }
}

@Composable
private fun ResultText(state: MainState) {
  val resultColor =
    when (state.result.value) {
      Coin.Value.HEADS -> MaterialTheme.colorScheme.secondary
      Coin.Value.TAILS -> MaterialTheme.colorScheme.tertiary
      else -> MaterialTheme.colorScheme.primary
    }
  Text(
    modifier =
      Modifier
        .fillMaxWidth()
        .alpha(if (state.resultVisible) 1f else 0f), // alpha, not visibility, to preserve layout space
    text = state.result.customLabel ?: stringResource(state.result.value.string),
    color = resultColor,
    fontWeight = FontWeight.Bold,
    fontSize = 72.sp,
    textAlign = TextAlign.Center
  )
}

@Composable
private fun InstructionsText(state: MainState) {
  Text(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(top = Dimen.medium),
    text = stringResource(state.instructionsText),
    style = MaterialTheme.typography.titleMedium,
    textAlign = TextAlign.Center
  )
}

@Composable
private fun StatsRow(state: MainState) {
  val headsLabel = state.labels.first ?: stringResource(R.string.heads)
  val tailsLabel = state.labels.second ?: stringResource(R.string.tails)
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(top = Dimen.large)
  ) {
    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.End
    ) {
      Text(
        text = headsLabel,
        modifier = Modifier.padding(end = Dimen.medium),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.secondary
      )
      Text(
        text = state.headsCount,
        modifier = Modifier.padding(end = Dimen.medium),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.secondary
      )
    }
    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.Start
    ) {
      Text(
        text = tailsLabel,
        modifier = Modifier.padding(start = Dimen.medium, end = Dimen.medium),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.tertiary
      )
      Text(
        text = state.tailsCount,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.tertiary
      )
    }
  }
}

@Composable
private fun MainNavigationBar(postAction: (MainAction) -> Unit) {
  NavigationBar {
    NavigationBarItem(
      selected = false,
      onClick = { postAction(MainAction.TapDiagnostics) },
      icon = {
        Icon(
          painter = painterResource(R.drawable.diagnostics),
          contentDescription = stringResource(R.string.diagnostics_menu_title)
        )
      },
      label = { Text(stringResource(R.string.diagnostics_menu_title)) }
    )
    NavigationBarItem(
      selected = false,
      onClick = { postAction(MainAction.TapSettings) },
      icon = {
        Icon(
          painter = painterResource(R.drawable.settings),
          contentDescription = stringResource(R.string.settings_menu_title)
        )
      },
      label = { Text(stringResource(R.string.settings_menu_title)) }
    )
    NavigationBarItem(
      selected = false,
      onClick = { postAction(MainAction.TapAbout) },
      icon = {
        Icon(
          painter = painterResource(R.drawable.about),
          contentDescription = stringResource(R.string.about_menu_title)
        )
      },
      label = { Text(stringResource(R.string.about_menu_title)) }
    )
  }
}

@PreviewLightDark
@Composable
fun MainViewPreview() {
  val state =
    MainState(
      coinImageType = CoinImageType.PLACEHOLDER,
      result = Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.HEADS_HEADS),
      resultVisible = true,
      statsVisible = true,
      headsCount = "51",
      tailsCount = "49"
    )
  MainView(state)
}