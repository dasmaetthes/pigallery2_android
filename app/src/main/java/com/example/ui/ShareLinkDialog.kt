package com.example.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.PiGalleryApi
import com.example.data.ShareTarget
import com.example.data.ShareTargetKind
import com.example.data.ShareValidity
import com.example.data.ShareValidityUnit
import com.example.util.rememberTvCapabilities

private fun ShareValidityUnit.labelResId(): Int = when (this) {
    ShareValidityUnit.MINUTES -> R.string.share_unit_minutes
    ShareValidityUnit.HOURS -> R.string.share_unit_hours
    ShareValidityUnit.DAYS -> R.string.share_unit_days
    ShareValidityUnit.MONTHS -> R.string.share_unit_months
    ShareValidityUnit.NONE -> R.string.share_unit_none
}

private fun ShareValidityUnit.defaultAmount(): String = when (this) {
    ShareValidityUnit.MINUTES -> "30"
    ShareValidityUnit.HOURS -> "24"
    ShareValidityUnit.DAYS -> "30"
    ShareValidityUnit.MONTHS -> "1"
    ShareValidityUnit.NONE -> ""
}

@Composable
fun ShareLinkDialog(
    target: ShareTarget,
    passwordProtected: Boolean,
    shareLinkState: ShareLinkUiState,
    onCreateShare: (
        includeSubfolders: Boolean,
        preventDownload: Boolean,
        validity: ShareValidity,
        password: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val tvCapabilities = rememberTvCapabilities()
    if (!tvCapabilities.allowShareLinks) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val context = LocalContext.current
    val isDirectoryTarget = target is ShareTarget.Directory
    val stateKey = remember(target) {
        when (target) {
            is ShareTarget.Directory -> "dir:${PiGalleryApi.cleanServerPath(target.path)}"
            is ShareTarget.Search -> "${target.kind.name}:${target.label}"
        }
    }
    val displayTargetName = when (target) {
        is ShareTarget.Directory -> {
            val normalizedPath = PiGalleryApi.cleanServerPath(target.path)
            normalizedPath.ifEmpty { stringResource(R.string.share_folder_root) }
        }
        is ShareTarget.Search -> target.label
    }
    val targetSubtitleResId = when (target) {
        is ShareTarget.Directory -> R.string.share_link_dialog_subtitle
        is ShareTarget.Search -> when (target.kind) {
            ShareTargetKind.SEARCH -> R.string.share_link_subtitle_search
            ShareTargetKind.ALBUM -> R.string.share_link_subtitle_album
            ShareTargetKind.PERSON -> R.string.share_link_subtitle_person
            ShareTargetKind.DIRECTORY -> R.string.share_link_dialog_subtitle
        }
    }
    val targetIcon = when (target) {
        is ShareTarget.Directory -> Icons.Default.Folder
        is ShareTarget.Search -> when (target.kind) {
            ShareTargetKind.SEARCH -> Icons.Default.Search
            ShareTargetKind.ALBUM -> Icons.Default.PhotoLibrary
            ShareTargetKind.PERSON -> Icons.Default.Person
            ShareTargetKind.DIRECTORY -> Icons.Default.Folder
        }
    }

    var includeSubfolders by rememberSaveable(stateKey) { mutableStateOf(true) }
    var preventDownload by rememberSaveable(stateKey) { mutableStateOf(false) }
    var validityAmountText by rememberSaveable(stateKey) { mutableStateOf("30") }
    var selectedUnitName by rememberSaveable(stateKey) { mutableStateOf(ShareValidityUnit.DAYS.name) }
    var unitMenuExpanded by remember { mutableStateOf(false) }
    var password by rememberSaveable(stateKey) { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val selectedUnit = remember(selectedUnitName) {
        ShareValidityUnit.entries.firstOrNull { it.name == selectedUnitName } ?: ShareValidityUnit.DAYS
    }
    val parsedAmount = validityAmountText.toIntOrNull()
    val isAmountInvalid = selectedUnit != ShareValidityUnit.NONE && (parsedAmount == null || parsedAmount <= 0)
    val isCreating = shareLinkState is ShareLinkUiState.Creating
    val canSubmit = !isCreating && !isAmountInvalid

    val scrollState = rememberScrollState()
    var wasCreating by remember { mutableStateOf(false) }

    LaunchedEffect(shareLinkState) {
        if (wasCreating && shareLinkState is ShareLinkUiState.Success) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
        wasCreating = shareLinkState is ShareLinkUiState.Creating
    }

    fun submitShareRequest() {
        if (!canSubmit) return
        val validity = if (selectedUnit == ShareValidityUnit.NONE) {
            ShareValidity(amount = 0, unit = ShareValidityUnit.NONE)
        } else {
            ShareValidity(amount = (parsedAmount ?: 1).coerceAtLeast(1), unit = selectedUnit)
        }
        onCreateShare(
            includeSubfolders,
            preventDownload,
            validity,
            password.ifBlank { null }
        )
    }

    fun copyUrlToClipboard(url: String, customToastResId: Int = R.string.share_link_copied) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("PiGallery2 Share Link", url))
        Toast.makeText(context, context.getString(customToastResId), Toast.LENGTH_SHORT).show()
    }

    fun launchShareIntent(url: String) {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            val chooser = Intent.createChooser(sendIntent, context.getString(R.string.share_link_via)).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            copyUrlToClipboard(url, R.string.share_no_app_fallback_copied)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .heightIn(max = 680.dp)
                .testTag("share_link_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.create_share_link),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(targetSubtitleResId),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("share_dialog_close_button")
                            .tvFocus(shape = RoundedCornerShape(24.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }

                if (isCreating) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_dialog_progress_bar")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Target indicator (folder / search / album / person)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = targetIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = displayTargetName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("share_target_text")
                            )
                        }
                    }

                    // Include subfolders toggle (only relevant when sharing a directory)
                    if (isDirectoryTarget) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.share_include_subfolders),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            )
                            Switch(
                                checked = includeSubfolders,
                                onCheckedChange = { includeSubfolders = it },
                                enabled = !isCreating,
                                modifier = Modifier.testTag("share_include_subfolders_switch")
                            )
                        }
                    }

                    // Prevent downloading toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.share_prevent_download),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.share_prevent_download_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preventDownload,
                            onCheckedChange = { preventDownload = it },
                            enabled = !isCreating,
                            modifier = Modifier.testTag("share_prevent_download_switch")
                        )
                    }

                    // Expiry duration (number input + unit selector: Minutes, Hours, Days, Months, None)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.share_validity_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            OutlinedTextField(
                                value = if (selectedUnit == ShareValidityUnit.NONE) "" else validityAmountText,
                                onValueChange = { input ->
                                    validityAmountText = input.filter { it.isDigit() }.take(4)
                                },
                                label = { Text(stringResource(R.string.share_validity_amount_label)) },
                                placeholder = {
                                    Text(
                                        text = if (selectedUnit == ShareValidityUnit.NONE) "∞" else "30"
                                    )
                                },
                                singleLine = true,
                                enabled = !isCreating && selectedUnit != ShareValidityUnit.NONE,
                                isError = isAmountInvalid,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = if (passwordProtected) ImeAction.Next else ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { submitShareRequest() }
                                ),
                                modifier = Modifier
                                    .weight(0.42f)
                                    .testTag("share_validity_amount_input")
                            )

                            Box(
                                modifier = Modifier
                                    .weight(0.58f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable(enabled = !isCreating) {
                                            unitMenuExpanded = true
                                        }
                                        .testTag("share_validity_unit_dropdown")
                                        .tvFocus(shape = RoundedCornerShape(4.dp))
                                ) {
                                    OutlinedTextField(
                                        value = stringResource(selectedUnit.labelResId()),
                                        onValueChange = {},
                                        readOnly = true,
                                        enabled = false,
                                        singleLine = true,
                                        label = { Text(stringResource(R.string.share_validity_unit_label)) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null
                                            )
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            disabledTextColor = if (isCreating) {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            disabledBorderColor = if (unitMenuExpanded) {
                                                MaterialTheme.colorScheme.primary
                                            } else if (isCreating) {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            disabledLabelColor = if (unitMenuExpanded) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            disabledTrailingIconColor = if (isCreating) {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                DropdownMenu(
                                    expanded = unitMenuExpanded,
                                    onDismissRequest = { unitMenuExpanded = false }
                                ) {
                                    ShareValidityUnit.entries.forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(unit.labelResId())) },
                                            trailingIcon = if (unit == selectedUnit) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                selectedUnitName = unit.name
                                                if (unit != ShareValidityUnit.NONE &&
                                                    (validityAmountText.toIntOrNull() ?: 0) <= 0
                                                ) {
                                                    validityAmountText = unit.defaultAmount()
                                                }
                                                unitMenuExpanded = false
                                            },
                                            modifier = Modifier.testTag("share_unit_option_${unit.name}")
                                        )
                                    }
                                }
                            }
                        }

                        if (isAmountInvalid) {
                            Text(
                                text = stringResource(R.string.share_validity_invalid_amount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("share_validity_error_text")
                            )
                        }
                    }

                    // Optional password input (only when enabled on server)
                    if (passwordProtected) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.share_password_label)) },
                            placeholder = { Text(stringResource(R.string.share_password_placeholder)) },
                            singleLine = true,
                            enabled = !isCreating,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitShareRequest() }
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = stringResource(
                                            if (passwordVisible) R.string.share_hide_password else R.string.share_show_password
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("share_password_input")
                        )
                    }

                    if (shareLinkState is ShareLinkUiState.Error) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = shareLinkState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .testTag("share_error_text")
                            )
                        }
                    }

                    // Primary Create Button
                    Button(
                        onClick = { submitShareRequest() },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("share_create_button")
                            .tvFocus(shape = RoundedCornerShape(24.dp))
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.share_creating_button))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.share_generate_button))
                        }
                    }

                    // Created Link Result Card
                    if (shareLinkState is ShareLinkUiState.Success) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("share_result_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.share_created_header),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = shareLinkState.shareUrl,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                                .testTag("share_result_url_text")
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { copyUrlToClipboard(shareLinkState.shareUrl) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("share_copy_button")
                                            .tvFocus(shape = RoundedCornerShape(24.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.share_copy_link),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = { launchShareIntent(shareLinkState.shareUrl) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("share_send_button")
                                            .tvFocus(shape = RoundedCornerShape(24.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.share_link_action),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("share_dialog_done_button")
                            .tvFocus(shape = RoundedCornerShape(20.dp))
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}
