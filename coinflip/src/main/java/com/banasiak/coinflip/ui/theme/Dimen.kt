package com.banasiak.coinflip.ui.theme

import androidx.compose.ui.unit.dp

class Dimen {
  companion object {
    val xlarge = 64.dp
    val large = 32.dp
    val medium = 16.dp
    val small = 8.dp
    val xsmall = 4.dp

    // a list or form wider than this strands trailing controls (a switch, a value) far from
    // their label on landscape phones and tablets
    val maxContent = 600.dp

    // leading artwork in the coin picker rows
    val coinThumbnail = 40.dp

    // the coin the emoji picker draws above its grid, big enough to judge a glyph at coin size
    val coinPreview = 128.dp

    // the same, for the face that is not being set: there for comparison, not for judging
    val coinPreviewSmall = 64.dp

    // one emoji, at Material's minimum touch target
    val emojiCell = 48.dp

    // the ring drawn for a coin that has no artwork to show: the random coin, and a custom face
    // that has not been set yet. Thin enough to read as an outline rather than as a coin of its own.
    val coinOutline = 1.dp

    // Material's icon-button footprint. A row whose trailing slot holds a static marker instead of
    // a button has to reserve the same width, or everything before it shifts across.
    val iconButton = 48.dp
  }
}