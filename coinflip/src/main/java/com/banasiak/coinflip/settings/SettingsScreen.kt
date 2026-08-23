package com.banasiak.coinflip.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.extensions.formatNumber
import com.banasiak.coinflip.ui.TextInputDialog
import com.banasiak.coinflip.ui.rememberEditableValue
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen
import com.banasiak.coinflip.ui.theme.Type
import kotlinx.coroutines.launch

private enum class OpenDialog { NONE, COIN, CUSTOM_TEXT }

@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onEnableRestartOnBack: () -> Unit = { },
  onNavigateBack: () -> Unit = { }
) {
  val state by viewModel.stateFlow.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val lifecycleOwner = LocalLifecycleOwner.current
  val resources = LocalResources.current

  LaunchedEffect(viewModel, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      viewModel.effectFlow.collect { effect ->
        when (effect) {
          is SettingsEffect.ShowSnackbar -> {
            // launch so the collector isn't suspended while the snackbar is visible -- effects
            // emitted in the meantime would overflow the buffer and be dropped
            launch {
              val result =
                snackbarHostState.showSnackbar(
                  message = resources.getString(effect.message),
                  actionLabel = effect.actionLabel?.let { resources.getString(it) },
                  // with an actionLabel the default duration is Indefinite; match the legacy Snackbar.LENGTH_LONG
                  duration = SnackbarDuration.Long
                )
              if (result == SnackbarResult.ActionPerformed && effect.action != null) {
                viewModel.postAction(effect.action)
              }
            }
          }
          SettingsEffect.EnableRestartOnBack -> {
            onEnableRestartOnBack()
          }
        }
      }
    }
  }

  SettingsView(state, viewModel::postAction, snackbarHostState, onNavigateBack)
}

@Composable
fun SettingsView(
  state: SettingsState,
  postAction: (SettingsAction) -> Unit = { },
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
  onNavigateBack: () -> Unit = { }
) {
  // saveable so an open dialog survives configuration change and process death
  var openDialog by rememberSaveable { mutableStateOf(OpenDialog.NONE) }

  val coinEntries = stringArrayResource(R.array.coins)
  val coinValues = stringArrayResource(R.array.coins_values)
  val coinGroups = stringArrayResource(R.array.coins_groups)
  val forceEntries = stringArrayResource(R.array.force)
  val forceValues = stringArrayResource(R.array.force_values)
  val headsDefault = stringResource(R.string.heads)
  val tailsDefault = stringResource(R.string.tails)

  AppTheme(dynamicColor = state.dynamic) {
    Scaffold(
      // the display cutout sits alongside the content in landscape, not above it
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = { SettingsTopBar(onNavigateBack) },
      snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { contentPadding ->
      Column(
        modifier =
          Modifier
            .padding(contentPadding)
            .fillMaxSize()
            // scroll the full width, but center the rows in a readable column so a switch never
            // ends up a screen away from the label it belongs to
            .verticalScroll(rememberScrollState())
            .wrapContentWidth()
            .widthIn(max = Dimen.maxContent)
      ) {
        // ----- Coin -----
        CategoryHeader(stringResource(R.string.settings_header_coin_title))
        PreferenceRow(
          title = stringResource(R.string.settings_item_coin_title),
          summary = coinEntries.getOrNull(coinValues.indexOf(state.coin)),
          onClick = { openDialog = OpenDialog.COIN }
        )
        SwitchPreference(
          title = stringResource(R.string.settings_item_text_title),
          summary = stringResource(R.string.settings_item_text_summary),
          checked = state.text,
          onCheckedChange = { postAction(SettingsAction.SetText(it)) }
        )
        // one row for both labels: naming the two faces is a single decision, and they are drawn
        // only by the result text, so this sits with the switch that turns it on
        PreferenceRow(
          title = stringResource(R.string.settings_item_custom_text_title),
          summary = "${state.customHeads ?: headsDefault} / ${state.customTails ?: tailsDefault}",
          // dependency: text
          enabled = state.text,
          onClick = { openDialog = OpenDialog.CUSTOM_TEXT }
        )

        // ----- Flip: the switches run together, with the one non-switch control after them -----
        CategoryHeader(stringResource(R.string.settings_header_flip_title))
        SwitchPreference(
          title = stringResource(R.string.settings_item_animation_title),
          summary = stringResource(R.string.settings_item_animation_summary),
          checked = state.animate,
          onCheckedChange = { postAction(SettingsAction.SetAnimate(it)) }
        )
        SwitchPreference(
          title = stringResource(R.string.settings_item_sound_title),
          summary = stringResource(R.string.settings_item_sound_summary),
          checked = state.sound,
          onCheckedChange = { postAction(SettingsAction.SetSound(it)) }
        )
        SwitchPreference(
          title = stringResource(R.string.settings_item_vibrate_title),
          summary = stringResource(R.string.settings_item_vibrate_summary),
          checked = state.vibrate,
          onCheckedChange = { postAction(SettingsAction.SetVibrate(it)) }
        )
        SwitchPreference(
          title = stringResource(R.string.settings_item_shake_title),
          summary = stringResource(R.string.settings_item_shake_summary),
          checked = state.shake,
          onCheckedChange = { postAction(SettingsAction.SetShake(it)) }
        )
        // three choices are cheaper to show inline than to bury behind a dialog
        SegmentedPreference(
          title = stringResource(R.string.settings_item_force_title),
          summary = stringResource(R.string.settings_item_force_summary),
          entries = forceEntries,
          values = forceValues,
          selectedValue = state.force,
          // dependency: shake
          enabled = state.shake,
          onSelect = { postAction(SettingsAction.SetForce(it)) }
        )

        // ----- Statistics -----
        CategoryHeader(stringResource(R.string.settings_header_statistics_title))
        SwitchPreference(
          title = stringResource(R.string.settings_item_stats_title),
          summary = stringResource(R.string.settings_item_stats_summary),
          checked = state.stats,
          onCheckedChange = { postAction(SettingsAction.SetStats(it)) }
        )
        SwitchPreference(
          title = stringResource(R.string.settings_item_quick_reset_title),
          summary = stringResource(R.string.settings_item_quick_reset_summary),
          checked = state.quickReset,
          // dependency: stats
          enabled = state.stats,
          onCheckedChange = { postAction(SettingsAction.SetQuickReset(it)) }
        )
        Text(
          text = stringResource(R.string.settings_item_reset_stats_summary, state.flipCount.formatNumber()),
          modifier = Modifier.padding(start = Dimen.medium, end = Dimen.medium, top = Dimen.small),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // an action, not a preference -- it changes data instead of recording a choice
        DestructiveButton(
          title = stringResource(R.string.reset_stats),
          enabled = state.flipCount > 0L,
          onClick = { postAction(SettingsAction.ResetStats) }
        )

        // ----- Appearance -----
        CategoryHeader(stringResource(R.string.settings_header_appearance_title))
        SwitchPreference(
          title = stringResource(R.string.settings_item_dynamic_colors_title),
          summary = stringResource(R.string.settings_item_dynamic_colors_summary),
          checked = state.dynamic,
          onCheckedChange = { postAction(SettingsAction.SetDynamic(it)) }
        )

        // ----- Advanced -----
        CategoryHeader(stringResource(R.string.settings_header_advanced_title))
        SwitchPreference(
          title = stringResource(R.string.settings_item_secure_random_title),
          summary = stringResource(R.string.settings_item_secure_random_summary),
          checked = state.secureRandom,
          onCheckedChange = { postAction(SettingsAction.SetSecureRandom(it)) }
        )
        Spacer(Modifier.height(Dimen.large))
      }
    }

    // inside AppTheme: a dialog is composed into its own window, so it only inherits the app's
    // colors if it is emitted from within the theme
    when (openDialog) {
      OpenDialog.COIN -> {
        CoinPicker(
          entries = coinEntries,
          values = coinValues,
          groups = coinGroups,
          selectedValue = state.coin,
          onSelect = { postAction(SettingsAction.SetCoin(it)) },
          onDismiss = { openDialog = OpenDialog.NONE }
        )
      }
      OpenDialog.CUSTOM_TEXT -> {
        CustomTextDialog(
          initialHeads = state.customHeads,
          initialTails = state.customTails,
          headsDefault = headsDefault,
          tailsDefault = tailsDefault,
          onConfirm = { heads, tails ->
            postAction(SettingsAction.SetCustomHeads(heads))
            postAction(SettingsAction.SetCustomTails(tails))
          },
          onDismiss = { openDialog = OpenDialog.NONE }
        )
      }
      OpenDialog.NONE -> { }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onNavigateBack: () -> Unit) {
  TopAppBar(
    title = { Text(stringResource(R.string.settings_menu_title)) },
    navigationIcon = {
      IconButton(onClick = onNavigateBack) {
        Icon(
          painter = painterResource(R.drawable.arrow_back),
          contentDescription = stringResource(R.string.navigate_back)
        )
      }
    }
  )
}

/** Shared with the coin picker so section headers match wherever they appear. */
@Composable
internal fun CategoryHeader(title: String) {
  Text(
    text = title,
    modifier =
      Modifier
        .fillMaxWidth()
        // weighted towards the top so the header binds to the rows it introduces rather than
        // floating midway between two sections
        .padding(start = Dimen.medium, end = Dimen.medium, top = Dimen.large, bottom = Dimen.small),
    style = Type.settingsHeader,
    color = MaterialTheme.colorScheme.primary
  )
}

@Composable
private fun SwitchPreference(
  title: String,
  summary: String?,
  checked: Boolean,
  enabled: Boolean = true,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        // toggleable (not clickable) so accessibility services see a single switch with its state
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(horizontal = Dimen.medium, vertical = Dimen.medium),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Dimen.medium)
  ) {
    TitleAndSummary(Modifier.weight(1f), title, summary, enabled)
    // null onCheckedChange: the row handles interaction, so the thumb isn't a second focus target
    Switch(checked = checked, enabled = enabled, onCheckedChange = null)
  }
}

@Composable
private fun PreferenceRow(
  title: String,
  summary: String?,
  enabled: Boolean = true,
  onClick: () -> Unit
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(enabled = enabled, role = Role.Button) { onClick() }
        .padding(horizontal = Dimen.medium, vertical = Dimen.medium),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TitleAndSummary(Modifier.weight(1f), title, summary, enabled)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedPreference(
  title: String,
  summary: String?,
  entries: Array<String>,
  values: Array<String>,
  selectedValue: String,
  enabled: Boolean,
  onSelect: (String) -> Unit
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = Dimen.medium, vertical = Dimen.medium)
  ) {
    TitleAndSummary(Modifier.fillMaxWidth(), title, summary, enabled)
    SingleChoiceSegmentedButtonRow(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(top = Dimen.small)
    ) {
      values.forEachIndexed { index, value ->
        SegmentedButton(
          selected = value == selectedValue,
          enabled = enabled,
          onClick = { onSelect(value) },
          shape = SegmentedButtonDefaults.itemShape(index = index, count = values.size),
          // the Material default paints the active segment with secondaryContainer, which in this
          // theme is the deep red used for HEADS -- follow the switches on primary instead
          colors =
            SegmentedButtonDefaults.colors(
              activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
              activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              activeBorderColor = MaterialTheme.colorScheme.outline
            )
        ) {
          Text(entries.getOrNull(index) ?: value)
        }
      }
    }
  }
}

@Composable
private fun DestructiveButton(title: String, enabled: Boolean, onClick: () -> Unit) {
  val border =
    if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
  OutlinedButton(
    // sized to its label rather than the page -- a full-width red pill outshouts every row above it
    modifier = Modifier.padding(horizontal = Dimen.medium, vertical = Dimen.small),
    onClick = onClick,
    enabled = enabled,
    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    border = BorderStroke(1.dp, border)
  ) {
    Text(title)
  }
}

@Composable
private fun TitleAndSummary(modifier: Modifier, title: String, summary: String?, enabled: Boolean) {
  val contentColor =
    if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
  Column(modifier = modifier) {
    Text(text = title, style = MaterialTheme.typography.titleMedium, color = contentColor)
    if (!summary.isNullOrEmpty()) {
      Text(
        text = summary,
        style = MaterialTheme.typography.bodyMedium,
        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else contentColor
      )
    }
  }
}

@Composable
private fun CustomTextDialog(
  initialHeads: String?,
  initialTails: String?,
  headsDefault: String,
  tailsDefault: String,
  onConfirm: (String?, String?) -> Unit,
  onDismiss: () -> Unit
) {
  // an empty field means "no override", shown as the localized default in the placeholder. Storing
  // the default as a literal instead would freeze the label to whatever language set it, and leave
  // no way back -- these are the words the coin lands on, so they have to follow the locale.
  var heads by rememberEditableValue(initialHeads.orEmpty(), selectAll = true)
  var tails by rememberEditableValue(initialTails.orEmpty())

  TextInputDialog(
    title = stringResource(R.string.settings_item_custom_text_title),
    onConfirm = { onConfirm(heads.text.ifBlank { null }, tails.text.ifBlank { null }) },
    onDismiss = onDismiss
  ) { focusRequester ->
    OutlinedTextField(
      value = heads,
      modifier = Modifier.focusRequester(focusRequester),
      onValueChange = { heads = it },
      label = { Text(stringResource(R.string.heads)) },
      placeholder = { Text(headsDefault) },
      singleLine = true
    )
    OutlinedTextField(
      value = tails,
      onValueChange = { tails = it },
      label = { Text(stringResource(R.string.tails)) },
      placeholder = { Text(tailsDefault) },
      singleLine = true
    )
  }
}

@PreviewLightDark
@Composable
fun SettingsViewPreview() {
  SettingsView(state = SettingsState())
}