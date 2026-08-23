package com.banasiak.coinflip

import com.banasiak.coinflip.settings.COIN_GROUP_LABELS
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.settings.SettingsManager.Settings
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The coin arrays are three separate lists that only work because they line up index for index,
 * and the drawables they name are found at runtime by string concatenation. Nothing in the
 * compiler or in lint notices when one of them drifts, so these read the resources straight off
 * disk and assert the couplings the code silently depends on.
 */
class CoinResourcesTests {
  private val entries = stringArray("coins")
  private val values = stringArray("coins_values")
  private val groups = stringArray("coins_groups")

  @Test
  fun `the three coin arrays line up index for index`() {
    values.size shouldBeEqualTo entries.size
    groups.size shouldBeEqualTo entries.size
  }

  @Test
  fun `coin values and names are unique`() {
    values.duplicates().shouldBeEmpty()
    entries.duplicates().shouldBeEmpty()
  }

  @Test
  fun `every group key has a header label`() {
    groups.toSet().forEach { COIN_GROUP_LABELS.keys shouldContain it }
  }

  @Test
  fun `every group label is actually used`() {
    // an unused key means the picker has a header nobody will ever see
    COIN_GROUP_LABELS.keys.forEach { groups shouldContain it }
  }

  @Test
  fun `the arrays are sorted by group so the picker renders one header per group`() {
    // buildCoinList only breaks a header on a change of group, so a group may not reappear later
    val runs = groups.fold(emptyList<String>()) { acc, group -> if (acc.lastOrNull() == group) acc else acc + group }

    runs.duplicates().shouldBeEmpty()
  }

  @Test
  fun `every coin ships both faces`() {
    // AnimationHelper resolves these by name at runtime, so a missing file is a crash, not a build error
    val drawables = drawableNames()
    val missing = values.filterNot { it == RANDOM }.filterNot { "${it}_heads" in drawables && "${it}_tails" in drawables }

    missing.shouldBeEmpty()
  }

  @Test
  fun `random is the only value without art, and it sorts last`() {
    val drawables = drawableNames()

    ("${RANDOM}_heads" in drawables) shouldBeEqualTo false
    values.last() shouldBeEqualTo RANDOM
    groups.last() shouldBeEqualTo "other"
  }

  @Test
  fun `the default coin is one of the offered values`() {
    values shouldContain Settings.COIN.default as String
  }

  @Test
  fun `the force arrays line up and cover every sensitivity`() {
    val forceValues = stringArray("force_values")

    stringArray("force").size shouldBeEqualTo forceValues.size
    forceValues shouldContain Settings.FORCE.default as String

    // a value the manager does not recognize silently collapses onto medium; distinct results prove it does not
    val sensitivities = forceValues.map { SettingsManager(FakeSharedPreferences(mapOf(Settings.FORCE.key to it))).shakeSensitivity }
    sensitivities.distinct().size shouldBeEqualTo forceValues.size
  }

  private fun List<String>.duplicates(): List<String> = groupBy { it }.filterValues { it.size > 1 }.keys.toList()

  private fun stringArray(name: String): List<String> {
    val body =
      Regex("""<string-array name="$name"[^>]*>(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL)
        .find(arraysXml.readText())
        ?.groupValues
        ?.get(1)
        ?: error("no <string-array name=\"$name\"> in ${arraysXml.path}")
    return Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL).findAll(body).map { it.groupValues[1].trim() }.toList()
  }

  private fun drawableNames(): Set<String> =
    checkNotNull(resDir.resolve("drawable").listFiles()) { "no drawable directory under ${resDir.path}" }
      .map { it.name.substringBeforeLast('.') }
      .toSet()

  companion object {
    private const val RANDOM = "random"

    /** The test JVM runs from the module directory, but tolerate being launched from the repo root. */
    private val resDir: File =
      generateSequence(File("").absoluteFile) { it.parentFile }
        .flatMap { sequenceOf(File(it, "src/main/res"), File(it, "coinflip/src/main/res")) }
        .firstOrNull { it.isDirectory }
        ?: error("could not locate the resource directory from ${File("").absolutePath}")

    private val arraysXml = resDir.resolve("values/arrays.xml")
  }
}