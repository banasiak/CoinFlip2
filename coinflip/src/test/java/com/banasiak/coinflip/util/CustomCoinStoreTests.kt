package com.banasiak.coinflip.util

import android.content.Context
import android.content.res.Resources
import com.banasiak.coinflip.common.CustomCoin
import com.banasiak.coinflip.common.CustomCoin.Face
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The file half of the store. Anything that decodes or writes a bitmap is left to the manual pass --
 * `Bitmap` is an android.jar stub here -- so these cover what survives without it: where the files
 * land, when a coin counts as complete, that a removal can be undone, and that the two coins sharing
 * a directory stay out of each other's way.
 */
class CustomCoinStoreTests {
  @TempDir
  lateinit var tempDir: File

  private lateinit var store: CustomCoinStore

  @BeforeEach
  fun setUp() {
    val context = mockk<Context> { every { filesDir } returns tempDir }
    store = CustomCoinStore(context, mockk<Resources>())
  }

  @Test
  fun `nothing written means no faces and no coin`() {
    CustomCoin.entries.forEach { coin ->
      store.exists(coin, Face.HEADS).shouldBeFalse()
      store.exists(coin, Face.TAILS).shouldBeFalse()
      store.isComplete(coin).shouldBeFalse()
    }
  }

  @Test
  fun `one face on its own is not a coin`() {
    write(CustomCoin.PHOTO, Face.HEADS)

    store.exists(CustomCoin.PHOTO, Face.HEADS).shouldBeTrue()
    store.isComplete(CustomCoin.PHOTO).shouldBeFalse()
  }

  @Test
  fun `both faces make a coin`() {
    writeBoth(CustomCoin.PHOTO)

    store.isComplete(CustomCoin.PHOTO).shouldBeTrue()
  }

  @Test
  fun `the faces land under the directory the backup rules name`() {
    write(CustomCoin.PHOTO, Face.HEADS)

    // res/xml/backup_rules.xml includes this path by name; a move here has to move there too
    File(tempDir, CustomCoin.DIRECTORY).resolve(CustomCoin.PHOTO.fileName(Face.HEADS)).isFile.shouldBeTrue()
  }

  @Test
  fun `the photo coin's file names are the ones earlier versions wrote`() {
    // they are already on disk from before the emoji coin existed, and Setting.COIN still holds this
    // prefix; renaming either would silently lose somebody's coin
    CustomCoin.PHOTO.prefix shouldBeEqualTo "custom"
    CustomCoin.PHOTO.fileName(Face.HEADS) shouldBeEqualTo "custom_heads.png"
    CustomCoin.PHOTO.fileName(Face.TAILS) shouldBeEqualTo "custom_tails.png"
  }

  @Test
  fun `the two coins do not share a file name`() {
    val names = CustomCoin.entries.flatMap { coin -> Face.entries.map { coin.fileName(it) } }

    names.distinct().size shouldBeEqualTo names.size
  }

  @Test
  fun `each coin is complete on its own`() {
    writeBoth(CustomCoin.EMOJI)

    store.isComplete(CustomCoin.EMOJI).shouldBeTrue()
    store.isComplete(CustomCoin.PHOTO).shouldBeFalse()
    store.storedFaces(CustomCoin.PHOTO).shouldBeEmpty()
  }

  @Test
  fun `revision is zero before anything is written`() {
    store.revision(CustomCoin.PHOTO) shouldBeEqualTo 0L
  }

  @Test
  fun `revision follows the most recently written face`() {
    writeBoth(CustomCoin.PHOTO)
    faceFile(CustomCoin.PHOTO, Face.HEADS).setLastModified(1_000_000L)
    faceFile(CustomCoin.PHOTO, Face.TAILS).setLastModified(2_000_000L)

    // AnimationHelper keys its cache on this, so replacing either face has to move it
    store.revision(CustomCoin.PHOTO) shouldBeEqualTo 2_000_000L
  }

  @Test
  fun `writing one coin does not move the other's revision`() {
    // they key separate animation caches; a shared revision would redraw a coin nothing happened to
    writeBoth(CustomCoin.PHOTO)
    faceFile(CustomCoin.PHOTO, Face.HEADS).setLastModified(1_000_000L)
    faceFile(CustomCoin.PHOTO, Face.TAILS).setLastModified(1_000_000L)
    writeBoth(CustomCoin.EMOJI)
    faceFile(CustomCoin.EMOJI, Face.HEADS).setLastModified(5_000_000L)
    faceFile(CustomCoin.EMOJI, Face.TAILS).setLastModified(5_000_000L)

    store.revision(CustomCoin.PHOTO) shouldBeEqualTo 1_000_000L
    store.revision(CustomCoin.EMOJI) shouldBeEqualTo 5_000_000L
  }

  @Test
  fun `deleting takes both faces off the disk`() =
    runTest {
      writeBoth(CustomCoin.PHOTO)

      store.deleteAll(CustomCoin.PHOTO).shouldBeTrue()

      store.isComplete(CustomCoin.PHOTO).shouldBeFalse()
      store.exists(CustomCoin.PHOTO, Face.HEADS).shouldBeFalse()
      store.exists(CustomCoin.PHOTO, Face.TAILS).shouldBeFalse()
    }

  @Test
  fun `deleting one coin leaves the other's files exactly where they were`() =
    runTest {
      // they share a directory, and the delete used to empty it
      writeBoth(CustomCoin.PHOTO)
      writeBoth(CustomCoin.EMOJI)

      store.deleteAll(CustomCoin.PHOTO)

      store.isComplete(CustomCoin.PHOTO).shouldBeFalse()
      store.isComplete(CustomCoin.EMOJI).shouldBeTrue()
    }

  @Test
  fun `deleting the emoji coin leaves the photo coin alone too`() =
    runTest {
      writeBoth(CustomCoin.PHOTO)
      writeBoth(CustomCoin.EMOJI)

      store.deleteAll(CustomCoin.EMOJI)

      store.isComplete(CustomCoin.EMOJI).shouldBeFalse()
      store.isComplete(CustomCoin.PHOTO).shouldBeTrue()
    }

  @Test
  fun `deleting clears whatever else the directory is holding for that coin`() =
    runTest {
      // an earlier build parked a removed face beside its original for an undo; those are leftovers
      write(CustomCoin.PHOTO, Face.HEADS)
      File(tempDir, CustomCoin.DIRECTORY).resolve("custom_tails.png.removed").writeText("stale")

      store.deleteAll(CustomCoin.PHOTO)

      File(tempDir, CustomCoin.DIRECTORY).listFiles().orEmpty().toList().shouldBeEmpty()
    }

  @Test
  fun `deleting nothing reports that it did nothing`() =
    runTest {
      store.deleteAll(CustomCoin.PHOTO).shouldBeFalse()
    }

  private fun faceFile(coin: CustomCoin, face: Face) =
    File(tempDir, CustomCoin.DIRECTORY).resolve(coin.fileName(face))

  private fun writeBoth(coin: CustomCoin) = Face.entries.forEach { write(coin, it) }

  private fun write(coin: CustomCoin, face: Face, content: String = "art") {
    File(tempDir, CustomCoin.DIRECTORY).mkdirs()
    faceFile(coin, face).writeText(content)
  }
}