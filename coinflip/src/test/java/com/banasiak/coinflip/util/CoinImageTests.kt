package com.banasiak.coinflip.util

import androidx.compose.ui.geometry.Offset
import androidx.exifinterface.media.ExifInterface
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeLessOrEqualTo
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * The sampling arithmetic, the EXIF orientation table and the glyph fit. The rest of [CoinImage]
 * draws, and `Bitmap`/`Canvas` are android.jar stubs under plain unit tests -- see the Coverage note
 * in CLAUDE.md.
 */
class CoinImageTests {
  @Test
  fun `an image already within the bound is not sampled down`() {
    CoinImage.sampleSizeFor(longestEdge = 1024, maxEdge = 2048) shouldBeEqualTo 1
    CoinImage.sampleSizeFor(longestEdge = 2048, maxEdge = 2048) shouldBeEqualTo 1
  }

  @Test
  fun `sampling halves until the longest edge fits`() {
    CoinImage.sampleSizeFor(longestEdge = 4096, maxEdge = 2048) shouldBeEqualTo 2
    CoinImage.sampleSizeFor(longestEdge = 8192, maxEdge = 2048) shouldBeEqualTo 4
    CoinImage.sampleSizeFor(longestEdge = 16384, maxEdge = 2048) shouldBeEqualTo 8
  }

  @Test
  fun `the bound is what integer division leaves, which is what inSampleSize actually does`() {
    // 4097 / 2 truncates to 2048 rather than 2048.5, so it fits and does not need halving again
    CoinImage.sampleSizeFor(longestEdge = 4097, maxEdge = 2048) shouldBeEqualTo 2
    CoinImage.sampleSizeFor(longestEdge = 4098, maxEdge = 2048) shouldBeEqualTo 4
  }

  @Test
  fun `a 50 megapixel photo comes back inside the bound`() {
    // 8160x6120 is what a current flagship hands over, and 200MB as ARGB_8888 if decoded whole
    val sample = CoinImage.sampleSizeFor(longestEdge = 8160, maxEdge = 2048)

    (8160 / sample) shouldBeLessOrEqualTo 2048
  }

  @Test
  fun `every sample size is a power of two, which is all inSampleSize honors`() {
    (1..10_000 step 37).forEach { edge ->
      val sample = CoinImage.sampleSizeFor(edge, maxEdge = 2048)
      (sample and (sample - 1)) shouldBeEqualTo 0
    }
  }

  @Test
  fun `dimensions that could not be read fall back to no sampling rather than looping`() {
    CoinImage.sampleSizeFor(longestEdge = 0, maxEdge = 2048) shouldBeEqualTo 1
    CoinImage.sampleSizeFor(longestEdge = -1, maxEdge = 2048) shouldBeEqualTo 1
    CoinImage.sampleSizeFor(longestEdge = 4096, maxEdge = 0) shouldBeEqualTo 1
  }

  // The canonical EXIF table, as exiftool states it. Each case is "mirror horizontally if the flag
  // is set, then rotate this many degrees clockwise". Checked against a real Pixel photo, whose tag
  // is TRANSVERSE -- the case that shipped broken, because reading only ExifInterface.rotationDegrees
  // drops the mirror and lands 180 degrees out.
  @Test
  fun `an upright photo is left alone`() {
    CoinImage.orientationFor(ExifInterface.ORIENTATION_NORMAL) shouldBeEqualTo CoinImage.Orientation.UPRIGHT
  }

  @Test
  fun `the three plain rotations carry no mirror`() {
    CoinImage.orientationFor(ExifInterface.ORIENTATION_ROTATE_90) shouldBeEqualTo
      CoinImage.Orientation(90f, mirrored = false)
    CoinImage.orientationFor(ExifInterface.ORIENTATION_ROTATE_180) shouldBeEqualTo
      CoinImage.Orientation(180f, mirrored = false)
    CoinImage.orientationFor(ExifInterface.ORIENTATION_ROTATE_270) shouldBeEqualTo
      CoinImage.Orientation(270f, mirrored = false)
  }

  @Test
  fun `the two axis mirrors are a horizontal flip, half a turn apart`() {
    CoinImage.orientationFor(ExifInterface.ORIENTATION_FLIP_HORIZONTAL) shouldBeEqualTo
      CoinImage.Orientation(0f, mirrored = true)
    // a vertical mirror is a horizontal one turned 180
    CoinImage.orientationFor(ExifInterface.ORIENTATION_FLIP_VERTICAL) shouldBeEqualTo
      CoinImage.Orientation(180f, mirrored = true)
  }

  @Test
  fun `the diagonal mirrors keep their mirror, which is the case that shipped broken`() {
    // "mirror horizontal and rotate 270 CW"
    CoinImage.orientationFor(ExifInterface.ORIENTATION_TRANSPOSE) shouldBeEqualTo
      CoinImage.Orientation(270f, mirrored = true)
    // "mirror horizontal and rotate 90 CW" -- what a Pixel camera writes for a portrait photo
    CoinImage.orientationFor(ExifInterface.ORIENTATION_TRANSVERSE) shouldBeEqualTo
      CoinImage.Orientation(90f, mirrored = true)
  }

  @Test
  fun `exactly half of the eight orientations are mirrored`() {
    val all = (1..8).map { CoinImage.orientationFor(it) }

    all.count { it.mirrored } shouldBeEqualTo 4
    all.distinct().size shouldBeEqualTo 8
  }

  @Test
  fun `an undefined or invented orientation is treated as upright`() {
    CoinImage.orientationFor(ExifInterface.ORIENTATION_UNDEFINED) shouldBeEqualTo CoinImage.Orientation.UPRIGHT
    CoinImage.orientationFor(0) shouldBeEqualTo CoinImage.Orientation.UPRIGHT
    CoinImage.orientationFor(99) shouldBeEqualTo CoinImage.Orientation.UPRIGHT
  }

  @Test
  fun `a glyph is scaled by its measured ink, not by the font size`() {
    // ink half the box it was measured at needs twice the fill fraction to reach that fraction
    CoinImage.glyphScale(box = 100f, inkWidth = 50f, inkHeight = 50f, fill = 0.5f) shouldBeEqualTo 1f
    CoinImage.glyphScale(box = 100f, inkWidth = 100f, inkHeight = 100f, fill = 0.5f) shouldBeEqualTo 0.5f
  }

  @Test
  fun `the axis that overflows first is the one that fits`() {
    // a wide glyph is bounded by its width and a tall one by its height, or a corner lands outside
    CoinImage.glyphScale(box = 100f, inkWidth = 200f, inkHeight = 50f, fill = 1f) shouldBeEqualTo 0.5f
    CoinImage.glyphScale(box = 100f, inkWidth = 50f, inkHeight = 200f, fill = 1f) shouldBeEqualTo 0.5f
  }

  @Test
  fun `a glyph that inks its whole box still clears the rim`() {
    // the constant is derived, not chosen: the square inscribed in the silhouette is 1/sqrt(2) of
    // the diameter, and the ring eats RIM_FRACTION off each side. Raising the fill "so it looks
    // bigger" puts a filled glyph's corners under the ring.
    val corner = CoinImage.glyphScale(box = 1f, inkWidth = 1f, inkHeight = 1f) * sqrt(2f)

    corner shouldBeLessOrEqualTo 1f - 2f * CoinImage.RIM_FRACTION
  }

  @Test
  fun `ink that could not be measured does not divide by zero`() {
    CoinImage.glyphScale(box = 100f, inkWidth = 0f, inkHeight = 0f).isFinite() shouldBeEqualTo true
    CoinImage.glyphScale(box = 100f, inkWidth = -5f, inkHeight = -5f).isFinite() shouldBeEqualTo true
    CoinImage.glyphScale(box = 0f, inkWidth = 10f, inkHeight = 10f) shouldBeEqualTo 1f
  }

  @Test
  fun `the origin centres the measured ink rather than the text run`() {
    // emoji ink sits entirely above the baseline, so both edges come back negative; centring the
    // run instead of the ink is what leaves the glyph riding high
    CoinImage.glyphOrigin(box = 100f, inkLeft = 0f, inkTop = -80f, inkRight = 80f, inkBottom = 0f) shouldBeEqualTo
      Offset(10f, 90f)
  }

  @Test
  fun `the fit is scale free, so the preview and the saved face agree`() {
    // the picker draws at its own box and the store at 390; both go through these, and a ratio is
    // the only thing that makes the two the same picture
    val small = CoinImage.glyphScale(box = 100f, inkWidth = 40f, inkHeight = 60f)
    val large = CoinImage.glyphScale(box = 390f, inkWidth = 156f, inkHeight = 234f)

    small shouldBeEqualTo large

    val origin = CoinImage.glyphOrigin(box = 100f, inkLeft = 10f, inkTop = -30f, inkRight = 50f, inkBottom = 10f)
    val scaled = CoinImage.glyphOrigin(box = 200f, inkLeft = 20f, inkTop = -60f, inkRight = 100f, inkBottom = 20f)

    scaled shouldBeEqualTo Offset(origin.x * 2f, origin.y * 2f)
  }
}