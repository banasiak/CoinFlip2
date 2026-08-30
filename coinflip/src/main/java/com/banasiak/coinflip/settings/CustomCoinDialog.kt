package com.banasiak.coinflip.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.CustomCoin
import com.banasiak.coinflip.ui.theme.AppTheme
import com.banasiak.coinflip.ui.theme.Dimen
import com.banasiak.coinflip.ui.theme.faceColor

/**
 * Sets the two images behind the custom coin, and is the only way into the photo picker.
 *
 * A coin with only one face set exists here and nowhere else -- it does not appear in the picker at
 * all until both are in place.
 */
@Composable
fun CustomCoinDialog(
  faces: Set<CustomCoin.Face>,
  revision: Long,
  rimEnabled: Boolean,
  loadThumbnail: (CustomCoin.Face, Int) -> ImageBitmap?,
  onPickImage: (CustomCoin.Face) -> Unit,
  onDelete: () -> Unit,
  onRimChange: (Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.settings_item_custom_coin_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(Dimen.small)) {
        CustomCoin.Face.entries.forEach { face ->
          CustomFaceRow(
            face = face,
            isSet = face in faces,
            revision = revision,
            loadThumbnail = loadThumbnail,
            onPick = { onPickImage(face) }
          )
        }
      }
    },
    // the whole bottom row goes in the confirm slot: the border toggle belongs down here beside OK
    // rather than as a third face-sized row, and this is the only way to reach the left of it
    confirmButton = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        BorderToggle(
          enabled = rimEnabled,
          // weight so a long translation truncates rather than pushing OK off the dialog
          modifier = Modifier.weight(1f, fill = false),
          onChange = onRimChange
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          TextButton(
            // nothing to delete is not an error worth a snackbar, so the button simply goes quiet
            enabled = faces.isNotEmpty(),
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
          ) {
            Text(stringResource(R.string.settings_item_custom_coin_delete))
          }
          TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        }
      }
    }
  )
}

/**
 * Rings the faces, or leaves them alone. A photograph of a real coin is already a coin, and a
 * themed ring around it looks like a mistake; everything else needs the ring so it looks like a coin. But, the decision is up to the user.
 */
@Composable
private fun BorderToggle(enabled: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
  Row(
    // toggleable rather than a bare Switch, so a screen reader gets one node with a state and a
    // label instead of an unlabelled control beside some text
    modifier = modifier.toggleable(value = enabled, role = Role.Switch, onValueChange = onChange),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Dimen.small)
  ) {
    Switch(checked = enabled, onCheckedChange = null)
    Text(
      text = stringResource(R.string.settings_item_custom_coin_border),
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 1
    )
  }
}

@Composable
private fun CustomFaceRow(
  face: CustomCoin.Face,
  isSet: Boolean,
  revision: Long,
  loadThumbnail: (CustomCoin.Face, Int) -> ImageBitmap?,
  onPick: () -> Unit
) {
  val targetPx = with(LocalDensity.current) { Dimen.coinThumbnail.roundToPx() }
  // keyed on the revision as well as isSet: replacing an image leaves the face set either way, and
  // this dialog stays composed underneath the photo picker and the crop, so a cache keyed on
  // anything that survives that round trip goes on drawing the picture it started with
  val thumbnail = remember(face, isSet, revision, targetPx) { if (isSet) loadThumbnail(face, targetPx) else null }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Dimen.small)
  ) {
    Box(modifier = Modifier.size(Dimen.coinThumbnail), contentAlignment = Alignment.Center) {
      if (thumbnail != null) {
        Image(bitmap = thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize())
      } else {
        // an outline rather than the picker's "?", which already means the random coin
        val outline = MaterialTheme.colorScheme.outlineVariant
        val stroke = with(LocalDensity.current) { Dimen.coinOutline.toPx() }
        Canvas(modifier = Modifier.fillMaxSize()) {
          drawCircle(color = outline, radius = size.minDimension / 2f - stroke, style = Stroke(stroke))
        }
      }
    }

    Text(
      text = stringResource(face.label),
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.bodyLarge,
      color = face.faceColor()
    )

    TextButton(onClick = onPick) {
      Text(
        stringResource(
          if (isSet) R.string.settings_item_custom_coin_replace else R.string.settings_item_custom_coin_choose
        )
      )
    }
  }
}

@PreviewLightDark
@Composable
fun CustomCoinDialogPreview() {
  AppTheme {
    CustomCoinDialog(
      faces = setOf(CustomCoin.Face.HEADS),
      revision = 0,
      rimEnabled = true,
      loadThumbnail = { _, _ -> null },
      onPickImage = { },
      onDelete = { },
      onRimChange = { },
      onDismiss = { }
    )
  }
}