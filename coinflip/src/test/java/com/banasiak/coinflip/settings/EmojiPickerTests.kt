package com.banasiak.coinflip.settings

import com.banasiak.coinflip.common.Emoji
import com.banasiak.coinflip.common.EmojiCatalog
import com.banasiak.coinflip.common.EmojiGroup
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.junit.jupiter.api.Test

/**
 * The list the emoji grid renders. Run against a slice of the real catalog rather than invented
 * emoji, so a curation change that breaks the grouping shows up here.
 */
class EmojiPickerTests {
  private val pizza = Emoji("🍕", "pizza slice", EmojiGroup.FOOD)
  private val taco = Emoji("🌮", "taco", EmojiGroup.FOOD)
  private val dog = Emoji("🐶", "dog face puppy", EmojiGroup.ANIMALS)
  private val catalog = listOf(dog, pizza, taco)
  private val all = catalog.map { it.glyph }.toSet()

  private fun build(query: String = "", supported: Set<String> = all) = buildEmojiList(catalog, query, supported)

  @Test
  fun `an empty query lists everything under a header per group`() {
    build() shouldBeEqualTo
      listOf(
        EmojiListItem.Group(EmojiGroup.ANIMALS.label),
        EmojiListItem.Option(dog),
        EmojiListItem.Group(EmojiGroup.FOOD.label),
        EmojiListItem.Option(pizza),
        EmojiListItem.Option(taco)
      )
  }

  @Test
  fun `a query matches the keywords and ignores case`() {
    build(query = "PIZZA") shouldBeEqualTo
      listOf(EmojiListItem.Group(EmojiGroup.FOOD.label), EmojiListItem.Option(pizza))
  }

  @Test
  fun `surrounding whitespace in the query is ignored`() {
    build(query = "  taco  ") shouldBeEqualTo build(query = "taco")
  }

  @Test
  fun `pasting an emoji from the catalog finds its own row rather than duplicating it`() {
    build(query = "🍕") shouldBeEqualTo
      listOf(EmojiListItem.Group(EmojiGroup.FOOD.label), EmojiListItem.Option(pizza))
  }

  @Test
  fun `an emoji the catalog does not carry is still offered when the device can draw it`() {
    // the escape hatch: the system keyboard reaches anything the curation rules left out
    val items = build(query = "🦩", supported = all + "🦩")

    items.first() shouldBeEqualTo EmojiListItem.Option(Emoji("🦩", "🦩", EmojiGroup.SYMBOLS))
  }

  @Test
  fun `an emoji the device cannot draw is not offered even when pasted`() {
    build(query = "🦩").shouldBeEmpty()
  }

  @Test
  fun `an emoji the device cannot draw is left out of the catalog entirely`() {
    build(supported = all - "🍕") shouldBeEqualTo
      listOf(
        EmojiListItem.Group(EmojiGroup.ANIMALS.label),
        EmojiListItem.Option(dog),
        EmojiListItem.Group(EmojiGroup.FOOD.label),
        EmojiListItem.Option(taco)
      )
  }

  @Test
  fun `a group the device cannot draw at all loses its header too`() {
    // an OEM font with a hole in it must not leave an empty shelf on screen
    build(supported = setOf(dog.glyph)) shouldBeEqualTo
      listOf(EmojiListItem.Group(EmojiGroup.ANIMALS.label), EmojiListItem.Option(dog))
  }

  @Test
  fun `a query that matches nothing yields an empty list`() {
    build(query = "doubloon").shouldBeEmpty()
  }

  @Test
  fun `every row carries a distinct key`() {
    val keys = buildEmojiList(EmojiCatalog.entries, "", EmojiCatalog.entries.map { it.glyph }.toSet()).map { it.key() }

    keys.distinct().size shouldBeEqualTo keys.size
  }

  @Test
  fun `the jump bar finds every group header, labelled by the first emoji under it`() {
    build().groupHeaderIndices() shouldBeEqualTo
      listOf(
        GroupJump(0, dog.glyph, EmojiGroup.ANIMALS.label),
        GroupJump(2, pizza.glyph, EmojiGroup.FOOD.label)
      )
  }

  @Test
  fun `the jump bar names no group the device cannot draw`() {
    val bar = build(supported = setOf(dog.glyph)).groupHeaderIndices()

    bar.map { it.icon } shouldContain dog.glyph
    bar.size shouldBeEqualTo 1
  }

  @Test
  fun `the real catalog opens one header per group and nothing before the first`() {
    val items = buildEmojiList(EmojiCatalog.entries, "", EmojiCatalog.entries.map { it.glyph }.toSet())

    items.first() shouldBeEqualTo EmojiListItem.Group(EmojiGroup.entries.first().label)
    items.groupHeaderIndices().size shouldBeEqualTo EmojiGroup.entries.size
  }
}