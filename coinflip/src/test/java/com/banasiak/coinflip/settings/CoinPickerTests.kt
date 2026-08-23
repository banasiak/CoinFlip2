package com.banasiak.coinflip.settings

import com.banasiak.coinflip.R
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotContain
import org.junit.jupiter.api.Test

class CoinPickerTests {
  private val favoritesHeader = R.string.settings_item_coin_group_favorites

  private val entries = arrayOf("George Washington Dollar", "Alabama Quarter", "Canadian Dollar", "One Euro")
  private val values = arrayOf("gw", "al", "cad", "euro1")
  private val groups = arrayOf("us", "us", "canada", "euro")

  private fun build(query: String = "", favorites: Set<String> = emptySet()) =
    buildCoinList(entries, values, groups, favorites, query)

  private fun labels(query: String = "") = build(query).filterIsInstance<CoinListItem.Option>().map { it.label }

  @Test
  fun `an empty query lists every coin under a header per group`() {
    build() shouldBeEqualTo
      listOf(
        CoinListItem.Group(R.string.settings_item_coin_group_us),
        CoinListItem.Option("George Washington Dollar", "gw"),
        CoinListItem.Option("Alabama Quarter", "al"),
        CoinListItem.Group(R.string.settings_item_coin_group_canada),
        CoinListItem.Option("Canadian Dollar", "cad"),
        CoinListItem.Group(R.string.settings_item_coin_group_euro),
        CoinListItem.Option("One Euro", "euro1")
      )
  }

  @Test
  fun `a query matches anywhere in the label and ignores case`() {
    labels("QUARTER") shouldBeEqualTo listOf("Alabama Quarter")
    labels("dollar") shouldBeEqualTo listOf("George Washington Dollar", "Canadian Dollar")
  }

  @Test
  fun `surrounding whitespace in the query is ignored`() {
    labels("  euro  ") shouldBeEqualTo listOf("One Euro")
    labels("   ") shouldBeEqualTo labels("")
  }

  @Test
  fun `groups with no surviving match lose their header`() {
    build("euro") shouldBeEqualTo
      listOf(
        CoinListItem.Group(R.string.settings_item_coin_group_euro),
        CoinListItem.Option("One Euro", "euro1")
      )
  }

  @Test
  fun `a query that matches nothing yields an empty list`() {
    build("doubloon") shouldBeEqualTo emptyList()
  }

  @Test
  fun `an unrecognized group key is filed under other`() {
    buildCoinList(arrayOf("Mystery Coin"), arrayOf("mystery"), arrayOf("atlantis"), emptySet(), "") shouldBeEqualTo
      listOf(
        CoinListItem.Group(R.string.settings_item_coin_group_other),
        CoinListItem.Option("Mystery Coin", "mystery")
      )
  }

  @Test
  fun `a value with no matching label is skipped rather than rendered blank`() {
    // guards against a coins_values entry that coins never gained a name for
    buildCoinList(arrayOf("George Washington Dollar"), values, groups, emptySet(), "") shouldBeEqualTo
      listOf(
        CoinListItem.Group(R.string.settings_item_coin_group_us),
        CoinListItem.Option("George Washington Dollar", "gw")
      )
  }

  @Test
  fun `a missing group entry falls back to other`() {
    buildCoinList(entries, values, arrayOf("us"), emptySet(), "").filterIsInstance<CoinListItem.Group>() shouldBeEqualTo
      listOf(
        CoinListItem.Group(R.string.settings_item_coin_group_us),
        CoinListItem.Group(R.string.settings_item_coin_group_other)
      )
  }

  @Test
  fun `headers only break on a change of group, so the arrays have to stay sorted`() {
    // an interleaved coins_groups would repeat headers rather than merge the runs
    val headers =
      buildCoinList(
        entries = arrayOf("A", "B", "C"),
        values = arrayOf("a", "b", "c"),
        groups = arrayOf("us", "canada", "us"),
        favorites = emptySet(),
        query = ""
      ).filterIsInstance<CoinListItem.Group>()

    headers shouldBeEqualTo
      listOf(
        CoinListItem.Group(R.string.settings_item_coin_group_us),
        CoinListItem.Group(R.string.settings_item_coin_group_canada),
        CoinListItem.Group(R.string.settings_item_coin_group_us)
      )
  }

  @Test
  fun `starred coins get a section at the top and stay in their origin group`() {
    val list = build(favorites = setOf("cad"))

    list shouldBeEqualTo
      listOf(
        CoinListItem.Group(R.string.settings_item_coin_group_favorites),
        CoinListItem.Option("Canadian Dollar", "cad", inFavoritesSection = true),
        CoinListItem.Group(R.string.settings_item_coin_group_us),
        CoinListItem.Option("George Washington Dollar", "gw"),
        CoinListItem.Option("Alabama Quarter", "al"),
        CoinListItem.Group(R.string.settings_item_coin_group_canada),
        CoinListItem.Option("Canadian Dollar", "cad"),
        CoinListItem.Group(R.string.settings_item_coin_group_euro),
        CoinListItem.Option("One Euro", "euro1")
      )
  }

  @Test
  fun `the duplicated rows carry different keys so LazyColumn does not collide`() {
    val keys = build(favorites = setOf("cad")).map { it.key() }

    keys.distinct().size shouldBeEqualTo keys.size
    keys shouldContain "favorite-cad"
    keys shouldContain "coin-cad"
  }

  @Test
  fun `no favorites means no favorites header`() {
    build().filterIsInstance<CoinListItem.Group>().map { it.title } shouldNotContain
      R.string.settings_item_coin_group_favorites
  }

  @Test
  fun `the favorites section keeps the order the coins appear in the arrays`() {
    val starred =
      build(favorites = setOf("euro1", "gw", "al"))
        .takeWhile { it !is CoinListItem.Group || it.title == R.string.settings_item_coin_group_favorites }
        .filterIsInstance<CoinListItem.Option>()

    starred.map { it.value } shouldBeEqualTo listOf("gw", "al", "euro1")
  }

  @Test
  fun `a search drops the favorites section so a hit is not listed twice`() {
    build(query = "euro", favorites = setOf("gw", "euro1")) shouldBeEqualTo
      listOf(
        CoinListItem.Group(R.string.settings_item_coin_group_euro),
        CoinListItem.Option("One Euro", "euro1")
      )
  }

  @Test
  fun `clearing the search brings the favorites section back`() {
    val favorites = setOf("gw", "euro1")

    build(query = "euro", favorites = favorites).none { it is CoinListItem.Group && it.title == favoritesHeader } shouldBeEqualTo true
    build(query = "", favorites = favorites).first() shouldBeEqualTo CoinListItem.Group(favoritesHeader)
    build(query = "   ", favorites = favorites).first() shouldBeEqualTo CoinListItem.Group(favoritesHeader)
  }

  @Test
  fun `a favorite that no longer exists in the arrays is ignored`() {
    build(favorites = setOf("doubloon")) shouldBeEqualTo build()
  }
}