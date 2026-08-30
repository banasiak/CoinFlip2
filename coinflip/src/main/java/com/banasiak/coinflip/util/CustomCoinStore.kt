package com.banasiak.coinflip.util

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.DisplayMetrics
import androidx.compose.ui.unit.IntRect
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.CustomCoin
import com.banasiak.coinflip.common.CustomCoin.Face
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two images behind [CustomCoin], under `filesDir/coins`.
 *
 * Only ever holds files the app wrote itself. A picked `Uri` is read once and copied here, so
 * nothing downstream depends on a read grant that expires with the activity, and no filename is
 * ever derived from anything the user supplied.
 */
@Singleton
class CustomCoinStore @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val resources: Resources
) {
  private val directory: File get() = File(context.filesDir, CustomCoin.DIRECTORY)

  // outlives every screen, so a delete can still be settled once the screen that asked is gone
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  fun exists(face: Face): Boolean = file(face).isFile

  // which faces are set. The UI reflects this without decoding anything.
  val storedFaces: Set<Face> get() = Face.entries.filterTo(mutableSetOf()) { exists(it) }

  // the custom coin is only offered once both faces are set; one face on its own is not a coin
  val isComplete: Boolean get() = Face.entries.all { exists(it) }

  /**
   * Moves with every write, so `AnimationHelper`'s cache key changes when a face is replaced. The
   * prefix stays `"custom"` across a re-upload, so without this the old artwork would stay on screen.
   */
  val revision: Long get() = Face.entries.maxOf { file(it).lastModified() }

  /**
   * Both faces, freshly decoded and **mutable**, or null unless both are present.
   *
   * Fresh on every call by design: the caller strokes the rim onto them in place, and a shared
   * bitmap would collect a ring per reload. Mutable for the same reason -- see [decode].
   */
  fun faces(): Pair<Bitmap, Bitmap>? {
    val heads = decode(Face.HEADS) ?: return null
    val tails = decode(Face.TAILS) ?: return null
    return Pair(heads, tails)
  }

  /**
   * Reads [uri] once, normalizes it to a coin face using [crop], and writes it.
   *
   * [crop] arrives as a Compose `IntRect` rather than an `android.graphics.Rect` so the caller --
   * a ViewModel with tests -- never has to construct one of the latter, which is an android.jar
   * stub that throws off a device. [adjustment] is whatever the user did with the rotate and mirror
   * buttons, and has to be replayed here because the rect is in that image's coordinates.
   */
  suspend fun save(uri: Uri, face: Face, crop: IntRect, adjustment: CoinImage.Orientation): Boolean =
    withContext(Dispatchers.IO) {
      val decoded = CoinImage.decodeBounded(context.contentResolver, uri) ?: return@withContext false
      // the crop was framed against the turned and flipped image, so this has to arrive at the same
      // one before the rect means anything. EXIF first, inside decodeBounded, then the user on top.
      val source = CoinImage.oriented(decoded, adjustment)
      if (source != decoded) decoded.recycle()
      val bitmap = CoinImage.toCoinFace(source, Rect(crop.left, crop.top, crop.right, crop.bottom), coinSize())
      source.recycle()
      try {
        directory.mkdirs()
        file(face).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        true
      } catch (e: IOException) {
        Timber.w(e, "Could not write the $face face")
        false
      } finally {
        bitmap.recycle()
      }
    }

  /**
   * Unlinks the whole directory's contents, not just the two names it knows about, so nothing of
   * the coin is left on disk. Returns whether there was anything to delete.
   */
  suspend fun deleteAll(): Boolean =
    withContext(Dispatchers.IO) {
      val files = directory.listFiles().orEmpty()
      files.forEach { it.delete() }
      files.isNotEmpty()
    }

  /**
   * [deleteAll] with nobody left to wait on it: `ViewModel.onCleared` runs after `viewModelScope`
   * has been cancelled and cannot suspend.
   */
  fun deleteAllDetached() {
    scope.launch { deleteAll() }
  }

  /**
   * A face decoded down to roughly [targetPx] for a thumbnail. Deliberately not the density-matched
   * [decode]: this is drawn at a fixed dp size rather than composited into the animation frames.
   */
  fun thumbnail(face: Face, targetPx: Int): Bitmap? {
    val file = file(face)
    if (!file.isFile || targetPx <= 0) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val options = BitmapFactory.Options().apply { inSampleSize = CoinImage.sampleSizeFor(bounds.outWidth, targetPx) }
    return BitmapFactory.decodeFile(file.path, options)
  }

  private fun file(face: Face): File = File(directory, face.fileName)

  /**
   * The coin's size in mdpi pixels, read off the backdrop the frames are composited against so it
   * cannot drift from the shipped artwork. `inScaled` is off deliberately: this wants the asset's
   * own size, not what the current display would stretch it to.
   */
  private fun coinSize(): Int {
    val options =
      BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        inScaled = false
      }
    BitmapFactory.decodeResource(resources, R.drawable.background, options)
    return options.outWidth
  }

  /**
   * Decodes a stored face into exactly what `ResourcesCompat.getDrawable` would hand back for a
   * shipped one: the stored pixels at their own size, tagged mdpi.
   *
   * The tempting mistake is to density-scale here. `res/drawable` carries no density qualifier, so
   * the shipped art is the mdpi baseline and renders at 1024px on a 420dpi screen -- but the
   * *bitmap* stays 390px and it is the drawable that scales at draw time. `resizeBitmapDrawable`
   * composites raw pixels against the 390px backdrop, so a face pre-scaled to 1024 overflows that
   * canvas and every squashed frame is clipped. Only the full-size frame survives it, which is what
   * makes the mistake so quiet: the coin looks right until it is mid-flip.
   *
   * Tagging the bitmap mdpi is what makes the drawable report the same intrinsic size a shipped
   * face does, so the two are interchangeable to everything downstream.
   */
  private fun decode(face: Face): Bitmap? {
    val file = file(face)
    if (!file.isFile) return null
    val options =
      BitmapFactory.Options().apply {
        inScaled = false
        // decodeFile hands back an immutable bitmap, and Canvas refuses to wrap one. The caller
        // strokes the rim straight onto this face, so it has to arrive drawable.
        inMutable = true
      }
    return BitmapFactory.decodeFile(file.path, options)?.apply { density = DisplayMetrics.DENSITY_MEDIUM }
  }

  companion object {
    // ignored for PNG, which is lossless, but the parameter is not optional
    private const val PNG_QUALITY = 100
  }
}