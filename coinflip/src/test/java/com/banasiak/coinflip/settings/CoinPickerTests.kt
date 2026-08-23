package com.banasiak.coinflip.settings

import com.banasiak.coinflip.R
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class CoinPickerTests {
  private val entries = arrayOf("George Washington Dollar", "Alabama Quarter", "Canadian Dollar", "One Euro")
  private val values = arrayOf("gw", "al", "cad", "euro1")
  private val groups = arrayOf("us", "us", "canada", "euro")

  private fun build(query: String = "") = buildCoinList(entries, values, groups, query)

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
    buildCoinList(arrayOf("Mystery Coin"), arrayOf("mystery"), arrayOf("atlantis"), "") shouldBeEqualTo
      listOf(
        CoinListItem.Group(R.string.settings_item_coin_group_other),
        CoinListItem.Option("Mystery Coin", "mystery")
      )
  }

  @Test
  fun `a value with no matching label is skipped rather than rendered blank`() {
    // guards against a coins_values entry that coins never gained a name for
    buildCoinList(arrayOf("George Washington Dollar"), values, groups, "") shouldBeEqualTo
      listOf(
        CoinListItem.Group(R.string.settings_item_coin_group_us),
        CoinListItem.Option("George Washington Dollar", "gw")
      )
  }

  @Test
  fun `a missing group entry falls back to other`() {
    buildCoinList(entries, values, arrayOf("us"), "").filterIsInstance<CoinListItem.Group>() shouldBeEqualTo
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
        query = ""
      ).filterIsInstance<CoinListItem.Group>()

    headers shouldBeEqualTo
      listOf(
        CoinListItem.Group(R.string.settings_item_coin_group_us),
        CoinListItem.Group(R.string.settings_item_coin_group_canada),
        CoinListItem.Group(R.string.settings_item_coin_group_us)
      )
  }
}