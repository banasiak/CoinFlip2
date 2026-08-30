package com.banasiak.coinflip.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeLessOrEqualTo
import org.junit.jupiter.api.Test

/**
 * The pan and zoom arithmetic behind the crop dialog.
 *
 * The gestures themselves cannot be tested -- there is no `androidTest` source set -- so this is
 * where the part that is easy to get wrong is checked instead. `Offset`, `Size` and `IntRect` are
 * pure Kotlin, so none of it needs a device.
 */
class CoinCropTests {
  private val square = Size(1000f, 1000f)
  private val wide = Size(2000f, 1000f)
  private val tall = Size(1000f, 2000f)

  @Test
  fun `cover scale fits the shorter edge to the viewport, so the longer one overflows`() {
    coverScale(wide, viewport = 500f) shouldBeEqualTo 0.5f
    coverScale(tall, viewport = 500f) shouldBeEqualTo 0.5f
    coverScale(square, viewport = 500f) shouldBeEqualTo 0.5f
  }

  @Test
  fun `a degenerate image does not divide by zero`() {
    coverScale(Size(0f, 0f), viewport = 500f) shouldBeEqualTo 1f
    clampOffset(Offset(9f, 9f), scale = 1f, image = Size(0f, 0f)) shouldBeEqualTo Offset.Zero
    cropRect(scale = 1f, offset = Offset.Zero, image = Size(0f, 0f)) shouldBeEqualTo IntRect(0, 0, 0, 0)
  }

  @Test
  fun `at minimum zoom a square image is framed whole`() {
    cropRect(MIN_SCALE, Offset.Zero, square) shouldBeEqualTo IntRect(0, 0, 1000, 1000)
  }

  @Test
  fun `at minimum zoom a wide image gives a full-height square from its middle`() {
    cropRect(MIN_SCALE, Offset.Zero, wide) shouldBeEqualTo IntRect(500, 0, 1500, 1000)
  }

  @Test
  fun `at minimum zoom a tall image gives a full-width square from its middle`() {
    cropRect(MIN_SCALE, Offset.Zero, tall) shouldBeEqualTo IntRect(0, 500, 1000, 1500)
  }

  @Test
  fun `the covering axis is pinned at minimum zoom, so a drag along it does nothing`() {
    // a wide image overflows horizontally and covers exactly vertically
    clampOffset(Offset(0.9f, 0.9f), MIN_SCALE, wide) shouldBeEqualTo Offset(0.5f, 0f)
    clampOffset(Offset(0.9f, 0.9f), MIN_SCALE, tall) shouldBeEqualTo Offset(0f, 0.5f)
    // a square image covers on both, so it cannot be dragged at all until it is zoomed
    clampOffset(Offset(0.9f, 0.9f), MIN_SCALE, square) shouldBeEqualTo Offset.Zero
  }

  @Test
  fun `panning to the limit lands on the edge of the image and never past it`() {
    val left = clampOffset(Offset(99f, 0f), MIN_SCALE, wide)
    val right = clampOffset(Offset(-99f, 0f), MIN_SCALE, wide)

    cropRect(MIN_SCALE, left, wide) shouldBeEqualTo IntRect(0, 0, 1000, 1000)
    cropRect(MIN_SCALE, right, wide) shouldBeEqualTo IntRect(1000, 0, 2000, 1000)
  }

  @Test
  fun `zooming in halves the source pixels the viewport frames`() {
    cropRect(scale = 2f, offset = Offset.Zero, image = square) shouldBeEqualTo IntRect(250, 250, 750, 750)
    cropRect(scale = 4f, offset = Offset.Zero, image = square) shouldBeEqualTo IntRect(375, 375, 625, 625)
  }

  @Test
  fun `zooming in unlocks the axis that was pinned`() {
    clampOffset(Offset(9f, 9f), scale = 2f, image = square) shouldBeEqualTo Offset(0.5f, 0.5f)

    cropRect(scale = 2f, offset = Offset(0.5f, 0.5f), image = square) shouldBeEqualTo IntRect(0, 0, 500, 500)
  }

  @Test
  fun `a clamped framing always stays square and inside the image`() {
    val offsets = (-6..6).map { it / 4f }

    listOf(square, wide, tall).forEach { image ->
      generateSequence(MIN_SCALE) { it + 0.25f }.takeWhile { it <= MAX_SCALE }.forEach { scale ->
        offsets.forEach { x ->
          offsets.forEach { y ->
            val rect = cropRect(scale, clampOffset(Offset(x, y), scale, image), image)

            rect.width shouldBeEqualTo rect.height
            rect.width shouldBeGreaterThan 0
            rect.left shouldBeGreaterThan -1
            rect.top shouldBeGreaterThan -1
            rect.right shouldBeLessOrEqualTo image.width.toInt()
            rect.bottom shouldBeLessOrEqualTo image.height.toInt()
          }
        }
      }
    }
  }

  @Test
  fun `an unclamped offset still cannot produce a rect outside the image`() {
    // the UI always clamps, but a rect off the edge would be a crash in Canvas.drawBitmap rather
    // than a wrong picture, so the bounds are enforced here too
    val rect = cropRect(scale = 1f, offset = Offset(50f, -50f), image = wide)

    rect.left shouldBeGreaterThan -1
    rect.right shouldBeLessOrEqualTo wide.width.toInt()
    rect.bottom shouldBeLessOrEqualTo wide.height.toInt()
  }
}