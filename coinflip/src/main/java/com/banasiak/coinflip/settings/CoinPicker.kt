package com.banasiak.coinflip.settings

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.banasiak.coinflip.R
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen

// the group keys in R.array.coins_groups, which runs parallel to coins_values
internal val COIN_GROUP_LABELS =
  mapOf(
    "us" to R.string.settings_item_coin_group_us,
    "canada" to R.string.settings_item_coin_group_canada,
    "euro" to R.string.settings_item_coin_group_euro,
    "other" to R.string.settings_item_coin_group_other
  )

internal sealed interface CoinListItem {
  data class Group(@param:StringRes val title: Int) : CoinListItem

  /**
   * [inFavoritesSection] marks the copy rendered at the top. A starred coin appears twice, and
   * LazyColumn keys have to differ or it throws on the duplicate.
   */
  data class Option(val label: String, val value: String, val inFavoritesSection: Boolean = false) : CoinListItem
}

/**
 * A full-screen picker for the 80-odd coins. The list is long enough that a plain modal list is
 * unusable, so this shows what each coin actually looks like, groups them by origin, and offers
 * a search field.
 */
@Composable
fun CoinPicker(
  entries: Array<String>,
  values: Array<String>,
  groups: Array<String>,
  selectedValue: String,
  favorites: Set<String>,
  onSelect: (String) -> Unit,
  onToggleFavorite: (String) -> Unit,
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    // a full-screen dialog, so it owns the whole window and draws edge to edge like a destination
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
  ) {
    CoinPickerContent(entries, values, groups, selectedValue, favorites, onSelect, onToggleFavorite, onDismiss)
  }
}

@Composable
private fun CoinPickerContent(
  entries: Array<String>,
  values: Array<String>,
  groups: Array<String>,
  selectedValue: String,
  favorites: Set<String>,
  onSelect: (String) -> Unit,
  onToggleFavorite: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var query by rememberSaveable { mutableStateOf("") }
  // keyed on the query alone: stringArrayResource hands back a fresh array on every composition,
  // so an array key would compare unequal every time and never memoize anything
  val items = remember(query, favorites) { buildCoinList(entries, values, groups, favorites, query) }
  val thumbnails = rememberThumbnails(values)
  val listState = rememberLazyListState()

  // Keyed on the query so editing or clearing the search lands on the top of the new results
  // instead of wherever the old list happened to sit. Deliberately not keyed on favorites: starring
  // a coin rebuilds the list too, and the row under the user's finger must not jump.
  LaunchedEffect(query) {
    if (query.isBlank() && favorites.isEmpty()) {
      // nothing pinned, so open on the coin that's already selected rather than at the top of a
      // very long list, keeping the row above it on screen so the group header isn't scrolled away
      val index = items.indexOfFirst { it is CoinListItem.Option && it.value == selectedValue }
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
                    label = item.label,
                    thumbnail = thumbnails[item.value] ?: 0,
                    selected = item.value == selectedValue,
                    favorite = item.value in favorites,
                    onClick = {
                      onSelect(item.value)
                      onDismiss()
                    },
                    onToggleFavorite = { onToggleFavorite(item.value) }
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
  @DrawableRes thumbnail: Int,
  selected: Boolean,
  favorite: Boolean,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit
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
  }
}

@Composable
private fun CoinThumbnail(@DrawableRes thumbnail: Int) {
  val resources = LocalResources.current
  val targetPx = with(LocalDensity.current) { Dimen.coinThumbnail.roundToPx() }
  val bitmap = remember(thumbnail, targetPx) { decodeThumbnail(resources, thumbnail, targetPx) }

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
      // "Random Coin" has no artwork of its own -- it rerolls a real coin on every flip
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
private fun rememberThumbnails(values: Array<String>): Map<String, Int> {
  val resources = LocalResources.current
  val packageName = LocalContext.current.packageName
  // unkeyed on purpose: these come from a static resource array that cannot change while the
  // picker is open, and getIdentifier is slow enough that 160-odd repeat lookups would show
  return remember {
    values.associateWith { value ->
      resources.rasterId("${value}_heads", packageName)
        ?: resources.rasterId("${value}_tails", packageName)
        ?: 0
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
    is CoinListItem.Group -> "group-$title"
    is CoinListItem.Option -> if (inFavoritesSection) "favorite-$value" else "coin-$value"
  }

/** Filters by [query] and interleaves a header ahead of each group that still has matches. */
@VisibleForTesting
internal fun buildCoinList(
  entries: Array<String>,
  values: Array<String>,
  groups: Array<String>,
  favorites: Set<String>,
  query: String
): List<CoinListItem> {
  val matches = mutableListOf<Pair<Int, CoinListItem.Option>>()

  values.forEachIndexed { index, value ->
    val label = entries.getOrNull(index) ?: return@forEachIndexed
    val group = COIN_GROUP_LABELS[groups.getOrNull(index)] ?: R.string.settings_item_coin_group_other
    if (query.isBlank() || label.contains(query.trim(), ignoreCase = true)) {
      matches += group to CoinListItem.Option(label, value)
    }
  }

  return buildList {
    // starred coins are repeated at the top rather than moved out, so the origin groups keep no
    // holes. Only while browsing though: a search has already narrowed the list, and showing one
    // hit under two headers reads as noise rather than as a shortcut.
    if (query.isBlank()) {
      val starred = matches.map { it.second }.filter { it.value in favorites }
      if (starred.isNotEmpty()) {
        add(CoinListItem.Group(R.string.settings_item_coin_group_favorites))
        starred.forEach { add(it.copy(inFavoritesSection = true)) }
      }
    }

    var currentGroup: Int? = null
    matches.forEach { (itemGroup, option) ->
      if (itemGroup != currentGroup) {
        add(CoinListItem.Group(itemGroup))
        currentGroup = itemGroup
      }
      add(option)
    }
  }
}

@PreviewLightDark
@Composable
fun CoinPickerPreview() {
  AppTheme {
    CoinPickerContent(
      entries = stringArrayResource(R.array.coins),
      values = stringArrayResource(R.array.coins_values),
      groups = stringArrayResource(R.array.coins_groups),
      selectedValue = "gw",
      favorites = setOf("gw", "jfk"),
      onSelect = { },
      onToggleFavorite = { },
      onDismiss = { }
    )
  }
}