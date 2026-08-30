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
 * land, when the coin counts as complete, and that a removal can be undone.
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
    store.exists(Face.HEADS).shouldBeFalse()
    store.exists(Face.TAILS).shouldBeFalse()
    store.isComplete.shouldBeFalse()
  }

  @Test
  fun `one face on its own is not a coin`() {
    write(Face.HEADS)

    store.exists(Face.HEADS).shouldBeTrue()
    store.isComplete.shouldBeFalse()
  }

  @Test
  fun `both faces make a coin`() {
    write(Face.HEADS)
    write(Face.TAILS)

    store.isComplete.shouldBeTrue()
  }

  @Test
  fun `the faces land under the directory the backup rules name`() {
    write(Face.HEADS)

    // res/xml/backup_rules.xml includes this path by name; a move here has to move there too
    File(tempDir, CustomCoin.DIRECTORY).resolve(Face.HEADS.fileName).isFile.shouldBeTrue()
  }

  @Test
  fun `revision is zero before anything is written`() {
    store.revision shouldBeEqualTo 0L
  }

  @Test
  fun `revision follows the most recently written face`() {
    write(Face.HEADS)
    write(Face.TAILS)
    faceFile(Face.HEADS).setLastModified(1_000_000L)
    faceFile(Face.TAILS).setLastModified(2_000_000L)

    // AnimationHelper keys its cache on this, so replacing either face has to move it
    store.revision shouldBeEqualTo 2_000_000L
  }

  @Test
  fun `deleting takes both faces off the disk`() =
    runTest {
      write(Face.HEADS)
      write(Face.TAILS)

      store.deleteAll().shouldBeTrue()

      store.isComplete.shouldBeFalse()
      store.exists(Face.HEADS).shouldBeFalse()
      store.exists(Face.TAILS).shouldBeFalse()
    }

  @Test
  fun `deleting clears whatever else the directory is holding`() =
    runTest {
      // an earlier build parked a removed face beside its original for an undo; those are leftovers
      write(Face.HEADS)
      File(tempDir, CustomCoin.DIRECTORY).resolve("custom_tails.png.removed").writeText("stale")

      store.deleteAll()

      File(tempDir, CustomCoin.DIRECTORY).listFiles().orEmpty().toList().shouldBeEmpty()
    }

  @Test
  fun `deleting nothing reports that it did nothing`() =
    runTest {
      store.deleteAll().shouldBeFalse()
    }

  private fun faceFile(face: Face) = File(tempDir, CustomCoin.DIRECTORY).resolve(face.fileName)

  private fun write(face: Face, content: String = "art") {
    File(tempDir, CustomCoin.DIRECTORY).mkdirs()
    faceFile(face).writeText(content)
  }
}