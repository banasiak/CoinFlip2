package com.banasiak.coinflip.util

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.BuildInfo
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.common.CoinType
import com.banasiak.coinflip.common.CustomCoin
import com.banasiak.coinflip.settings.Setting
import com.banasiak.coinflip.ui.DurationAnimationDrawable
import com.banasiak.coinflip.util.AnimationHelper.Permutation.HEADS_HEADS
import com.banasiak.coinflip.util.AnimationHelper.Permutation.HEADS_TAILS
import com.banasiak.coinflip.util.AnimationHelper.Permutation.TAILS_HEADS
import com.banasiak.coinflip.util.AnimationHelper.Permutation.TAILS_TAILS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimationHelper @Inject constructor(
  private val buildInfo: BuildInfo,
  private val clock: Clock,
  private val resources: Resources,
  private val customCoins: CustomCoinStore
) {
  companion object {
    private const val FRAME_DURATION = 20 // milliseconds

    // the edge sits between the two faces, so it takes an even mix of their two rim colors
    private const val EDGE_BLEND = 0.5f
  }

  // the colors the custom coin's rim is drawn in; null for a shipped coin, which has its own
  // artwork, and for a custom coin whose owner turned the rim off
  data class RimColors(@param:ColorInt val heads: Int, @param:ColorInt val tails: Int)

  // written on Dispatchers.IO and read on Main during flips, so publish as a single volatile reference swap
  @Volatile
  var animations: Map<Permutation, DurationAnimationDrawable> = emptyMap()
    private set

  @Volatile
  private var loadedKey: String? = null

  suspend fun loadAnimationsForCoin(prefix: String, rim: RimColors?) {
    withContext(Dispatchers.IO) {
      val key = cacheKey(prefix, rim)
      // skip the bitmap work when the same coin is already loaded; "random" rerolls on every load
      if (key == loadedKey && prefix != CoinType.RANDOM.prefix) return@withContext
      val startTime = clock.millis()
      // a stored prefix can name a coin this build no longer ships -- or a custom coin whose files
      // have since been removed -- so fall back rather than leave the screen nothing to draw
      val own = facesForPrefix(prefix, rim)
      val faces = own ?: facesForPrefix(Setting.COIN.default, rim)
      loadedKey = key
      if (faces == null) {
        Timber.w("No artwork for '$prefix', nor for the default coin. Leaving the coin unanimated.")
        animations = emptyMap()
        return@withContext
      }
      // the edge is tinted only when the custom coin's own artwork is what actually loaded and its
      // rim is on; a fallback to a shipped coin takes the shipped grey with it, and so does a coin
      // the user asked to leave unringed
      generateAnimations(faces.first, faces.second, edge(rim.takeIf { own != null && prefix == CustomCoin.PREFIX }))
      Timber.i("Animations generated in: ${clock.millis() - startTime} milliseconds")
    }
  }

  /**
   * What makes one coin's artwork a *different coin* rather than the same one redrawn: the prefix,
   * plus [CustomCoinStore.revision] for the custom coin. Free of the rim colors, unlike [cacheKey].
   */
  fun identity(prefix: String): String = if (prefix == CustomCoin.PREFIX) "$prefix:${customCoins.revision}" else prefix

  // the colors are what survive a light/dark switch, which recreates the activity but not this singleton
  @VisibleForTesting
  internal fun cacheKey(prefix: String, rim: RimColors?): String =
    if (prefix == CustomCoin.PREFIX) "${identity(prefix)}:${rim?.heads}:${rim?.tails}" else prefix

  private fun facesForPrefix(prefix: String, rim: RimColors?): Pair<BitmapDrawable, BitmapDrawable>? =
    if (prefix == CustomCoin.PREFIX) customFaces(rim) else shippedFaces(prefix)

  // the rim goes on the full-size face, before resizeBitmapDrawable squashes the frames: a ring
  // added afterwards would stay round while the coin turned inside it
  private fun customFaces(rim: RimColors?): Pair<BitmapDrawable, BitmapDrawable>? {
    val (heads, tails) = customCoins.faces() ?: return null
    if (rim != null) {
      CoinImage.drawRim(heads, rim.heads)
      CoinImage.drawRim(tails, rim.tails)
    }
    return Pair(heads.toDrawable(resources), tails.toDrawable(resources))
  }

  private fun shippedFaces(prefix: String): Pair<BitmapDrawable, BitmapDrawable>? {
    val (heads, tails) = getIdentifiersForPrefix(prefix)
    return Pair(bitmapDrawable(heads) ?: return null, bitmapDrawable(tails) ?: return null)
  }

  private fun edge(rim: RimColors?): BitmapDrawable {
    val edge = ResourcesCompat.getDrawable(resources, R.drawable.edge, null) as BitmapDrawable
    if (rim == null) return edge
    val color = ColorUtils.blendARGB(rim.heads, rim.tails, EDGE_BLEND)
    return CoinImage.tinted(edge.bitmap, color).toDrawable(resources)
  }

  // the coin drawables are addressed by name, so one this build no longer ships is a runtime fact
  // rather than a build error; null here is what reaches the fallback above instead of crashing
  private fun bitmapDrawable(@DrawableRes id: Int): BitmapDrawable? =
    if (id == 0) null else ResourcesCompat.getDrawable(resources, id, null) as? BitmapDrawable

  @SuppressLint("DiscouragedApi") // lol
  @VisibleForTesting
  internal fun getIdentifiersForPrefix(prefix: String): Pair<Int, Int> {
    val newPrefix = if (prefix == CoinType.RANDOM.prefix) CoinType.flippable.random().prefix else prefix
    Timber.d("coin selected: $newPrefix")
    val heads = resources.getIdentifier("${newPrefix}_heads", "drawable", buildInfo.packageName)
    val tails = resources.getIdentifier("${newPrefix}_tails", "drawable", buildInfo.packageName)
    return Pair(heads, tails)
  }

  // a4 and b4 are the full-size faces; the narrower frames are derived from them here
  private fun generateAnimations(a4: BitmapDrawable, b4: BitmapDrawable, e: BitmapDrawable) {
    // the backdrop ships with the app rather than being addressed by name, so unlike the faces it
    // cannot go missing
    val bg = ResourcesCompat.getDrawable(resources, R.drawable.background, null) as BitmapDrawable

    // create the individual animation frames for the heads side
    val a3 = resizeBitmapDrawable(a4, bg, 0.75f)
    val a2 = resizeBitmapDrawable(a4, bg, 0.5f)
    val a1 = resizeBitmapDrawable(a4, bg, 0.25f)

    // create the individual animation frames for the tails side
    val b3 = resizeBitmapDrawable(b4, bg, 0.75f)
    val b2 = resizeBitmapDrawable(b4, bg, 0.5f)
    val b1 = resizeBitmapDrawable(b4, bg, 0.25f)

    val generated = mutableMapOf<Permutation, DurationAnimationDrawable>()
    for (permutation in Permutation.entries) {
      // a permutation with no frames gets no entry at all: an empty AnimationDrawable is worse than
      // a missing one, because getLastFrame() reads index -1 on it and callers' null checks never
      // get the chance to fire
      generateAnimatedDrawable(a4, a3, a2, a1, b4, b3, b2, b1, e, permutation)?.let { generated[permutation] = it }
    }
    animations = generated
  }

  private fun resizeBitmapDrawable(
    image: BitmapDrawable,
    background: BitmapDrawable,
    widthScale: Float
  ): BitmapDrawable {
    // load the transparent background and convert to a bitmap
    val backgroundBitmap = background.bitmap

    // convert the passed in image to a bitmap and resize according to parameters
    val imageBitmap = image.bitmap
    val width = (imageBitmap.width * widthScale).toInt()
    val height = imageBitmap.height
    val scaledBitmap = imageBitmap.scale(width, height)
    scaledBitmap.density = Bitmap.DENSITY_NONE

    // create a new canvas to combine the two images on
    val comboImageBitmap = createBitmap(backgroundBitmap.width, backgroundBitmap.height)
    comboImageBitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(comboImageBitmap)

    // add the background as well as the new image to the horizontal center of the image
    canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)
    canvas.drawBitmap(scaledBitmap, (backgroundBitmap.width - scaledBitmap.width) / 2f, 0f, null)

    // convert the new combo image bitmap to a BitmapDrawable
    return comboImageBitmap.toDrawable(resources)
  }

  private fun generateAnimatedDrawable(
    a4: BitmapDrawable,
    a3: BitmapDrawable,
    a2: BitmapDrawable,
    a1: BitmapDrawable,
    b4: BitmapDrawable,
    b3: BitmapDrawable,
    b2: BitmapDrawable,
    b1: BitmapDrawable,
    edge: BitmapDrawable,
    permutation: Permutation
  ): DurationAnimationDrawable? {
    val animation = DurationAnimationDrawable()
    animation.isOneShot = true

    when (permutation) {
      HEADS_HEADS -> {
        // Begin Flip 1
        animation.addFrame(a4, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b4, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        // Begin Flip 2
        animation.addFrame(a4, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b4, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        // Begin Flip 3
        animation.addFrame(a4, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b4, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a4, FRAME_DURATION)
      }
      HEADS_TAILS -> {
        // Begin Flip 1
        animation.addFrame(a4, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b4, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        // Begin Flip 2
        animation.addFrame(a4, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b4, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        // Begin Flip 3 (half flip)
        animation.addFrame(a4, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b4, FRAME_DURATION)
      }
      TAILS_HEADS -> {
        // Begin Flip 1
        animation.addFrame(b4, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a4, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        // Begin Flip 2
        animation.addFrame(b4, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a4, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        // Begin Flip 3 (half flip)
        animation.addFrame(b4, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a4, FRAME_DURATION)
      }
      TAILS_TAILS -> {
        // Begin Flip 1
        animation.addFrame(b4, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a4, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        // Begin Flip 2
        animation.addFrame(b4, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a4, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        // Begin Flip 3
        animation.addFrame(b4, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a4, FRAME_DURATION)
        animation.addFrame(a3, FRAME_DURATION)
        animation.addFrame(a2, FRAME_DURATION)
        animation.addFrame(a1, FRAME_DURATION)
        animation.addFrame(edge, FRAME_DURATION)
        animation.addFrame(b1, FRAME_DURATION)
        animation.addFrame(b2, FRAME_DURATION)
        animation.addFrame(b3, FRAME_DURATION)
        animation.addFrame(b4, FRAME_DURATION)
      }
      // UNKNOWN, and anything added to the enum later, has no flip to draw
      else -> {
        return null
      }
    }
    return animation
  }

  enum class Permutation {
    HEADS_HEADS,
    HEADS_TAILS,
    TAILS_HEADS,
    TAILS_TAILS,
    UNKNOWN;

    companion object {
      /** The permutation whose last frame is [value]: the flip that leaves that face showing. */
      fun landingOn(value: Coin.Value): Permutation =
        when (value) {
          Coin.Value.HEADS -> HEADS_HEADS
          Coin.Value.TAILS -> TAILS_TAILS
          Coin.Value.UNKNOWN -> UNKNOWN
        }
    }
  }
}