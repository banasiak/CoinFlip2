package com.banasiak.coinflip.util

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import com.banasiak.coinflip.R
import com.banasiak.coinflip.ui.CallbackAnimationDrawable
import com.banasiak.coinflip.util.AnimationHelper.Permutation.HEADS_HEADS
import com.banasiak.coinflip.util.AnimationHelper.Permutation.HEADS_TAILS
import com.banasiak.coinflip.util.AnimationHelper.Permutation.TAILS_HEADS
import com.banasiak.coinflip.util.AnimationHelper.Permutation.TAILS_TAILS
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimationHelper @Inject constructor(
  private val resources: Resources
) {
  companion object {
    private const val FRAME_DURATION = 20 // milliseconds
  }

  private val _animations = mutableMapOf<Permutation, CallbackAnimationDrawable>()
  val animations: Map<Permutation, CallbackAnimationDrawable> = _animations

  suspend fun generateAnimations(
    @DrawableRes imageA: Int,
    @DrawableRes imageB: Int
  ) {
    val drawableA = ResourcesCompat.getDrawable(resources, imageA, null)!!
    val drawableB = ResourcesCompat.getDrawable(resources, imageB, null)!!
    val drawableEdge = ResourcesCompat.getDrawable(resources, R.drawable.edge, null)!!
    val drawableBackground = ResourcesCompat.getDrawable(resources, R.drawable.background, null)!!

    val e = drawableEdge as BitmapDrawable
    val bg = drawableBackground as BitmapDrawable

    // create the individual animation frames for the heads side
    val a4 = drawableA as BitmapDrawable
    val a3 = resizeBitmapDrawable(a4, bg, 0.75f)
    val a2 = resizeBitmapDrawable(a4, bg, 0.5f)
    val a1 = resizeBitmapDrawable(a4, bg, 0.25f)

    // create the individual animation frames for the tails side
    val b4 = drawableB as BitmapDrawable
    val b3 = resizeBitmapDrawable(b4, bg, 0.75f)
    val b2 = resizeBitmapDrawable(b4, bg, 0.5f)
    val b1 = resizeBitmapDrawable(b4, bg, 0.25f)

    for (permutation in Permutation.entries) {
      _animations[permutation] = generateAnimatedDrawable(a4, a3, a2, a1, b4, b3, b2, b1, e, permutation)
    }
  }

  private suspend fun resizeBitmapDrawable(
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
    val scaledBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, true)
    scaledBitmap.density = Bitmap.DENSITY_NONE

    // create a new canvas to combine the two images on
    val comboImageBitmap = Bitmap.createBitmap(backgroundBitmap.width, backgroundBitmap.height, Bitmap.Config.ARGB_8888)
    comboImageBitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(comboImageBitmap)

    // add the background as well as the new image to the horizontal center of the image
    canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)
    canvas.drawBitmap(scaledBitmap, (backgroundBitmap.width - scaledBitmap.width) / 2f, 0f, null)

    // convert the new combo image bitmap to a BitmapDrawable
    return BitmapDrawable(resources, comboImageBitmap)
  }

  private suspend fun generateAnimatedDrawable(
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
  ): CallbackAnimationDrawable {
    Timber.d("generateAnimatedDrawable()")

    val animation = CallbackAnimationDrawable()
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
      else -> { }
    }
    return animation
  }

  enum class Permutation {
    HEADS_HEADS,
    HEADS_TAILS,
    TAILS_HEADS,
    TAILS_TAILS,
    UNKNOWN
  }
}