package com.banasiak.coinflip.main

import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.extensions.formatNumber
import com.banasiak.coinflip.ui.DurationAnimationDrawable
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen
import com.banasiak.coinflip.util.AnimationHelper
import kotlin.math.min

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
      // the display cutout sits alongside the content in landscape, not above it
      contentWindowInsets = WindowInsets.safeDrawing,
      bottomBar = { MainNavigationBar(postAction) }
    ) { contentPadding ->
      BoxWithConstraints(
        modifier =
          Modifier
            .padding(contentPadding)
            .fillMaxSize()
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null
            ) { postAction(MainAction.TapCoin) }
      ) {
        // the coin is emitted from two different call sites below; movableContent keeps the same
        // ImageView (and any in-flight animation) alive when the device rotates between them
        val coin =
          remember {
            movableContentOf<MainState, Int, Dp, Dp> { coinState, token, size, coinPadding ->
              CoinImage(coinState, token, size, coinPadding)
            }
          }

        val landscape = maxWidth > maxHeight
        // portrait sizes the coin off the width; landscape sizes it off the (much scarcer) height,
        // capped at half the width so the text column still gets its share of a square-ish window
        val coinSize = if (landscape) maxHeight.coerceAtMost(maxWidth / 2) else maxWidth

        if (landscape) {
          // the coin keeps its half of the window and the text column takes the other
          Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier.weight(1f),
              contentAlignment = Alignment.Center
            ) {
              coin(state, flipToken, coinSize, Dimen.large)
            }
            Column(
              modifier = Modifier.weight(1f),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              CoinDetails(state, postAction, landscape = true)
            }
          }
        } else {
          Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            coin(state, flipToken, coinSize, Dimen.xlarge)
            CoinDetails(state, postAction, landscape = false)
          }
        }
      }
    }
  }
}

@Composable
private fun CoinDetails(state: MainState, postAction: (MainAction) -> Unit, landscape: Boolean) {
  ResultText(state, landscape)
  InstructionsText(state, landscape)
  if (state.statsVisible) {
    StatsRow(state, landscape)
  }
  if (state.resetVisible) {
    Button(
      modifier = Modifier.padding(vertical = if (landscape) Dimen.small else Dimen.medium),
      onClick = { postAction(MainAction.ResetStats) }
    ) {
      Text(stringResource(R.string.reset_stats))
    }
  }
}

private const val PLACEHOLDER_GLYPH = "?"

// a multiplication sign and a numeral, so the run needs no translating; the words are left to the
// content description, which keeps a plural rule per locale out of the most visible text in the app
private const val STREAK_PREFIX = "×"

// big enough to read across a table, small enough that 'HEADS ×12' still fits a portrait phone
private const val STREAK_SIZE_RATIO = 0.55f

// scaling to exactly the available width can still spill by a pixel, because glyph advances are not
// perfectly linear in font size; this keeps a sliver in hand
private const val FIT_MARGIN = 0.98f

// applied to both halves of the result line: it reserves the line's height before the first flip,
// and keeping it a constant multiple of each font size is what lets the two centre against each other
private const val RESULT_LINE_HEIGHT_RATIO = 1.25f

@Composable
private fun CoinImage(state: MainState, flipToken: Int, size: Dp, coinPadding: Dp) {
  val imageViewRef = remember { mutableStateOf<ImageView?>(null) }

  Box(
    modifier = Modifier.size(size),
    contentAlignment = Alignment.Center
  ) {
    when (state.coinImageType) {
      CoinImageType.PLACEHOLDER -> {
        val glyphColor = MaterialTheme.colorScheme.primary
        Canvas(
          modifier =
            Modifier
              .fillMaxSize()
              // the same inset the coin gets, so the glyph fills exactly the coin's bounds
              .padding(coinPadding)
              // a Canvas has no semantics of its own; announce what a Text normally would
              .semantics { contentDescription = PLACEHOLDER_GLYPH }
        ) {
          // explicit receiver: the enclosing composable also has a `size`, in Dp
          val box = this.size
          val paint =
            Paint().apply {
              isAntiAlias = true
              color = glyphColor.toArgb()
              typeface = Typeface.DEFAULT_BOLD
              textSize = box.minDimension
            }
          val ink = Rect()
          // A font size is not a glyph size: '?' inks barely three quarters of its em box and none
          // of its width, so scale by what was measured rather than by a guessed fraction. This
          // lands the glyph at ~97% of the box; getTextBounds reports a slightly larger rect than
          // the glyph actually paints, and iterating the fit does not close that last few percent.
          paint.getTextBounds(PLACEHOLDER_GLYPH, 0, PLACEHOLDER_GLYPH.length, ink)
          paint.textSize *=
            min(
              box.width / ink.width().coerceAtLeast(1),
              box.height / ink.height().coerceAtLeast(1)
            )
          paint.getTextBounds(PLACEHOLDER_GLYPH, 0, PLACEHOLDER_GLYPH.length, ink)
          // drawText puts the origin on the baseline with the ink sitting asymmetrically around it,
          // so centring the text run leaves the glyph high; centre the measured ink box instead
          drawContext.canvas.nativeCanvas.drawText(
            PLACEHOLDER_GLYPH,
            box.width / 2f - ink.exactCenterX(),
            box.height / 2f - ink.exactCenterY(),
            paint
          )
        }
      }
      else -> {
        AndroidView(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(coinPadding),
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
private fun ResultText(state: MainState, landscape: Boolean) {
  val resultColor =
    when (state.result.value) {
      Coin.Value.HEADS -> MaterialTheme.colorScheme.secondary
      Coin.Value.TAILS -> MaterialTheme.colorScheme.tertiary
      else -> MaterialTheme.colorScheme.primary
    }
  val baseFontSize = if (landscape) 56.sp else 72.sp
  val label = state.result.customLabel ?: stringResource(state.result.value.string)
  val streakVisible = state.streakVisible && state.streakCount >= MIN_DRAWN_STREAK
  val streakText = "$STREAK_PREFIX${state.streakCount}"
  val streakDescription = stringResource(R.string.streak_content_description, state.streakCount)

  BoxWithConstraints(
    // scaling fills whatever width it is given, so a long label would otherwise run to both edges;
    // the inset is what keeps it off them
    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimen.medium)
  ) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // a custom label long enough to wrap reads worse than a smaller one that fits, and once it wraps
    // there is nowhere for the run to sit beside it. Both halves shrink by one factor so the pair fits
    // on a single line -- one factor, so the run keeps its proportion to the label and stays centred.
    // The label is measured whether or not it is currently drawn, so the line does not change height
    // as a flip is revealed.
    val scale =
      run {
        val bold = FontWeight.Bold
        val labelWidth = measurer.measure(label, TextStyle(fontSize = baseFontSize, fontWeight = bold)).size.width
        val runWidth =
          if (streakVisible) {
            measurer.measure(streakText, TextStyle(fontSize = baseFontSize * STREAK_SIZE_RATIO, fontWeight = bold)).size.width
          } else {
            0
          }
        val gap = if (streakVisible) with(density) { Dimen.small.toPx() } else 0f
        val needed = labelWidth + runWidth + gap
        val available = with(density) { maxWidth.toPx() } * FIT_MARGIN
        if (needed > available && needed > 0f) available / needed else 1f
      }

    val fontSize = baseFontSize * scale
    val streakFontSize = fontSize * STREAK_SIZE_RATIO
    // the line keeps the height it would occupy unscaled, so shrinking a long label to fit does not
    // pull the instructions and stats up with it. The two halves still center against each other
    // inside it, because each keeps its line box proportional to its own font size.
    val unscaledHeight = with(density) { (baseFontSize * RESULT_LINE_HEIGHT_RATIO).toDp() }

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = unscaledHeight)
          // alpha, not visibility, to preserve layout space
          .alpha(if (state.resultVisible || streakVisible) 1f else 0f)
          .then(
            if (streakVisible) {
              // '×7' reads as "times seven"; say what it means, and only when there is a run to say it about
              Modifier.semantics {
                contentDescription = if (state.resultVisible) "$label, $streakDescription" else streakDescription
              }
            } else {
              Modifier
            }
          ),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        modifier = Modifier.weight(1f, fill = false),
        text = if (state.resultVisible) label else "",
        color = resultColor,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        // a hidden result resolves to an empty string, which measures shorter than a word does; pinning
        // the line height keeps everything below from shifting when the first result lands
        lineHeight = fontSize * RESULT_LINE_HEIGHT_RATIO,
        textAlign = TextAlign.Center
      )
      if (streakVisible) {
        Text(
          // the gap belongs to the pair, so it goes away when the run is the only thing on the line
          modifier = if (state.resultVisible) Modifier.padding(start = Dimen.small) else Modifier,
          text = streakText,
          color = resultColor,
          fontWeight = FontWeight.Bold,
          fontSize = streakFontSize,
          lineHeight = streakFontSize * RESULT_LINE_HEIGHT_RATIO
        )
      }
    }
  }
}

@Composable
private fun InstructionsText(state: MainState, landscape: Boolean) {
  Text(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(top = if (landscape) Dimen.small else Dimen.medium),
    text = stringResource(state.instructionsText),
    style = MaterialTheme.typography.titleMedium,
    textAlign = TextAlign.Center
  )
}

@Composable
private fun StatsRow(state: MainState, landscape: Boolean) {
  val headsLabel = state.labels.first ?: stringResource(R.string.heads)
  val tailsLabel = state.labels.second ?: stringResource(R.string.tails)
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(top = if (landscape) Dimen.medium else Dimen.large)
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
        text = state.headsCount.formatNumber(),
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
        text = state.tailsCount.formatNumber(),
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

private fun previewState() =
  MainState(
    coinImageType = CoinImageType.PLACEHOLDER,
    result = Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.HEADS_HEADS),
    resultVisible = true,
    statsVisible = true,
    streakVisible = true,
    headsCount = 51,
    tailsCount = 49,
    streakCount = 7
  )

@PreviewLightDark
@Composable
fun MainViewPreview() {
  MainView(previewState())
}

@Preview(name = "landscape", widthDp = 892, heightDp = 411)
@Preview(name = "landscape dark", widthDp = 892, heightDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MainViewLandscapePreview() {
  MainView(previewState())
}