package com.banasiak.coinflip.settings

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.CustomCoin
import com.banasiak.coinflip.ui.theme.Dimen
import com.banasiak.coinflip.ui.theme.faceColor
import com.banasiak.coinflip.util.CoinImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt

// scale 1 is the image at cover size, so it can never be pulled back off the viewport
@VisibleForTesting
internal const val MIN_SCALE = 1f

// past this a phone photo is drawing single source pixels across the whole coin
@VisibleForTesting
internal const val MAX_SCALE = 5f

// how much of the shorter screen edge the circle takes, leaving room for the bars
private const val VIEWPORT_FRACTION = 0.8f

private const val SCRIM_ALPHA = 0.7f

private const val QUARTER_TURN = 90f
private const val FULL_TURN = 360f

/**
 * Frames a picked image inside the coin's silhouette.
 *
 * The pan is carried as a fraction of the viewport's diameter rather than in pixels. The viewport
 * then cancels out of the arithmetic entirely -- so none of it needs measuring, and a framing
 * survives a rotation that changes how big the circle is on screen.
 */
@Composable
fun CoinCropDialog(
  uri: Uri,
  face: CustomCoin.Face,
  rimEnabled: Boolean,
  onConfirm: (IntRect, CoinImage.Orientation) -> Unit,
  onFailed: () -> Unit,
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
  ) {
    CoinCropContent(uri, face, rimEnabled, onConfirm, onFailed, onDismiss)
  }
}

private sealed interface CropSource {
  data object Loading : CropSource

  data object Failed : CropSource

  data class Ready(val bitmap: Bitmap) : CropSource
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoinCropContent(
  uri: Uri,
  face: CustomCoin.Face,
  rimEnabled: Boolean,
  onConfirm: (IntRect, CoinImage.Orientation) -> Unit,
  onFailed: () -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val source by produceState<CropSource>(CropSource.Loading, uri) {
    value =
      withContext(Dispatchers.IO) {
        CoinImage.decodeBounded(context.contentResolver, uri)
          ?.let { CropSource.Ready(it) }
          ?: CropSource.Failed
      }
  }

  // three floats rather than a Scale/Offset pair: Offset has no built-in Saver, and losing the
  // framing to a rotation halfway through is exactly what rememberSaveable is here to prevent
  var scale by rememberSaveable { mutableFloatStateOf(MIN_SCALE) }
  var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
  var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
  var rotation by rememberSaveable { mutableFloatStateOf(0f) }
  var mirrored by rememberSaveable { mutableStateOf(false) }
  val adjustment = CoinImage.Orientation(rotation, mirrored)

  // a turn allocates a second full-size copy, so it can run out of memory where the bounded decode
  // did not
  var adjustFailed by remember { mutableStateOf(false) }

  // the turn and the flip are applied to the bitmap rather than to the drawing, so every bit of the
  // arithmetic below goes on working against a plain upright image and needs no cases for either
  val adjusted by produceState<ImageBitmap?>(null, source, adjustment) {
    val ready = source as? CropSource.Ready
    value =
      if (ready == null) {
        null
      } else {
        withContext(Dispatchers.IO) {
          try {
            CoinImage.oriented(ready.bitmap, adjustment).asImageBitmap()
          } catch (e: OutOfMemoryError) {
            Timber.w(e, "Ran out of memory turning the image")
            null
          }
        }
      }
    if (ready != null && value == null) adjustFailed = true
  }

  // released on dispose rather than when replaced: until then it is still what the last frame drew.
  // An unturned image is the original itself, and that one belongs to [CropSource].
  DisposableEffect(adjusted) {
    val copy = adjusted?.asAndroidBitmap()
    val original = (source as? CropSource.Ready)?.bitmap
    onDispose {
      if (copy != null && copy !== original && !copy.isRecycled) copy.recycle()
    }
  }

  // a quarter turn swaps the image's sides, so a pan that was legal before may not be now
  LaunchedEffect(adjusted) {
    adjusted?.let {
      val clamped = clampOffset(Offset(offsetX, offsetY), scale, Size(it.width.toFloat(), it.height.toFloat()))
      offsetX = clamped.x
      offsetY = clamped.y
    }
  }

  Surface(modifier = Modifier.fillMaxSize()) {
    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = {
        TopAppBar(
          title = { Text(stringResource(R.string.coin_crop_title)) },
          navigationIcon = {
            IconButton(onClick = onDismiss) {
              Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(android.R.string.cancel)
              )
            }
          }
        )
      }
    ) { contentPadding ->
      Column(
        modifier =
          Modifier
            .padding(contentPadding)
            .fillMaxSize()
      ) {
        Box(
          modifier =
            Modifier
              .weight(1f)
              .fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          // an adjustment that ran out of memory reports through the branch that already reports one
          when (if (adjustFailed) CropSource.Failed else source) {
            CropSource.Loading -> {
              CircularProgressIndicator()
            }
            CropSource.Failed -> {
              Text(
                text = stringResource(R.string.coin_crop_failed),
                modifier = Modifier.padding(Dimen.large),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              // a side effect, so it belongs outside the composition rather than in it
              LaunchedEffect(Unit) { onFailed() }
            }
            is CropSource.Ready -> {
              // null only for the moment between the adjustment changing and the new image arriving
              val image = adjusted
              if (image == null) {
                CircularProgressIndicator()
              } else {
                CropViewport(
                  image = image,
                  face = face,
                  rimEnabled = rimEnabled,
                  scale = scale,
                  offset = Offset(offsetX, offsetY),
                  onTransform = { newScale, newOffset ->
                    scale = newScale
                    offsetX = newOffset.x
                    offsetY = newOffset.y
                  }
                )
              }
            }
          }
        }

        CropActions(
          enabled = adjusted != null,
          onReset = {
            scale = MIN_SCALE
            offsetX = 0f
            offsetY = 0f
            rotation = 0f
            mirrored = false
          },
          onRotate = { rotation = (rotation + QUARTER_TURN) % FULL_TURN },
          // the turn is negated alongside the toggle because the adjustment is mirror-then-rotate:
          // without it, mirroring at 90 or 270 degrees lands as a vertical flip
          onMirror = {
            mirrored = !mirrored
            rotation = (FULL_TURN - rotation) % FULL_TURN
          },
          onConfirm = {
            val image = adjusted ?: return@CropActions
            val size = Size(image.width.toFloat(), image.height.toFloat())
            onConfirm(cropRect(scale, Offset(offsetX, offsetY), size), adjustment)
            onDismiss()
          }
        )
      }
    }
  }
}

@Composable
private fun CropViewport(
  image: ImageBitmap,
  face: CustomCoin.Face,
  rimEnabled: Boolean,
  scale: Float,
  offset: Offset,
  onTransform: (Float, Offset) -> Unit
) {
  val imageSize = Size(image.width.toFloat(), image.height.toFloat())
  // the rim the saved face will carry, so the circle shows what is kept and not just what is framed
  val rimColor = face.faceColor()
  val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)

  // pointerInput restarts only when its keys change, so the gesture lambda would otherwise go on
  // reading the scale and offset it captured on the first composition
  val currentScale by rememberUpdatedState(scale)
  val currentOffset by rememberUpdatedState(offset)

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val viewport = with(LocalDensity.current) { minOf(maxWidth, maxHeight).toPx() } * VIEWPORT_FRACTION

    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .pointerInput(imageSize, viewport) {
            detectTransformGestures { centroid, pan, zoom, _ ->
              val next = (currentScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
              // what the coercion allowed, so a pinch past the limit stops dragging the image with it
              val applied = next / currentScale
              val focus = (centroid - Offset(size.width / 2f, size.height / 2f)) / viewport
              val zoomed = (currentOffset - focus) * applied + focus + pan / viewport
              onTransform(next, clampOffset(zoomed, next, imageSize))
            }
          }
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val effective = coverScale(imageSize, viewport) * scale
        val width = imageSize.width * effective
        val height = imageSize.height * effective
        drawImage(
          image = image,
          dstOffset =
            IntOffset(
              (center.x + offset.x * viewport - width / 2f).roundToInt(),
              (center.y + offset.y * viewport - height / 2f).roundToInt()
            ),
          dstSize = IntSize(width.roundToInt(), height.roundToInt()),
          filterQuality = FilterQuality.Medium
        )
      }

      // offscreen so the punched-out circle composites against the scrim rather than the window
      Canvas(
        modifier =
          Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
      ) {
        val radius = viewport / 2f
        val rimWidth = viewport * CoinImage.RIM_FRACTION
        drawRect(color = scrimColor)
        drawCircle(color = Color.Black, radius = radius, center = center, blendMode = BlendMode.Clear)
        // the punched hole already shows where the edge falls, so with no rim to come there is
        // nothing more to draw -- and drawing one anyway would promise a ring the coin will not have
        if (rimEnabled) {
          drawCircle(
            color = rimColor,
            radius = radius - rimWidth / 2f,
            center = center,
            style = Stroke(width = rimWidth)
          )
        }
      }
    }
  }
}

@Composable
private fun CropActions(
  enabled: Boolean,
  onReset: () -> Unit,
  onRotate: () -> Unit,
  onMirror: () -> Unit,
  onConfirm: () -> Unit
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = Dimen.medium, vertical = Dimen.small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    // pinch and drag are invisible to a screen reader, so the framing has to be usable without
    // them: confirming untouched keeps the centred fit, this puts that back after a stray drag,
    // and the two buttons beside it are the whole of what a twist gesture would have offered
    TextButton(enabled = enabled, onClick = onReset) {
      Text(stringResource(R.string.coin_crop_reset))
    }
    Row {
      IconButton(enabled = enabled, onClick = onRotate) {
        Icon(
          painter = painterResource(R.drawable.rotate),
          contentDescription = stringResource(R.string.coin_crop_rotate)
        )
      }
      IconButton(enabled = enabled, onClick = onMirror) {
        Icon(
          painter = painterResource(R.drawable.mirror),
          contentDescription = stringResource(R.string.coin_crop_mirror)
        )
      }
    }
    TextButton(enabled = enabled, onClick = onConfirm) {
      Text(stringResource(android.R.string.ok))
    }
  }
}

/**
 * The scale at which [image] just covers a [viewport]-square hole.
 *
 * [MIN_SCALE] means exactly this, which is what makes "the image always covers the circle" true by
 * construction rather than by correcting for it afterwards. Only the drawing needs this; the
 * arithmetic below is expressed in viewport fractions and so does not.
 */
@VisibleForTesting
internal fun coverScale(image: Size, viewport: Float): Float =
  if (image.width <= 0f || image.height <= 0f) {
    1f
  } else {
    maxOf(viewport / image.width, viewport / image.height)
  }

/**
 * [offset], in viewport diameters, pulled back to the range that keeps the image covering the
 * viewport on both axes.
 *
 * At [MIN_SCALE] the covering axis has no slack at all and pins to zero, which is why dragging a
 * portrait photo sideways does nothing until it has been zoomed in.
 */
@VisibleForTesting
internal fun clampOffset(offset: Offset, scale: Float, image: Size): Offset {
  val shortest = minOf(image.width, image.height)
  if (shortest <= 0f) return Offset.Zero
  val maxX = ((image.width * scale / shortest) - 1f).coerceAtLeast(0f) / 2f
  val maxY = ((image.height * scale / shortest) - 1f).coerceAtLeast(0f) / 2f
  return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/**
 * The square of [image]'s own pixels the viewport currently frames, ready for `CoinImage.toCoinFace`.
 *
 * Kept square through the rounding rather than rounding each edge independently: the result is
 * scaled into a square face, so a rect a pixel off square would stretch the photo by that much.
 */
@VisibleForTesting
internal fun cropRect(scale: Float, offset: Offset, image: Size): IntRect {
  val shortest = minOf(image.width, image.height)
  if (shortest <= 0f) return IntRect(0, 0, 0, 0)
  val side = (shortest / scale).roundToInt().coerceIn(1, shortest.toInt())
  val centerX = image.width / 2f - offset.x * shortest / scale
  val centerY = image.height / 2f - offset.y * shortest / scale
  val left = (centerX - side / 2f).roundToInt().coerceIn(0, (image.width.toInt() - side).coerceAtLeast(0))
  val top = (centerY - side / 2f).roundToInt().coerceIn(0, (image.height.toInt() - side).coerceAtLeast(0))
  return IntRect(left, top, left + side, top + side)
}