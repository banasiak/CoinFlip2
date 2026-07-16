package com.banasiak.coinflip.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.extensions.formatNumber
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen

private enum class OpenDialog { NONE, COIN, FORCE, HEADS, TAILS, DIAGNOSTICS }

@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onEnableRestartOnBack: () -> Unit = { }
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
            val result =
              snackbarHostState.showSnackbar(
                message = resources.getString(effect.message),
                actionLabel = effect.actionLabel?.let { resources.getString(it) }
              )
            if (result == SnackbarResult.ActionPerformed && effect.action != null) {
              viewModel.postAction(effect.action)
            }
          }
          SettingsEffect.EnableRestartOnBack -> onEnableRestartOnBack()
        }
      }
    }
  }

  SettingsView(state, viewModel::postAction, snackbarHostState)
}

@Composable
fun SettingsView(
  state: SettingsState,
  postAction: (SettingsAction) -> Unit = { },
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
  // saveable so an open dialog survives configuration change and process death
  var openDialog by rememberSaveable { mutableStateOf(OpenDialog.NONE) }

  val coinEntries = stringArrayResource(R.array.coins)
  val coinValues = stringArrayResource(R.array.coins_values)
  val forceEntries = stringArrayResource(R.array.force)
  val forceValues = stringArrayResource(R.array.force_values)
  val headsDefault = stringResource(R.string.heads)
  val tailsDefault = stringResource(R.string.tails)

  AppTheme(dynamicColor = state.dynamic) {
    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { contentPadding ->
      Column(
        modifier =
          Modifier
            .padding(contentPadding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
      ) {
        // ----- Basic -----
        CategoryHeader(stringResource(R.string.settings_header_basic_title))
        PreferenceRow(
          title = stringResource(R.string.settings_item_coin_title),
          summary = coinEntries.getOrNull(coinValues.indexOf(state.coin)),
          onClick = { openDialog = OpenDialog.COIN }
        )
        SwitchPreference(
          title = stringResource(R.string.settings_item_animation_title),
          summary = stringResource(R.string.settings_item_animation_summary),
          checked = state.animate,
          onCheckedChange = { postAction(SettingsAction.SetAnimate(it)) }
        )
        SwitchPreference(
          title = stringResource(R.string.settings_item_shake_title),
          summary = stringResource(R.string.settings_item_shake_summary),
          checked = state.shake,
          onCheckedChange = { postAction(SettingsAction.SetShake(it)) }
        )
        SwitchPreference(
          title = stringResource(R.string.settings_item_sound_title),
          summary = stringResource(R.string.settings_item_sound_summary),
          checked = state.sound,
          onCheckedChange = { postAction(SettingsAction.SetSound(it)) }
        )
        SwitchPreference(
          title = stringResource(R.string.settings_item_text_title),
          summary = stringResource(R.string.settings_item_text_summary),
          checked = state.text,
          onCheckedChange = { postAction(SettingsAction.SetText(it)) }
        )
        SwitchPreference(
          title = stringResource(R.string.settings_item_vibrate_title),
          summary = stringResource(R.string.settings_item_vibrate_summary),
          checked = state.vibrate,
          onCheckedChange = { postAction(SettingsAction.SetVibrate(it)) }
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
        PreferenceRow(
          title = stringResource(R.string.settings_item_reset_stats_title),
          summary = null,
          onClick = { postAction(SettingsAction.ResetStats) }
        )

        // ----- Advanced -----
        CategoryHeader(stringResource(R.string.settings_header_advanced_title))
        PreferenceRow(
          title = stringResource(R.string.settings_item_custom_heads_title),
          summary = state.customHeads ?: headsDefault,
          onClick = { openDialog = OpenDialog.HEADS }
        )
        PreferenceRow(
          title = stringResource(R.string.settings_item_custom_tails_title),
          summary = state.customTails ?: tailsDefault,
          onClick = { openDialog = OpenDialog.TAILS }
        )
        PreferenceRow(
          title = stringResource(R.string.settings_item_diagnostics_title),
          summary = "${state.diagnostics.formatNumber()} ${stringResource(R.string.settings_item_diagnostics_summary)}",
          onClick = { openDialog = OpenDialog.DIAGNOSTICS }
        )
        SwitchPreference(
          title = stringResource(R.string.settings_item_dynamic_colors_title),
          summary = stringResource(R.string.settings_item_dynamic_colors_summary),
          checked = state.dynamic,
          onCheckedChange = { postAction(SettingsAction.SetDynamic(it)) }
        )
        SwitchPreference(
          title = stringResource(R.string.settings_item_secure_random_title),
          summary = stringResource(R.string.settings_item_secure_random_summary),
          checked = state.secureRandom,
          onCheckedChange = { postAction(SettingsAction.SetSecureRandom(it)) }
        )
        PreferenceRow(
          title = stringResource(R.string.settings_item_force_title),
          summary = stringResource(R.string.settings_item_force_summary),
          // dependency: shake
          enabled = state.shake,
          onClick = { openDialog = OpenDialog.FORCE }
        )
      }
    }
  }

  when (openDialog) {
    OpenDialog.COIN ->
      SingleChoiceDialog(
        title = stringResource(R.string.settings_item_coin_title),
        entries = coinEntries,
        values = coinValues,
        selectedValue = state.coin,
        onSelect = { postAction(SettingsAction.SetCoin(it)) },
        onDismiss = { openDialog = OpenDialog.NONE }
      )
    OpenDialog.FORCE ->
      SingleChoiceDialog(
        title = stringResource(R.string.settings_item_force_title),
        entries = forceEntries,
        values = forceValues,
        selectedValue = state.force,
        onSelect = { postAction(SettingsAction.SetForce(it)) },
        onDismiss = { openDialog = OpenDialog.NONE }
      )
    OpenDialog.HEADS ->
      TextInputDialog(
        title = stringResource(R.string.settings_item_custom_heads_title),
        initialValue = state.customHeads ?: headsDefault,
        onConfirm = { value -> postAction(SettingsAction.SetCustomHeads(value.ifBlank { headsDefault })) },
        onDismiss = { openDialog = OpenDialog.NONE }
      )
    OpenDialog.TAILS ->
      TextInputDialog(
        title = stringResource(R.string.settings_item_custom_tails_title),
        initialValue = state.customTails ?: tailsDefault,
        onConfirm = { value -> postAction(SettingsAction.SetCustomTails(value.ifBlank { tailsDefault })) },
        onDismiss = { openDialog = OpenDialog.NONE }
      )
    OpenDialog.DIAGNOSTICS ->
      TextInputDialog(
        title = stringResource(R.string.settings_item_diagnostics_title),
        message = stringResource(R.string.settings_item_diagnostics_dialog),
        initialValue = state.diagnostics,
        numeric = true,
        onConfirm = { value -> postAction(SettingsAction.SetDiagnostics(value)) },
        onDismiss = { openDialog = OpenDialog.NONE }
      )
    OpenDialog.NONE -> { }
  }
}

@Composable
private fun CategoryHeader(title: String) {
  Text(
    text = title,
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(start = Dimen.medium, end = Dimen.medium, top = Dimen.medium, bottom = Dimen.small),
    style = MaterialTheme.typography.titleSmall,
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
        .clickable(enabled = enabled) { onCheckedChange(!checked) }
        .padding(horizontal = Dimen.medium, vertical = Dimen.medium),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Dimen.medium)
  ) {
    TitleAndSummary(Modifier.weight(1f), title, summary, enabled)
    Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
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
        .clickable(enabled = enabled) { onClick() }
        .padding(horizontal = Dimen.medium, vertical = Dimen.medium),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TitleAndSummary(Modifier.weight(1f), title, summary, enabled)
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
private fun SingleChoiceDialog(
  title: String,
  entries: Array<String>,
  values: Array<String>,
  selectedValue: String,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      LazyColumn(modifier = Modifier.selectableGroup()) {
        items(entries.size) { index ->
          val value = values[index]
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .selectable(
                  selected = value == selectedValue,
                  onClick = {
                    onSelect(value)
                    onDismiss()
                  }
                )
                .padding(vertical = Dimen.small),
            verticalAlignment = Alignment.CenterVertically
          ) {
            RadioButton(selected = value == selectedValue, onClick = null)
            Text(
              text = entries[index],
              modifier = Modifier.padding(start = Dimen.medium),
              style = MaterialTheme.typography.bodyLarge
            )
          }
        }
      }
    },
    confirmButton = { },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
    }
  )
}

@Composable
private fun TextInputDialog(
  title: String,
  initialValue: String,
  message: String? = null,
  numeric: Boolean = false,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var value by rememberSaveable { mutableStateOf(initialValue) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column {
        if (!message.isNullOrEmpty()) {
          Text(text = message, modifier = Modifier.padding(bottom = Dimen.small))
        }
        OutlinedTextField(
          value = value,
          // the legacy EditTextPreference enforced TYPE_CLASS_NUMBER, so keep numeric input digits-only
          onValueChange = { value = if (numeric) it.filter { char -> char.isDigit() } else it },
          singleLine = true,
          keyboardOptions =
            KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text)
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onConfirm(value)
          onDismiss()
        }
      ) {
        Text(stringResource(android.R.string.ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
    }
  )
}

@PreviewLightDark
@Composable
fun SettingsViewPreview() {
  SettingsView(state = SettingsState())
}