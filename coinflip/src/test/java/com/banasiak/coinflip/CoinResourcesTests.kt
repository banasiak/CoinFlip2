package com.banasiak.coinflip

import com.banasiak.coinflip.common.CoinGroup
import com.banasiak.coinflip.common.CoinType
import com.banasiak.coinflip.common.CustomCoin
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotContain
import org.junit.jupiter.api.Test
import java.io.File

/**
 * [CoinType] names its artwork by string concatenation, and the picker's headers depend on the
 * order the entries are declared in. Neither is something the compiler notices when it drifts, so
 * these read the drawables straight off disk and assert the couplings the code silently depends on.
 */
class CoinResourcesTests {
  @Test
  fun `coin prefixes and names are unique`() {
    CoinType.entries.map { it.prefix }.duplicates().shouldBeEmpty()
    CoinType.entries.map { it.coinName }.duplicates().shouldBeEmpty()
  }

  @Test
  fun `no shipped coin claims the custom coin's prefix`() {
    // the picker keys its rows on the prefix, and LazyColumn throws on two rows sharing one
    CoinType.entries.map { it.prefix } shouldNotContain CustomCoin.PHOTO.prefix
  }

  @Test
  fun `every group has coins in it`() {
    // an empty group means the picker has a header nobody will ever see
    CoinGroup.entries.filterNot { group -> CoinType.entries.any { it.group == group } }.shouldBeEmpty()
  }

  @Test
  fun `the catalog is sorted by group so the picker renders one header per group`() {
    // buildCoinList only breaks a header on a change of group, so a group may not reappear later
    val order = CoinType.entries.map { it.group }
    val runs = order.fold(emptyList<CoinGroup>()) { acc, group -> if (acc.lastOrNull() == group) acc else acc + group }

    runs.duplicates().shouldBeEmpty()
  }

  @Test
  fun `every coin ships both faces`() {
    // AnimationHelper resolves these by name at runtime, so a missing file is a crash, not a build error
    val drawables = drawableNames()
    val missing = CoinType.flippable.filterNot { "${it.prefix}_heads" in drawables && "${it.prefix}_tails" in drawables }

    missing.shouldBeEmpty()
  }

  @Test
  fun `random is the only coin without art, and it sorts last`() {
    val drawables = drawableNames()

    ("${CoinType.RANDOM.prefix}_heads" in drawables) shouldBeEqualTo false
    CoinType.entries.last() shouldBeEqualTo CoinType.RANDOM
    CoinType.RANDOM.group shouldBeEqualTo CoinGroup.OTHER
  }

  private fun <T> List<T>.duplicates(): List<T> = groupBy { it }.filterValues { it.size > 1 }.keys.toList()

  private fun drawableNames(): Set<String> =
    checkNotNull(resDir.resolve("drawable").listFiles()) { "no drawable directory under ${resDir.path}" }
      .map { it.name.substringBeforeLast('.') }
      .toSet()

  companion object {
    /** The test JVM runs from the module directory, but tolerate being launched from the repo root. */
    private val resDir: File =
      generateSequence(File("").absoluteFile) { it.parentFile }
        .flatMap { sequenceOf(File(it, "src/main/res"), File(it, "coinflip/src/main/res")) }
        .firstOrNull { it.isDirectory }
        ?: error("could not locate the resource directory from ${File("").absolutePath}")
  }
}