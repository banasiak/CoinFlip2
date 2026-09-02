package com.banasiak.coinflip.settings

import com.banasiak.coinflip.common.CoinType
import com.banasiak.coinflip.common.CustomCoin
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

// the line a custom coin's row shows about itself: the coin has been created or it has not, and if
// it has, it is either the one in use or it is not
class CustomCoinSummaryTests {
  private val both = setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)

  private fun stateOf(coin: CustomCoin, faces: Set<CustomCoin.Face>, selected: String = Setting.COIN.default) =
    SettingsState(coin = selected, custom = mapOf(coin to CustomCoinState(faces)))

  @Test
  fun `no faces invites the user to make one`() {
    CustomCoin.entries.forEach { coin ->
      customCoinSummary(SettingsState(), coin) shouldBeEqualTo CustomCoinSummary.CREATE
    }
  }

  @Test
  fun `one face is not a created coin, whichever face it is`() {
    CustomCoin.Face.entries.forEach { face ->
      customCoinSummary(stateOf(CustomCoin.PHOTO, setOf(face)), CustomCoin.PHOTO) shouldBeEqualTo
        CustomCoinSummary.CREATE
    }
  }

  @Test
  fun `each coin reports on itself, so building one does not light up the other`() {
    val state = stateOf(CustomCoin.EMOJI, both, selected = CustomCoin.EMOJI.prefix)

    customCoinSummary(state, CustomCoin.EMOJI) shouldBeEqualTo CustomCoinSummary.SELECTED
    customCoinSummary(state, CustomCoin.PHOTO) shouldBeEqualTo CustomCoinSummary.CREATE
  }

  @Test
  fun `both faces and the coin in use says so`() {
    val state = stateOf(CustomCoin.PHOTO, both, selected = CustomCoin.PHOTO.prefix)

    customCoinSummary(state, CustomCoin.PHOTO) shouldBeEqualTo CustomCoinSummary.SELECTED
  }

  @Test
  fun `both faces but another coin in use points at the picker`() {
    val state = stateOf(CustomCoin.PHOTO, both, selected = CoinType.JFK.prefix)

    customCoinSummary(state, CustomCoin.PHOTO) shouldBeEqualTo CustomCoinSummary.UNSELECTED
  }

  @Test
  fun `a half-made coin still reads as uncreated even while its prefix is somehow selected`() {
    // deleting one face resets the selection, so this should not arise -- but the row would be
    // claiming a coin that is not in the picker if it did
    val state = stateOf(CustomCoin.PHOTO, setOf(CustomCoin.Face.HEADS), selected = CustomCoin.PHOTO.prefix)

    customCoinSummary(state, CustomCoin.PHOTO) shouldBeEqualTo CustomCoinSummary.CREATE
  }

  @Test
  fun `every state is reachable, so none of the three is dead`() {
    val seen =
      setOf(
        customCoinSummary(SettingsState(), CustomCoin.PHOTO),
        customCoinSummary(stateOf(CustomCoin.PHOTO, both, CustomCoin.PHOTO.prefix), CustomCoin.PHOTO),
        customCoinSummary(stateOf(CustomCoin.PHOTO, both, CoinType.JFK.prefix), CustomCoin.PHOTO)
      )

    seen shouldBeEqualTo CustomCoinSummary.entries.toSet()
  }
}