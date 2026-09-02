package com.banasiak.coinflip.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import timber.log.Timber
import java.io.IOException

/**
 * Turning an arbitrary photo into coin artwork, and drawing the themed rim onto a face.
 *
 * `Bitmap` and `Canvas` are android.jar stubs that throw under plain unit tests, so everything here
 * but [sampleSizeFor] is reachable only by the manual pass.
 */
object CoinImage {
  /**
   * The rim's width as a fraction of the coin's diameter.
   *
   * The shipped Claude coin rings a 390px face with 20px, which is 5.1%; this rounds to an even 5%,
   * half a pixel narrower and indistinguishable beside it. A fraction rather than a pixel count so
   * it follows the coin through the density scaling in [CustomCoinStore].
   */
  const val RIM_FRACTION = 0.05f

  /**
   * The longest edge a picked image is decoded to. A current phone camera hands back 50 megapixels,
   * which is 200MB as ARGB_8888 and an `OutOfMemoryError` on most devices. Nothing beyond this
   * survives the scale down to a 390px coin; it is here to bound an untrusted decode.
   */
  private const val MAX_SOURCE_EDGE = 2048

  /**
   * How much of a face's width an emoji's measured ink fills.
   *
   * Derived rather than chosen. The largest square that fits inside the silhouette is 1/sqrt(2) of
   * its diameter, and the rim eats [RIM_FRACTION] off each side, which leaves 0.707 * 0.9 = 0.636.
   * Some emoji ink their whole box, so anything above that puts their corners under the ring.
   */
  private const val GLYPH_FILL_FRACTION = 0.62f

  // see [fillDisc]: the disc has to be opaque, whatever the caller hands over
  private const val OPAQUE = 0xFF000000.toInt()

  /**
   * Decodes [uri] with its longest edge at or under [maxEdge], honoring the EXIF orientation tag.
   *
   * `BitmapFactory` ignores that tag and phone cameras lean on it heavily, so without this a photo
   * taken in portrait lands on the coin lying on its side. Returns null rather than throwing: the
   * bytes behind a picked Uri are entirely untrusted, and a bad one should reach the user as a
   * message rather than as a crash.
   */
  fun decodeBounded(resolver: ContentResolver, uri: Uri, maxEdge: Int = MAX_SOURCE_EDGE): Bitmap? =
    try {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
      val longest = maxOf(bounds.outWidth, bounds.outHeight)
      if (longest <= 0) {
        Timber.w("Could not read the dimensions of $uri")
        null
      } else {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(longest, maxEdge) }
        resolver.openInputStream(uri)
          ?.use { BitmapFactory.decodeStream(it, null, options) }
          ?.let { decoded ->
            val upright = oriented(decoded, orientationOf(resolver, uri))
            if (upright != decoded) decoded.recycle()
            upright
          }
      }
    } catch (e: IOException) {
      Timber.w(e, "Could not read the image at $uri")
      null
    } catch (e: OutOfMemoryError) {
      // the decode is bounded above, but a decoder is still free to want more than is left
      Timber.w(e, "Ran out of memory decoding $uri")
      null
    }

  /**
   * The power of two that brings [longestEdge] to [maxEdge] or under, which is the only kind of
   * reduction `inSampleSize` honors.
   */
  @VisibleForTesting
  internal fun sampleSizeFor(longestEdge: Int, maxEdge: Int): Int {
    if (longestEdge <= 0 || maxEdge <= 0) return 1
    var sample = 1
    while (longestEdge / sample > maxEdge) sample *= 2
    return sample
  }

  /**
   * [crop], in [source]'s own pixels, scaled to a [size]-square face with everything outside the
   * inscribed circle transparent.
   *
   * The silhouette goes down first and the photo is drawn into it with `SRC_IN`, rather than the
   * mask being applied afterwards: that keeps the circle's antialiased boundary, which the shipped
   * faces have too -- their outermost pixel is alpha 169 rather than 255.
   */
  fun toCoinFace(source: Bitmap, crop: Rect, size: Int): Bitmap {
    val face = createBitmap(size, size)
    val canvas = Canvas(face)
    val radius = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    canvas.drawCircle(radius, radius, radius, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(source, crop, Rect(0, 0, size, size), paint)
    return face
  }

  /**
   * Strokes the rim onto [face] in place, just inside the silhouette so the coin's outline stays
   * exactly the one every shipped face has.
   *
   * In place because the only caller hands over a bitmap it decoded for this and nothing else; a
   * face that outlived a single call would collect a ring per reload. [face] must be mutable --
   * `Canvas` refuses an immutable bitmap, and `BitmapFactory` returns immutable ones by default.
   */
  fun drawRim(face: Bitmap, @ColorInt color: Int) {
    val width = face.width * RIM_FRACTION
    val radius = face.width / 2f
    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
      }

    // centred half a stroke in, so the ring's outer edge lands on the silhouette rather than past it
    Canvas(face).drawCircle(radius, radius, radius - width / 2f, paint)
  }

  /**
   * A copy of [source] with its color replaced by [color] and its alpha left alone.
   *
   * A copy rather than a `ColorFilter` on the drawable: drawables resolved from resources share
   * their `ConstantState`, so tinting `R.drawable.edge` in place would follow the shipped coins
   * around any process that had also drawn a custom one. `SRC_IN` loses nothing here -- the edge is
   * a single flat `#696969` whose thirty-odd distinct values differ only in alpha.
   */
  fun tinted(source: Bitmap, @ColorInt color: Int): Bitmap {
    val out = createBitmap(source.width, source.height)
    // carried over explicitly: the copy stands in for a resource-decoded bitmap, and a drawable
    // wrapping one at the wrong density would size itself differently from the frames beside it
    out.density = source.density
    val paint = Paint().apply { colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) }

    Canvas(out).drawBitmap(source, 0f, 0f, paint)
    return out
  }

  /**
   * A rotation with an optional mirror: mirror horizontally if [mirrored], *then* rotate [degrees]
   * clockwise. Every one of the eight EXIF orientations decomposes that way, and so does anything
   * the user asks for with the rotate and mirror buttons -- which is why they share this.
   */
  data class Orientation(val degrees: Float, val mirrored: Boolean) {
    companion object {
      val UPRIGHT = Orientation(0f, false)
    }
  }

  /**
   * The transform an EXIF orientation tag calls for.
   *
   * `ExifInterface.rotationDegrees` is *not* enough on its own, which is the trap this replaced. It
   * reports only the rotation half, so the four mirrored orientations came out flipped -- and for
   * `TRANSPOSE` and `TRANSVERSE` the rotation it reports belongs to a decomposition whose mirror was
   * being dropped, leaving the result a further 180 degrees out. A Pixel's camera writes
   * `TRANSVERSE` for an ordinary portrait photo, so those arrived on the coin upside down.
   */
  @VisibleForTesting
  internal fun orientationFor(exifOrientation: Int): Orientation =
    when (exifOrientation) {
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Orientation(0f, mirrored = true)
      ExifInterface.ORIENTATION_ROTATE_180 -> Orientation(180f, mirrored = false)
      // a vertical mirror is a horizontal one turned half a turn
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> Orientation(180f, mirrored = true)
      ExifInterface.ORIENTATION_TRANSPOSE -> Orientation(270f, mirrored = true)
      ExifInterface.ORIENTATION_ROTATE_90 -> Orientation(90f, mirrored = false)
      ExifInterface.ORIENTATION_TRANSVERSE -> Orientation(90f, mirrored = true)
      ExifInterface.ORIENTATION_ROTATE_270 -> Orientation(270f, mirrored = false)
      // NORMAL, UNDEFINED, and anything a file invents
      else -> Orientation.UPRIGHT
    }

  private fun orientationOf(resolver: ContentResolver, uri: Uri): Orientation =
    try {
      val tag =
        resolver.openInputStream(uri)?.use {
          ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
      orientationFor(tag)
    } catch (e: IOException) {
      Timber.w(e, "Could not read the orientation of $uri; assuming it is upright")
      Orientation.UPRIGHT
    }

  /**
   * [source] transformed as [orientation] asks.
   *
   * Deliberately does not recycle [source]: the crop screen keeps the decoded image and re-derives
   * the displayed one every time the user turns or flips it, so the original has to survive.
   */
  fun oriented(source: Bitmap, orientation: Orientation): Bitmap {
    if (orientation == Orientation.UPRIGHT) return source
    val matrix =
      Matrix().apply {
        // post- applies each step after the last, so this is the mirror-then-rotate order above
        if (orientation.mirrored) postScale(-1f, 1f)
        if (orientation.degrees != 0f) postRotate(orientation.degrees)
      }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
  }

  /**
   * The multiplier a text size needs so ink measured at that size fills [fill] of a [box]-square
   * face.
   *
   * A font size is not a glyph size, and emoji differ wildly in how much of their em box they ink,
   * so this scales by what was *measured*. Everything is a ratio of [box], which is what lets the
   * picker's preview and the stored face agree without either knowing the other's size.
   */
  @VisibleForTesting
  internal fun glyphScale(box: Float, inkWidth: Float, inkHeight: Float, fill: Float = GLYPH_FILL_FRACTION): Float {
    if (box <= 0f) return 1f
    val target = box * fill
    return minOf(target / inkWidth.coerceAtLeast(1f), target / inkHeight.coerceAtLeast(1f))
  }

  /**
   * Where `drawText` has to put its origin for measured ink to land in the middle of a [box]-square
   * face.
   *
   * The edges arrive as numbers rather than as a `Rect`: that is an android.jar stub which throws
   * under plain unit tests, and this is the arithmetic worth testing. `drawText` positions the
   * *baseline*, with the ink sitting asymmetrically around it, so centering the text run leaves the
   * glyph high -- this centers the measured ink box instead.
   */
  @VisibleForTesting
  internal fun glyphOrigin(box: Float, inkLeft: Float, inkTop: Float, inkRight: Float, inkBottom: Float): Offset =
    Offset(box / 2f - (inkLeft + inkRight) / 2f, box / 2f - (inkTop + inkBottom) / 2f)

  /**
   * Draws [emoji] fitted and centred into a [box]-square area of [canvas], in whatever colors the
   * system emoji font supplies.
   *
   * Shared by the stored face and the picker's live preview, and scale-free. That sharing is the
   * whole of what makes the preview honest enough to stand in for a crop step.
   */
  fun drawGlyph(canvas: Canvas, emoji: String, box: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = box }
    val ink = Rect()
    paint.getTextBounds(emoji, 0, emoji.length, ink)
    paint.textSize *= glyphScale(box, ink.width().toFloat(), ink.height().toFloat())
    paint.getTextBounds(emoji, 0, emoji.length, ink)
    val origin = glyphOrigin(box, ink.left.toFloat(), ink.top.toFloat(), ink.right.toFloat(), ink.bottom.toFloat())
    canvas.drawText(emoji, origin.x, origin.y, paint)
  }

  /**
   * [emoji] as a [size]-square coin face, on transparency.
   *
   * No disc: the color behind an emoji follows the Material theme, so it goes on at animation time
   * through [fillDisc] rather than being stored. Handed to [toCoinFace] rather than clipped here so
   * the silhouette comes off exactly one code path and the two sources cannot drift; at
   * [GLYPH_FILL_FRACTION] that clip does nothing, and is there for a font whose metrics lie.
   */
  fun toEmojiFace(emoji: String, size: Int): Bitmap {
    val square = createBitmap(size, size)
    drawGlyph(Canvas(square), emoji, size.toFloat())
    return toCoinFace(square, Rect(0, 0, size, size), size).also { square.recycle() }
  }

  /**
   * Fills [face]'s silhouette with [color] underneath whatever is already drawn on it, in place.
   *
   * `DST_OVER` rather than a fresh bitmap composited the other way round: it paints only where the
   * face is transparent, so the glyph is untouched and the circle's own antialiased edge becomes
   * the silhouette -- the same edge [toCoinFace] gives a photograph. The alpha is forced because
   * the animation composites frames onto a transparent backdrop, where a disc even slightly
   * translucent shows the screen through the coin.
   */
  fun fillDisc(face: Bitmap, @ColorInt color: Int) {
    val radius = face.width / 2f
    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color or OPAQUE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
      }

    Canvas(face).drawCircle(radius, radius, radius, paint)
  }
}