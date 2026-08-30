package com.banasiak.coinflip.settings

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.CoinGroup
import com.banasiak.coinflip.common.CoinType
import com.banasiak.coinflip.common.CustomCoin
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen

internal sealed interface CoinListItem {
  /** A [CoinGroup]'s header, or the favorites section, which is a pseudo-group with no coins of its own. */
  data class Group(@param:StringRes val title: Int) : CoinListItem

  /**
   * [inFavoritesSection] marks the copy rendered at the top. A starred coin appears twice, and
   * LazyColumn keys have to differ or it throws on the duplicate.
   */
  data class Option(val coin: CoinType, val inFavoritesSection: Boolean = false) : CoinListItem

  /**
   * The user's own coin. Not a [CoinListItem.Option] because it is not a [CoinType]: it owns no
   * drawable in the APK and `RANDOM` must not draw it. It appears only once both faces are set.
   */
  data class Custom(val inFavoritesSection: Boolean = false) : CoinListItem
}

/**
 * A full-screen picker for the 80-odd coins. The list is long enough that a plain modal list is
 * unusable, so this shows what each coin actually looks like, groups them by origin, and offers
 * a search field.
 */
@Composable
fun CoinPicker(
  selectedValue: String,
  favorites: Set<String>,
  customCoinReady: Boolean,
  customRevision: Long,
  loadThumbnail: (CustomCoin.Face, Int) -> ImageBitmap?,
  onSelect: (String) -> Unit,
  onToggleFavorite: (String) -> Unit,
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    // a full-screen dialog, so it owns the whole window and draws edge to edge like a destination
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
  ) {
    CoinPickerContent(
      selectedValue,
      favorites,
      customCoinReady,
      customRevision,
      loadThumbnail,
      onSelect,
      onToggleFavorite,
      onDismiss
    )
  }
}

@Composable
private fun CoinPickerContent(
  selectedValue: String,
  favorites: Set<String>,
  customCoinReady: Boolean = false,
  customRevision: Long = 0,
  loadThumbnail: (CustomCoin.Face, Int) -> ImageBitmap? = { _, _ -> null },
  onSelect: (String) -> Unit,
  onToggleFavorite: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var query by rememberSaveable { mutableStateOf("") }
  val customName = stringResource(R.string.settings_item_custom_coin_title)
  val items =
    remember(query, favorites, customCoinReady) {
      buildCoinList(CoinType.entries, favorites, query, customName.takeIf { customCoinReady })
    }
  val thumbnails = rememberThumbnails()
  val targetPx = with(LocalDensity.current) { Dimen.coinThumbnail.roundToPx() }
  // keyed on the revision too: replacing the artwork leaves the prefix alone, so nothing else here
  // would change
  val customArt =
    remember(customCoinReady, customRevision, targetPx) {
      if (customCoinReady) loadThumbnail(CustomCoin.Face.HEADS, targetPx) else null
    }
  val listState = rememberLazyListState()

  // Keyed on the query so editing or clearing the search lands on the top of the new results
  // instead of wherever the old list happened to sit. Deliberately not keyed on favorites: starring
  // a coin rebuilds the list too, and the row under the user's finger must not jump.
  LaunchedEffect(query) {
    if (query.isBlank() && favorites.isEmpty()) {
      // nothing pinned, so open on the coin that's already selected rather than at the top of a
      // very long list, keeping the row above it on screen so the group header isn't scrolled away
      val index = items.indexOfFirst { it is CoinListItem.Option && it.coin.prefix == selectedValue }
      if (index > 0) listState.scrollToItem(index - 1)
    } else {
      // with favorites the top is the part worth landing on, and it is off-screen above otherwise
      listState.scrollToItem(0)
    }
  }

  Surface(modifier = Modifier.fillMaxSize()) {
    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = { CoinPickerTopBar(onDismiss) }
    ) { contentPadding ->
      Column(modifier = Modifier.padding(contentPadding)) {
        SearchField(query = query, onQueryChange = { query = it })
        if (items.isEmpty()) {
          EmptyResults()
        } else {
          LazyColumn(
            state = listState,
            modifier = Modifier.selectableGroup()
          ) {
            items(items = items, key = { it.key() }) { item ->
              when (item) {
                is CoinListItem.Group -> {
                  CategoryHeader(stringResource(item.title))
                }
                is CoinListItem.Option -> {
                  CoinRow(
                    label = item.coin.coinName,
                    thumbnail = rememberCoinArt(thumbnails[item.coin.prefix] ?: 0),
                    selected = item.coin.prefix == selectedValue,
                    favorite = item.coin.prefix in favorites,
                    onClick = {
                      onSelect(item.coin.prefix)
                      onDismiss()
                    },
                    onToggleFavorite = { onToggleFavorite(item.coin.prefix) }
                  )
                }
                is CoinListItem.Custom -> {
                  CoinRow(
                    label = customName,
                    thumbnail = customArt,
                    selected = selectedValue == CustomCoin.PREFIX,
                    favorite = null,
                    onClick = {
                      onSelect(CustomCoin.PREFIX)
                      onDismiss()
                    }
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoinPickerTopBar(onDismiss: () -> Unit) {
  TopAppBar(
    title = { Text(stringResource(R.string.settings_item_coin_title)) },
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

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(start = Dimen.medium, end = Dimen.small, top = Dimen.small, bottom = Dimen.small),
    placeholder = { Text(stringResource(android.R.string.search_go)) },
    leadingIcon = { Icon(painter = painterResource(R.drawable.search), contentDescription = null) },
    trailingIcon = {
      if (query.isNotEmpty()) {
        IconButton(onClick = { onQueryChange("") }) {
          Icon(
            painter = painterResource(R.drawable.close),
            contentDescription = stringResource(R.string.clear)
          )
        }
      }
    },
    singleLine = true
  )
}

@Composable
private fun CoinRow(
  label: String,
  thumbnail: ImageBitmap?,
  selected: Boolean,
  favorite: Boolean?,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit = { }
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = Dimen.medium, vertical = Dimen.small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Dimen.medium)
  ) {
    CoinThumbnail(thumbnail)

    Text(
      text = label,
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.bodyLarge
    )
    if (selected) {
      Icon(
        painter = painterResource(R.drawable.check),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
      )
    }
    if (favorite != null) {
      // an IconButton rather than a clickable Icon: the row is already selectable, so the star needs
      // its own semantics node and its own 48dp target instead of being swallowed by the row
      IconButton(onClick = onToggleFavorite) {
        Icon(
          painter = painterResource(if (favorite) R.drawable.star_filled else R.drawable.star),
          contentDescription =
            stringResource(
              if (favorite) R.string.settings_item_coin_favorite_remove else R.string.settings_item_coin_favorite_add,
              label
            ),
          tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      // the custom coin is pinned rather than starred, so there is nothing here to toggle -- but the
      // slot still has to be filled, or the check mark slides across into where the star would be.
      // A Box rather than a disabled IconButton: this is a state, and nothing should invite a tap.
      Box(modifier = Modifier.size(Dimen.iconButton), contentAlignment = Alignment.Center) {
        Icon(
          painter = painterResource(R.drawable.pin),
          contentDescription = stringResource(R.string.settings_item_coin_favorite_pinned, label),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

// a shipped coin's artwork, decoded once per row rather than on every recomposition of it
@Composable
private fun rememberCoinArt(@DrawableRes id: Int): ImageBitmap? {
  val resources = LocalResources.current
  val targetPx = with(LocalDensity.current) { Dimen.coinThumbnail.roundToPx() }
  return remember(id, targetPx) { decodeThumbnail(resources, id, targetPx) }
}

@Composable
private fun CoinThumbnail(bitmap: ImageBitmap?) {
  Box(
    modifier = Modifier.size(Dimen.coinThumbnail),
    contentAlignment = Alignment.Center
  ) {
    if (bitmap != null) {
      Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      // "Random Coin" has no artwork of its own -- it rerolls a real coin on every flip -- so its
      // coin is drawn instead. The ring gives the ? something to sit on, at the diameter the real
      // thumbnails either side of it fill.
      val outline = MaterialTheme.colorScheme.primary
      val stroke = with(LocalDensity.current) { Dimen.coinOutline.toPx() }
      Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(color = outline, radius = size.minDimension / 2f - stroke, style = Stroke(stroke))
      }
      Text(
        text = "?",
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleLarge
      )
    }
  }
}

@Composable
private fun EmptyResults() {
  Text(
    text = stringResource(R.string.settings_item_coin_empty),
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(Dimen.large),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}

/**
 * Resolves each coin's artwork once, rather than on every recomposition of a row.
 *
 * Every state quarter shares a single Washington obverse and every euro shares a single common
 * reverse; those shared faces are stored as `<bitmap>` aliases in XML. So the side that actually
 * identifies a coin is whichever one is its own raster asset.
 */
@SuppressLint("DiscouragedApi") // the coin drawables are only addressable by name, as in AnimationHelper
@Composable
private fun rememberThumbnails(): Map<String, Int> {
  val resources = LocalResources.current
  val packageName = LocalContext.current.packageName
  // unkeyed on purpose: the catalog is fixed at compile time, and getIdentifier is slow enough
  // that 160-odd repeat lookups would show
  return remember {
    CoinType.entries.associate { coin ->
      val id = resources.rasterId("${coin.prefix}_heads", packageName) ?: resources.rasterId("${coin.prefix}_tails", packageName)
      coin.prefix to (id ?: 0)
    }
  }
}

/** The id of the named drawable, or null when it is missing or is an XML alias rather than an image. */
@SuppressLint("DiscouragedApi")
private fun Resources.rasterId(name: String, packageName: String): Int? {
  val id = getIdentifier(name, "drawable", packageName)
  if (id == 0) return null
  val resolved = TypedValue()
  getValue(id, resolved, true)
  return id.takeUnless { resolved.string?.toString()?.endsWith(".xml") == true }
}

/**
 * Decodes coin art down to roughly the size it is drawn at. The assets are 390px square, so
 * decoding them full size would cost ~600KB per visible row for a 40dp slot.
 */
private fun decodeThumbnail(resources: Resources, @DrawableRes id: Int, targetPx: Int): ImageBitmap? {
  if (id == 0 || targetPx <= 0) return null
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeResource(resources, id, bounds)
  val options =
    BitmapFactory.Options().apply {
      inSampleSize = (bounds.outWidth / targetPx).coerceAtLeast(1)
    }
  return BitmapFactory.decodeResource(resources, id, options)?.asImageBitmap()
}

@VisibleForTesting
internal fun CoinListItem.key(): String =
  when (this) {
    is CoinListItem.Group -> {
      "group-$title"
    }
    is CoinListItem.Option -> {
      if (inFavoritesSection) "favorite-${coin.prefix}" else "coin-${coin.prefix}"
    }
    is CoinListItem.Custom -> {
      if (inFavoritesSection) "favorite-${CustomCoin.PREFIX}" else "coin-${CustomCoin.PREFIX}"
    }
  }

/**
 * Filters by [query] and interleaves a header ahead of each group that still has matches.
 *
 * [customCoin] is the user's coin: its localized name when both faces are set, and null otherwise,
 * so one parameter carries both whether to list it and what to match a search against.
 */
@VisibleForTesting
internal fun buildCoinList(
  coins: List<CoinType>,
  favorites: Set<String>,
  query: String,
  customCoin: String? = null
): List<CoinListItem> {
  val trimmed = query.trim()
  val matches = coins.filter { query.isBlank() || it.coinName.contains(trimmed, ignoreCase = true) }
  val customMatches = customCoin != null && (query.isBlank() || customCoin.contains(trimmed, ignoreCase = true))

  return buildList {
    // starred coins are repeated at the top rather than moved out, so the origin groups keep no
    // holes. Only while browsing though: a search has already narrowed the list, and showing one
    // hit under two headers reads as noise rather than as a shortcut.
    if (query.isBlank()) {
      val starred = matches.filter { it.prefix in favorites }
      // the custom coin is permanently starred, and that lives here rather than in Setting.FAVORITES:
      // a stored star could be toggled off from the row, and would outlive the artwork it names
      if (starred.isNotEmpty() || customMatches) {
        add(CoinListItem.Group(R.string.settings_item_coin_group_favorites))
        if (customMatches) add(CoinListItem.Custom(inFavoritesSection = true))
        starred.forEach { add(CoinListItem.Option(it, inFavoritesSection = true)) }
      }
    }

    var currentGroup: CoinGroup? = null
    var customPlaced = false
    matches.forEach { coin ->
      if (coin.group != currentGroup) {
        add(CoinListItem.Group(coin.group.label))
        currentGroup = coin.group
      }
      // ahead of RANDOM, so the sentinel stays last on screen as well as in the enum
      if (customMatches && !customPlaced && coin == CoinType.RANDOM) {
        add(CoinListItem.Custom())
        customPlaced = true
      }
      add(CoinListItem.Option(coin))
    }

    // a search can filter RANDOM out from under it, so it still needs somewhere to land
    if (customMatches && !customPlaced) {
      if (currentGroup != CoinGroup.OTHER) add(CoinListItem.Group(CoinGroup.OTHER.label))
      add(CoinListItem.Custom())
    }
  }
}

@PreviewLightDark
@Composable
fun CoinPickerPreview() {
  AppTheme {
    CoinPickerContent(
      selectedValue = CoinType.GEORGE_WASHINGTON.prefix,
      favorites = setOf("gw", "jfk"),
      customCoinReady = true,
      onSelect = { },
      onToggleFavorite = { },
      onDismiss = { }
    )
  }
}