package com.banasiak.coinflip.settings

import android.graphics.Paint
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.CustomCoin
import com.banasiak.coinflip.common.Emoji
import com.banasiak.coinflip.common.EmojiCatalog
import com.banasiak.coinflip.common.EmojiGroup
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen
import com.banasiak.coinflip.ui.theme.emojiDisc
import com.banasiak.coinflip.ui.theme.faceColor
import com.banasiak.coinflip.util.CoinImage
import kotlinx.coroutines.launch

/**
 * Picks the emoji for one face, and shows it on the coin it is about to become.
 *
 * There is no crop step, unlike a photographed face: a glyph has nothing to frame. The preview
 * stands in for one, and it is honest because it goes through [CoinImage.drawGlyph] -- the same fit,
 * scale-free, that the stored face gets. [otherEmoji] is the opposite face when it is already set,
 * because the pairing is the point of this coin and a per-face picker would otherwise never let the
 * user see the two together until both were committed.
 */
@Composable
fun EmojiPicker(
  face: CustomCoin.Face,
  initialEmoji: String?,
  otherEmoji: String?,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    // a full-screen dialog, so it owns the whole window and draws edge to edge like a destination
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
  ) {
    EmojiPickerContent(face, initialEmoji, otherEmoji, onConfirm, onDismiss)
  }
}

@Composable
private fun EmojiPickerContent(
  face: CustomCoin.Face,
  initialEmoji: String? = null,
  otherEmoji: String? = null,
  onConfirm: (String) -> Unit = { },
  onDismiss: () -> Unit = { }
) {
  var query by rememberSaveable { mutableStateOf("") }
  // keyed on the face: the dialog underneath can point this at the other side without the picker
  // leaving the composition, and the glyph already chosen must not follow it there
  var glyph by rememberSaveable(face) { mutableStateOf(initialEmoji) }
  // unkeyed: the system font is fixed for this process, and hasGlyph shapes every string it is
  // handed, so re-running it per keystroke over the whole catalog would show
  val supported = remember { EmojiCatalog.entries.supportedGlyphs() }
  val items = remember(query, supported) { buildEmojiList(EmojiCatalog.entries, query, supported) }
  val gridState = rememberLazyGridState()
  val scope = rememberCoroutineScope()

  // reopening a face that is already set should land on it rather than at the top of 700 entries
  LaunchedEffect(face) {
    val index = items.indexOfFirst { it is EmojiListItem.Option && it.emoji.glyph == initialEmoji }
    if (index > 0) gridState.scrollToItem(index)
  }

  Surface(modifier = Modifier.fillMaxSize()) {
    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = { EmojiPickerTopBar(onDismiss) }
    ) { contentPadding ->
      Column(modifier = Modifier.padding(contentPadding)) {
        CoinPreviewRow(face, glyph, otherEmoji)
        SearchField(query = query, onQueryChange = { query = it })
        // hidden while searching: the results are already short, and the groups it would name are
        // the filtered ones rather than the catalog's
        if (query.isBlank()) {
          GroupJumpBar(items) { index -> scope.launch { gridState.scrollToItem(index) } }
        }
        if (items.isEmpty()) {
          EmptyResults(R.string.settings_item_custom_coin_emoji_empty)
        } else {
          LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(Dimen.emojiCell),
            modifier =
              Modifier
                .weight(1f)
                .selectableGroup()
                .padding(horizontal = Dimen.small)
          ) {
            items(
              items = items,
              key = { it.key() },
              span = { if (it is EmojiListItem.Group) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
            ) { item ->
              when (item) {
                is EmojiListItem.Group -> {
                  CategoryHeader(stringResource(item.title))
                }
                is EmojiListItem.Option -> {
                  EmojiCell(
                    emoji = item.emoji.glyph,
                    selected = item.emoji.glyph == glyph,
                    onClick = { glyph = item.emoji.glyph }
                  )
                }
              }
            }
          }
        }
        PickerActions(
          enabled = glyph != null,
          onDismiss = onDismiss,
          onConfirm = {
            glyph?.let(onConfirm)
            onDismiss()
          }
        )
      }
    }
  }
}

/** The coin as it will be saved, beside the face already on the other side of it. */
@Composable
private fun CoinPreviewRow(face: CustomCoin.Face, glyph: String?, otherEmoji: String?) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(vertical = Dimen.medium),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    CoinPreview(face, glyph, Dimen.coinPreview)
    if (otherEmoji != null) {
      Column(
        modifier = Modifier.padding(start = Dimen.medium),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        CoinPreview(face.other, otherEmoji, Dimen.coinPreviewSmall)
        Text(
          text = stringResource(face.other.label),
          style = MaterialTheme.typography.labelSmall,
          color = face.other.faceColor()
        )
      }
    }
  }
}

@Composable
private fun CoinPreview(face: CustomCoin.Face, glyph: String?, diameter: Dp) {
  val disc = emojiDisc
  val rimColor = face.faceColor()
  Canvas(modifier = Modifier.size(diameter)) {
    val box = size.minDimension
    val radius = box / 2f
    drawCircle(color = disc, radius = radius)
    // the same fit the stored face gets, at this box rather than at 390: everything in it is a
    // ratio, which is what makes this a substitute for a crop screen
    glyph?.let { CoinImage.drawGlyph(drawContext.canvas.nativeCanvas, it, box) }
    val width = box * CoinImage.RIM_FRACTION
    drawCircle(color = rimColor, radius = radius - width / 2f, style = Stroke(width))
  }
}

/**
 * Jumps the grid to a group, the way every system emoji keyboard does.
 *
 * Each group is labelled by its own first surviving entry rather than by a second list of icons to
 * keep in step, so a group the device cannot draw at all loses its icon along with its rows.
 */
@Composable
private fun GroupJumpBar(items: List<EmojiListItem>, onJump: (Int) -> Unit) {
  val headers = remember(items) { items.groupHeaderIndices() }
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = Dimen.small)
  ) {
    headers.forEach { (index, icon) ->
      Text(
        text = icon,
        modifier =
          Modifier
            .clickable { onJump(index) }
            .padding(Dimen.small),
        style = MaterialTheme.typography.titleLarge
      )
    }
  }
}

@Composable
private fun EmojiCell(emoji: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(Dimen.xsmall),
    contentAlignment = Alignment.Center
  ) {
    if (selected) {
      val highlight = MaterialTheme.colorScheme.secondaryContainer
      Canvas(modifier = Modifier.size(Dimen.emojiCell)) {
        drawCircle(color = highlight, radius = size.minDimension / 2f)
      }
    }
    // a Text rather than drawGlyph on a Canvas, even though that would share one code path with the
    // preview: TalkBack announces an emoji's name from a Text, localized, and reads a Canvas as
    // nothing at all. The cell follows the text so a large font size grows it rather than clipping.
    Text(
      text = emoji,
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(Dimen.xsmall)
    )
  }
}

@Composable
private fun PickerActions(enabled: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = Dimen.small, vertical = Dimen.xsmall),
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.CenterVertically
  ) {
    TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
    TextButton(enabled = enabled, onClick = onConfirm) { Text(stringResource(android.R.string.ok)) }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiPickerTopBar(onDismiss: () -> Unit) {
  TopAppBar(
    title = { Text(stringResource(R.string.settings_item_custom_coin_emoji_picker_title)) },
    navigationIcon = {
      IconButton(onClick = onDismiss) {
        Icon(
          painter = painterResource(R.drawable.close),
          contentDescription = stringResource(android.R.string.cancel)
        )
      }
    }
  )
}

// the opposite side, for a preview that shows the pair rather than one face of it
private val CustomCoin.Face.other: CustomCoin.Face
  get() = if (this == CustomCoin.Face.HEADS) CustomCoin.Face.TAILS else CustomCoin.Face.HEADS

/**
 * The catalog entries this device's font can actually draw.
 *
 * Trustworthy for the same reason nothing else here has to be: it queries the same system
 * `Typeface` the `drawText` a moment later will use, so the picker cannot offer something the save
 * would render as tofu.
 */
private fun List<Emoji>.supportedGlyphs(): Set<String> {
  val paint = Paint()
  return mapNotNullTo(mutableSetOf()) { entry -> entry.glyph.takeIf { paint.hasGlyph(it) } }
}

internal sealed interface EmojiListItem {
  data class Group(@param:StringRes val title: Int) : EmojiListItem

  data class Option(val emoji: Emoji) : EmojiListItem
}

@VisibleForTesting
internal fun EmojiListItem.key(): String =
  when (this) {
    is EmojiListItem.Group -> "group-$title"
    is EmojiListItem.Option -> "emoji-${emoji.glyph}"
  }

/** One tab of the jump bar: where to scroll, what to draw there, and what to announce it as. */
internal data class GroupJump(val index: Int, val icon: String, @param:StringRes val title: Int)

/**
 * Where each group's header sits in [items], paired with the first emoji under it.
 *
 * The index is what the jump bar scrolls to and the glyph is what it draws, so they are resolved
 * together -- and the icon comes from the group's own first surviving entry rather than a second
 * list to keep in step. A header with nothing under it could not be labelled and cannot happen,
 * since [buildEmojiList] does not emit one.
 */
@VisibleForTesting
internal fun List<EmojiListItem>.groupHeaderIndices(): List<GroupJump> =
  mapIndexedNotNull { index, item ->
    if (item !is EmojiListItem.Group) return@mapIndexedNotNull null
    val first = getOrNull(index + 1) as? EmojiListItem.Option ?: return@mapIndexedNotNull null
    GroupJump(index, first.emoji.glyph, item.title)
  }

/**
 * Filters by [query] and interleaves a header ahead of each group that still has matches.
 *
 * [supported] is what the device's font can draw, resolved once by the caller rather than per
 * keystroke. A group nothing survives in loses its header along with its rows, which is what keeps
 * an OEM font with a hole in it from leaving an empty shelf on screen.
 *
 * A query that is itself a single emoji the device can draw is offered even when the catalog does
 * not carry it, so the system keyboard reaches anything the curation rules left out.
 */
@VisibleForTesting
internal fun buildEmojiList(
  catalog: List<Emoji>,
  query: String,
  supported: Set<String>
): List<EmojiListItem> {
  val trimmed = query.trim()
  val matches =
    catalog.filter { emoji ->
      emoji.glyph in supported &&
        (trimmed.isBlank() || emoji.glyph == trimmed || emoji.keywords.contains(trimmed, ignoreCase = true))
    }
  val pasted = trimmed.takeIf { it.isNotEmpty() && it in supported && matches.none { m -> m.glyph == it } }

  return buildList {
    // ahead of every header, unlabelled: it is the thing the user just typed, not a category
    pasted?.let { add(EmojiListItem.Option(Emoji(it, it, EmojiGroup.SYMBOLS))) }

    var currentGroup: EmojiGroup? = null
    matches.forEach { emoji ->
      if (emoji.group != currentGroup) {
        add(EmojiListItem.Group(emoji.group.label))
        currentGroup = emoji.group
      }
      add(EmojiListItem.Option(emoji))
    }
  }
}

@PreviewLightDark
@Composable
fun EmojiPickerPreview() {
  AppTheme {
    EmojiPickerContent(face = CustomCoin.Face.HEADS, initialEmoji = "🍕", otherEmoji = "🌮")
  }
}