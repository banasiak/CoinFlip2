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
 * The images behind each [CustomCoin], under `filesDir/coins`.
 *
 * Both coins share the directory and are told apart by [CustomCoin.fileName], so every member takes
 * the coin it is about -- including [deleteAll], which must never reach past the coin it was asked
 * for.
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

  fun exists(coin: CustomCoin, face: Face): Boolean = file(coin, face).isFile

  // which faces are set. The UI reflects this without decoding anything.
  fun storedFaces(coin: CustomCoin): Set<Face> = Face.entries.filterTo(mutableSetOf()) { exists(coin, it) }

  // a custom coin is only offered once both its faces are set; one face on its own is not a coin
  fun isComplete(coin: CustomCoin): Boolean = Face.entries.all { exists(coin, it) }

  /**
   * Moves with every write, so `AnimationHelper`'s cache key changes when a face is replaced. The
   * prefix survives a re-upload, so without this the old artwork would stay on screen. Per coin, so
   * writing one does not invalidate the other's animations.
   */
  fun revision(coin: CustomCoin): Long = Face.entries.maxOf { file(coin, it).lastModified() }

  /**
   * Both faces, freshly decoded and **mutable**, or null unless both are present.
   *
   * Fresh on every call by design: the caller strokes the rim onto them in place, and a shared
   * bitmap would collect a ring per reload. Mutable for the same reason -- see [decode].
   */
  fun faces(coin: CustomCoin): Pair<Bitmap, Bitmap>? {
    val heads = decode(coin, Face.HEADS) ?: return null
    val tails = decode(coin, Face.TAILS) ?: return null
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
      write(CustomCoin.PHOTO, face, bitmap)
    }

  /**
   * Renders [emoji] as a face of [CustomCoin.EMOJI] and writes it.
   *
   * No crop and no color: a glyph has nothing to frame, and the disc behind it follows the Material
   * theme, so it is drawn at animation time rather than stored. Everything else about the file is
   * identical to a photographed face, which is what lets the rest of the app stay ignorant of where
   * a face came from.
   */
  suspend fun save(emoji: String, face: Face): Boolean =
    withContext(Dispatchers.IO) { write(CustomCoin.EMOJI, face, CoinImage.toEmojiFace(emoji, coinSize())) }

  /**
   * Unlinks everything belonging to [coin] -- not just the two names it knows about, so a leftover
   * from an earlier version goes too, and **not** the whole directory, which would take the other
   * coin with it. Returns whether there was anything to delete.
   */
  suspend fun deleteAll(coin: CustomCoin): Boolean =
    withContext(Dispatchers.IO) {
      val files = directory.listFiles { file -> file.name.startsWith("${coin.prefix}_") }.orEmpty()
      files.forEach { it.delete() }
      files.isNotEmpty()
    }

  /**
   * [deleteAll] with nobody left to wait on it: `ViewModel.onCleared` runs after `viewModelScope`
   * has been cancelled and cannot suspend.
   */
  fun deleteAllDetached(coin: CustomCoin) {
    scope.launch { deleteAll(coin) }
  }

  /**
   * A face decoded down to roughly [targetPx] for a thumbnail. Deliberately not the density-matched
   * [decode]: this is drawn at a fixed dp size rather than composited into the animation frames.
   */
  fun thumbnail(coin: CustomCoin, face: Face, targetPx: Int): Bitmap? {
    val file = file(coin, face)
    if (!file.isFile || targetPx <= 0) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val options = BitmapFactory.Options().apply { inSampleSize = CoinImage.sampleSizeFor(bounds.outWidth, targetPx) }
    return BitmapFactory.decodeFile(file.path, options)
  }

  private fun file(coin: CustomCoin, face: Face): File = File(directory, coin.fileName(face))

  /**
   * Writes a finished face and releases it.
   *
   * Shared by both [save] overloads so the two sources cannot drift on where the file lands, what a
   * failure reports, or when the bitmap is freed. Callers are already on `Dispatchers.IO`.
   */
  private fun write(coin: CustomCoin, face: Face, bitmap: Bitmap): Boolean =
    try {
      directory.mkdirs()
      file(coin, face).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
      true
    } catch (e: IOException) {
      Timber.w(e, "Could not write the $coin $face face")
      false
    } finally {
      bitmap.recycle()
    }

  /**
   * The coin's size in mdpi pixels, read off the backdrop the frames are composited against so it
   * cannot drift from the shipped artwork. `inScaled` is off deliberately: this wants the asset's
   * own size, not what the current display would stretch it to.
   *
   * Stays private. The emoji picker's preview draws the same glyph at its own box, through the
   * ratios in [CoinImage.glyphScale]; one that asked for this number instead would couple itself to
   * something it does not need and still not be pixel-identical, at a different density.
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
  private fun decode(coin: CustomCoin, face: Face): Bitmap? {
    val file = file(coin, face)
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