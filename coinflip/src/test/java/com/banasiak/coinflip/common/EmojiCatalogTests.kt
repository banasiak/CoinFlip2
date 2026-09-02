package com.banasiak.coinflip.common

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeEmpty
import org.junit.jupiter.api.Test

/**
 * The curation rules the catalog commits to. They are not cosmetic: `Paint.hasGlyph` is only
 * dependable for single code points, and a sequence a device cannot compose lands on the coin as
 * two glyphs side by side.
 */
class EmojiCatalogTests {
  private val entries = EmojiCatalog.entries

  @Test
  fun `every entry is exactly one code point`() {
    // one assertion that bars U+FE0F, zero-width joiners, skin-tone modifiers, keycaps and
    // regional-indicator flags together
    val offenders = entries.filter { it.glyph.codePointCount(0, it.glyph.length) != 1 }

    offenders.map { it.glyph } shouldBeEqualTo emptyList()
  }

  @Test
  fun `no glyph appears twice`() {
    // the grid keys on it, and LazyVerticalGrid throws on a duplicate key
    val glyphs = entries.map { it.glyph }

    glyphs.distinct().size shouldBeEqualTo glyphs.size
  }

  @Test
  fun `every entry has lowercase keywords`() {
    // the search lowercases the query, not the bag
    entries.filter { it.keywords.isBlank() }.map { it.glyph } shouldBeEqualTo emptyList()
    entries.filter { it.keywords != it.keywords.lowercase() }.map { it.glyph } shouldBeEqualTo emptyList()
  }

  @Test
  fun `every group has emoji in it`() {
    // an empty group is a translated label nobody will ever see
    EmojiGroup.entries.forEach { group ->
      entries.filter { it.group == group }.shouldNotBeEmpty()
    }
  }

  @Test
  fun `the catalog stays grouped, so the picker renders one header per group`() {
    // buildEmojiList opens a header on every change of group, so a group split in two would get two
    val runs = entries.map { it.group }.fold(emptyList<EmojiGroup>()) { acc, g -> if (acc.lastOrNull() == g) acc else acc + g }

    runs.distinct().size shouldBeEqualTo runs.size
  }

  @Test
  fun `the coin this was built for can be made`() {
    entries.any { it.glyph == "🍕" }.shouldBeTrue()
    entries.any { it.glyph == "🌮" }.shouldBeTrue()
  }
}