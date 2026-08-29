package com.banasiak.coinflip.settings

import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.CoinGroup
import com.banasiak.coinflip.common.CoinType
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotContain
import org.junit.jupiter.api.Test

class CoinPickerTests {
  private val favoritesHeader = R.string.settings_item_coin_group_favorites

  // a slice of the real catalog rather than invented coins: the picker's only input is now the
  // enum, so a fixture that could not exist would prove nothing
  private val coins =
    listOf(CoinType.GEORGE_WASHINGTON, CoinType.ALABAMA, CoinType.LOONIE, CoinType.TOONIE, CoinType.FRANCE)

  private fun build(query: String = "", favorites: Set<String> = emptySet()) = buildCoinList(coins, favorites, query)

  private fun labels(query: String = "") = build(query).filterIsInstance<CoinListItem.Option>().map { it.coin.coinName }

  @Test
  fun `an empty query lists every coin under a header per group`() {
    build() shouldBeEqualTo
      listOf(
        CoinListItem.Group(CoinGroup.US.label),
        CoinListItem.Option(CoinType.GEORGE_WASHINGTON),
        CoinListItem.Option(CoinType.ALABAMA),
        CoinListItem.Group(CoinGroup.CANADA.label),
        CoinListItem.Option(CoinType.LOONIE),
        CoinListItem.Option(CoinType.TOONIE),
        CoinListItem.Group(CoinGroup.EURO.label),
        CoinListItem.Option(CoinType.FRANCE)
      )
  }

  @Test
  fun `a query matches anywhere in the label and ignores case`() {
    labels("QUARTER") shouldBeEqualTo listOf("Alabama Quarter")
    labels("canadian") shouldBeEqualTo listOf("Canadian Loonie", "Canadian Toonie")
    labels("washington") shouldBeEqualTo listOf("George Washington Dollar")
  }

  @Test
  fun `surrounding whitespace in the query is ignored`() {
    labels("  euro  ") shouldBeEqualTo listOf("France Euro")
    labels("   ") shouldBeEqualTo labels("")
  }

  @Test
  fun `groups with no surviving match lose their header`() {
    build("euro") shouldBeEqualTo
      listOf(
        CoinListItem.Group(CoinGroup.EURO.label),
        CoinListItem.Option(CoinType.FRANCE)
      )
  }

  @Test
  fun `a query that matches nothing yields an empty list`() {
    build("doubloon") shouldBeEqualTo emptyList()
  }

  @Test
  fun `headers only break on a change of group, so the catalog has to stay sorted`() {
    // an interleaved CoinType would repeat headers rather than merge the runs
    val headers =
      buildCoinList(
        coins = listOf(CoinType.GEORGE_WASHINGTON, CoinType.LOONIE, CoinType.ALABAMA),
        favorites = emptySet(),
        query = ""
      ).filterIsInstance<CoinListItem.Group>()

    headers shouldBeEqualTo
      listOf(
        CoinListItem.Group(CoinGroup.US.label),
        CoinListItem.Group(CoinGroup.CANADA.label),
        CoinListItem.Group(CoinGroup.US.label)
      )
  }

  @Test
  fun `starred coins get a section at the top and stay in their origin group`() {
    val list = build(favorites = setOf(CoinType.LOONIE.prefix))

    list shouldBeEqualTo
      listOf(
        CoinListItem.Group(favoritesHeader),
        CoinListItem.Option(CoinType.LOONIE, inFavoritesSection = true),
        CoinListItem.Group(CoinGroup.US.label),
        CoinListItem.Option(CoinType.GEORGE_WASHINGTON),
        CoinListItem.Option(CoinType.ALABAMA),
        CoinListItem.Group(CoinGroup.CANADA.label),
        CoinListItem.Option(CoinType.LOONIE),
        CoinListItem.Option(CoinType.TOONIE),
        CoinListItem.Group(CoinGroup.EURO.label),
        CoinListItem.Option(CoinType.FRANCE)
      )
  }

  @Test
  fun `the duplicated rows carry different keys so LazyColumn does not collide`() {
    val keys = build(favorites = setOf(CoinType.LOONIE.prefix)).map { it.key() }

    keys.distinct().size shouldBeEqualTo keys.size
    keys shouldContain "favorite-loonie"
    keys shouldContain "coin-loonie"
  }

  @Test
  fun `no favorites means no favorites header`() {
    build().filterIsInstance<CoinListItem.Group>().map { it.title } shouldNotContain favoritesHeader
  }

  @Test
  fun `the favorites section keeps the order the coins appear in the catalog`() {
    val starred =
      build(favorites = setOf(CoinType.FRANCE.prefix, CoinType.GEORGE_WASHINGTON.prefix, CoinType.ALABAMA.prefix))
        .takeWhile { it !is CoinListItem.Group || it.title == favoritesHeader }
        .filterIsInstance<CoinListItem.Option>()

    starred.map { it.coin } shouldBeEqualTo listOf(CoinType.GEORGE_WASHINGTON, CoinType.ALABAMA, CoinType.FRANCE)
  }

  @Test
  fun `a search drops the favorites section so a hit is not listed twice`() {
    build(query = "euro", favorites = setOf(CoinType.GEORGE_WASHINGTON.prefix, CoinType.FRANCE.prefix)) shouldBeEqualTo
      listOf(
        CoinListItem.Group(CoinGroup.EURO.label),
        CoinListItem.Option(CoinType.FRANCE)
      )
  }

  @Test
  fun `clearing the search brings the favorites section back`() {
    val favorites = setOf(CoinType.GEORGE_WASHINGTON.prefix, CoinType.FRANCE.prefix)

    build(query = "euro", favorites = favorites).none { it is CoinListItem.Group && it.title == favoritesHeader } shouldBeEqualTo true
    build(query = "", favorites = favorites).first() shouldBeEqualTo CoinListItem.Group(favoritesHeader)
    build(query = "   ", favorites = favorites).first() shouldBeEqualTo CoinListItem.Group(favoritesHeader)
  }

  @Test
  fun `a favorite that no longer exists in the catalog is ignored`() {
    build(favorites = setOf("doubloon")) shouldBeEqualTo build()
  }
}