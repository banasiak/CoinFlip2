package com.banasiak.coinflip.settings

import com.banasiak.coinflip.common.CoinType
import com.banasiak.coinflip.common.CustomCoin
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

// the line the Custom Coin row shows about itself: the coin has been created or it has not, and if
// it has, it is either the one in use or it is not
class CustomCoinSummaryTests {
  private val both = setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)

  @Test
  fun `no faces invites the user to make one`() {
    customCoinSummary(SettingsState()) shouldBeEqualTo CustomCoinSummary.CREATE
  }

  @Test
  fun `one face is not a created coin, whichever face it is`() {
    CustomCoin.Face.entries.forEach { face ->
      customCoinSummary(SettingsState(customFaces = setOf(face))) shouldBeEqualTo CustomCoinSummary.CREATE
    }
  }

  @Test
  fun `both faces and the coin in use says so`() {
    val state = SettingsState(customFaces = both, coin = CustomCoin.PREFIX)

    customCoinSummary(state) shouldBeEqualTo CustomCoinSummary.SELECTED
  }

  @Test
  fun `both faces but another coin in use points at the picker`() {
    val state = SettingsState(customFaces = both, coin = CoinType.JFK.prefix)

    customCoinSummary(state) shouldBeEqualTo CustomCoinSummary.UNSELECTED
  }

  @Test
  fun `a half-made coin still reads as uncreated even while its prefix is somehow selected`() {
    // deleting one face resets the selection, so this should not arise -- but the row would be
    // claiming a coin that is not in the picker if it did
    val state = SettingsState(customFaces = setOf(CustomCoin.Face.HEADS), coin = CustomCoin.PREFIX)

    customCoinSummary(state) shouldBeEqualTo CustomCoinSummary.CREATE
  }

  @Test
  fun `every state is reachable, so none of the three is dead`() {
    val seen =
      setOf(
        customCoinSummary(SettingsState()),
        customCoinSummary(SettingsState(customFaces = both, coin = CustomCoin.PREFIX)),
        customCoinSummary(SettingsState(customFaces = both, coin = CoinType.JFK.prefix))
      )

    seen shouldBeEqualTo CustomCoinSummary.entries.toSet()
  }
}