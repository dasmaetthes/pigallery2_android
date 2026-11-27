package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.util.LocaleHelper

private data class TvSettingsCategory(
    val index: Int,
    val titleRes: Int,
    val icon: ImageVector
)

/**
 * Stripped-down, D-pad-friendly Settings screen built specifically for Android TV.
 *
 * Intentionally omits unsupported mobile/desktop controls:
 * - Manage Active Shares (share links disabled on TV)
 * - Security & App Protection / Lock Item / Biometric Lock / Hide Protected Items
 * - Touch Gestures (swipe-to-dismiss, swipe-up details)
 * - Portrait grid columns & Max Screen Brightness
 * - Raw editable path & API suffix developer text inputs
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvSettingsTabContent(
    viewModel: GalleryViewModel,
    onOpenAbout: (() -> Unit)? = null,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    val selectedCategory by viewModel.selectedSettingsTab.collectAsState()
    val clampedCategory = selectedCategory.coerceIn(0, 2)

    val categories = remember {
        listOf(
            TvSettingsCategory(0, R.string.tv_settings_category_display, Icons.Default.Palette),
            TvSettingsCategory(1, R.string.tv_settings_category_playback, Icons.Default.Slideshow),
            TvSettingsCategory(2, R.string.tv_settings_category_server, Icons.Default.Cloud)
        )
    }

    val settingsScrollKey = "tv_settings_tab_$clampedCategory"
    val scrollState = remember(settingsScrollKey) {
        ScrollState(viewModel.getScrollPosition(settingsScrollKey).offset)
    }
    LaunchedEffect(scrollState, settingsScrollKey) {
        snapshotFlow { scrollState.value }
            .collect { offset ->
                viewModel.saveScrollPosition(settingsScrollKey, 0, offset)
            }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tv_settings_screen")
    ) {
        val useTwoPane = maxWidth >= 600.dp
        if (useTwoPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left D-pad Category Rail
                Column(
                    modifier = Modifier
                        .width(248.dp)
                        .fillMaxHeight()
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    categories.forEach { category ->
                        TvSettingsCategoryItem(
                            category = category,
                            selected = clampedCategory == category.index,
                            onClick = { viewModel.setSelectedSettingsTab(category.index) },
                            modifier = if (category.index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                        )
                    }
                }

                // Right Scrollable Detail Pane
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(scrollState),
                    contentAlignment = Alignment.TopStart
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        TvSettingsCategoryContent(
                            categoryIndex = clampedCategory,
                            viewModel = viewModel,
                            onOpenAbout = onOpenAbout
                        )
                    }
                }
            }
        } else {
            // Compact fallback for narrow split-screen windows
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        val title = stringResource(category.titleRes)
                        FilterChip(
                            selected = clampedCategory == category.index,
                            onClick = { viewModel.setSelectedSettingsTab(category.index) },
                            label = { Text(title) },
                            leadingIcon = {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .then(if (category.index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                .tvFocus(shape = RoundedCornerShape(12.dp))
                                .testTag("settings_tab_${category.index}")
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TvSettingsCategoryContent(
                        categoryIndex = clampedCategory,
                        viewModel = viewModel,
                        onOpenAbout = onOpenAbout
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSettingsCategoryItem(
    category: TvSettingsCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(category.titleRes)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val border = if (selected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .tvFocus(shape = RoundedCornerShape(16.dp))
            .testTag("settings_tab_${category.index}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TvSettingsCategoryContent(
    categoryIndex: Int,
    viewModel: GalleryViewModel,
    onOpenAbout: (() -> Unit)?
) {
    when (categoryIndex) {
        0 -> TvDisplayAndThemeSettings(viewModel = viewModel)
        1 -> TvPlaybackAndGallerySettings(viewModel = viewModel)
        else -> TvServerAndStorageSettings(viewModel = viewModel, onOpenAbout = onOpenAbout)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvDisplayAndThemeSettings(viewModel: GalleryViewModel) {
    val context = LocalContext.current

    var showItemCount by remember { mutableStateOf(viewModel.prefs.showDirectoryItemCount) }
    var itemsPerRowLandscape by remember { mutableFloatStateOf(viewModel.prefs.itemsPerRowLandscape.toFloat()) }
    var spacing by remember { mutableFloatStateOf(viewModel.prefs.spacing.toFloat()) }
    var cornerRadius by remember { mutableFloatStateOf(viewModel.prefs.cornerRadius.toFloat()) }
    var selectedRatioIndex by remember {
        mutableIntStateOf(
            when (viewModel.prefs.aspectRatio) {
                1.0f -> 0
                1.5f -> 1
                1.3333333f -> 2
                1.7777778f -> 3
                0.0f -> 4
                else -> 4
            }
        )
    }

    val ratioOptions = listOf(
        stringResource(R.string.ratio_square),
        stringResource(R.string.ratio_classic),
        stringResource(R.string.ratio_standard),
        stringResource(R.string.ratio_wide),
        stringResource(R.string.ratio_original)
    )
    val ratioValues = listOf(1.0f, 1.5f, 1.3333333f, 1.7777778f, 0.0f)

    // 1. Theme Card
    TvSettingsCard(modifier = Modifier.testTag("tv_settings_theme_card")) {
        TvSettingsSectionHeader(title = stringResource(R.string.theme))
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.theme_color),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        val colorsList = listOf(
            Triple("Purple", Color(0xFFD0BCFF), stringResource(R.string.color_purple)),
            Triple("Blue", Color(0xFF82CFFF), stringResource(R.string.color_blue)),
            Triple("Green", Color(0xFF88F3A2), stringResource(R.string.color_green)),
            Triple("Orange", Color(0xFFFFB77C), stringResource(R.string.color_orange)),
            Triple("Teal", Color(0xFF80F2DB), stringResource(R.string.color_teal)),
            Triple("Pink", Color(0xFFFFAEBA), stringResource(R.string.color_pink)),
            Triple("Grey", Color(0xFF8F9099), stringResource(R.string.color_grey)),
            Triple("White", Color(0xFFFFFFFF), stringResource(R.string.color_white))
        )
        val selectedThemeColor by viewModel.themeColorOption.collectAsState()

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            colorsList.forEach { (name, color, colorLabel) ->
                val isSelected = selectedThemeColor == name
                val checkTint = if (color.luminance() > 0.5f) Color.Black else Color.White
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .tvFocus(shape = CircleShape)
                        .clickable { viewModel.setThemeColorOption(name) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = colorLabel,
                                tint = checkTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        TvSettingsDivider()

        Text(
            text = stringResource(R.string.theme_mode),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        val selectedThemeMode by viewModel.themeMode.collectAsState()
        val themeModes = listOf(
            "Auto" to stringResource(R.string.theme_mode_auto),
            "Light" to stringResource(R.string.theme_mode_light),
            "Dark" to stringResource(R.string.theme_mode_dark),
            "Black" to stringResource(R.string.theme_mode_black)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            themeModes.forEach { (mode, label) ->
                val isSelected = selectedThemeMode == mode ||
                    (mode == "Black" && (selectedThemeMode == "OLED Black" || selectedThemeMode == "OLED"))
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setThemeMode(mode) },
                    label = { Text(label) },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .tvFocus(shape = RoundedCornerShape(10.dp))
                )
            }
        }
    }

    // 2. Language Card
    TvSettingsCard(modifier = Modifier.testTag("settings_language_card")) {
        TvSettingsSectionHeader(title = stringResource(R.string.language))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.language_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        val selectedLanguage by viewModel.appLanguage.collectAsState()
        val languageOptions = listOf(
            LocaleHelper.LANGUAGE_SYSTEM to stringResource(R.string.language_system),
            LocaleHelper.LANGUAGE_ENGLISH to stringResource(R.string.language_english),
            LocaleHelper.LANGUAGE_GERMAN to stringResource(R.string.language_german),
            LocaleHelper.LANGUAGE_UKRAINIAN to stringResource(R.string.language_ukrainian)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            languageOptions.forEach { (code, label) ->
                val isSelected = selectedLanguage == code
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            viewModel.setAppLanguage(code)
                            LocaleHelper.applyLanguage(context, code)
                        }
                    },
                    label = { Text(label) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("language_option_$code")
                        .tvFocus(shape = RoundedCornerShape(10.dp))
                )
            }
        }
    }

    // 3. TV Grid Layout Card (landscape-only, inline aspect ratio chips — no portrait or brightness)
    TvSettingsCard(modifier = Modifier.testTag("tv_settings_layout_card")) {
        TvSettingsSectionHeader(title = stringResource(R.string.tv_settings_grid_layout))
        Spacer(modifier = Modifier.height(12.dp))

        TvSettingsToggleRow(
            title = stringResource(R.string.show_folder_item_count),
            description = stringResource(R.string.show_folder_item_count_desc),
            checked = showItemCount,
            onCheckedChange = {
                showItemCount = it
                viewModel.setShowDirectoryItemCount(it)
            }
        )

        TvSettingsDivider()

        TvSettingsSliderRow(
            title = stringResource(R.string.tv_settings_items_per_row),
            valueLabel = "${itemsPerRowLandscape.toInt()}",
            value = itemsPerRowLandscape,
            valueRange = 2f..10f,
            steps = 7,
            onValueChange = {
                itemsPerRowLandscape = it
                viewModel.setItemsPerRowLandscape(it.toInt())
            }
        )

        TvSettingsDivider()

        TvSettingsSliderRow(
            title = stringResource(R.string.spacing),
            valueLabel = stringResource(R.string.dp_value, spacing.toInt()),
            value = spacing,
            valueRange = 0f..24f,
            steps = 24,
            onValueChange = {
                spacing = it
                viewModel.setSpacing(it.toInt())
            }
        )

        TvSettingsDivider()

        TvSettingsSliderRow(
            title = stringResource(R.string.corner_radius),
            valueLabel = stringResource(R.string.dp_value, cornerRadius.toInt()),
            value = cornerRadius,
            valueRange = 0f..32f,
            steps = 32,
            onValueChange = {
                cornerRadius = it
                viewModel.setCornerRadius(it.toInt())
            }
        )

        TvSettingsDivider()

        Text(
            text = stringResource(R.string.aspect_ratio),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ratioOptions.forEachIndexed { index, option ->
                FilterChip(
                    selected = selectedRatioIndex == index,
                    onClick = {
                        selectedRatioIndex = index
                        viewModel.setAspectRatio(ratioValues[index])
                    },
                    label = { Text(option) },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .tvFocus(shape = RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

@Composable
private fun TvPlaybackAndGallerySettings(viewModel: GalleryViewModel) {
    val slideDuration by viewModel.slideshowDuration.collectAsState()
    val daysRange by viewModel.rediscoverDays.collectAsState()
    val peopleFallback by viewModel.peopleFallbackToKeywords.collectAsState()

    TvSettingsCard(modifier = Modifier.testTag("tv_settings_playback_card")) {
        TvSettingsSectionHeader(title = stringResource(R.string.tv_settings_category_playback))
        Spacer(modifier = Modifier.height(14.dp))

        TvSettingsSliderRow(
            title = stringResource(R.string.slideshow_duration_tv),
            valueLabel = if (slideDuration == 1) {
                stringResource(R.string.second_singular, slideDuration)
            } else {
                stringResource(R.string.second_plural, slideDuration)
            },
            value = slideDuration.toFloat(),
            valueRange = 1f..10f,
            steps = 8,
            onValueChange = { viewModel.setSlideshowDuration(it.toInt()) }
        )

        TvSettingsDivider()

        TvSettingsSliderRow(
            title = stringResource(R.string.rediscover_range),
            valueLabel = if (daysRange == 1) {
                stringResource(R.string.day_singular, daysRange)
            } else {
                stringResource(R.string.day_plural, daysRange)
            },
            value = daysRange.toFloat(),
            valueRange = 1f..14f,
            steps = 13,
            onValueChange = { viewModel.setRediscoverDays(it.toInt()) }
        )

        TvSettingsDivider()

        TvSettingsToggleRow(
            title = stringResource(R.string.keyword_fallback_persons),
            description = stringResource(R.string.keyword_fallback_persons_desc),
            checked = peopleFallback,
            onCheckedChange = { viewModel.setPeopleFallbackToKeywords(it) }
        )
    }
}

@Composable
private fun TvServerAndStorageSettings(
    viewModel: GalleryViewModel,
    onOpenAbout: (() -> Unit)?
) {
    val context = LocalContext.current
    val netInfo by viewModel.networkConnectionInfo.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.testNetworkConnections()
        viewModel.updateCacheSize()
    }

    // 1. Connected Server Summary Card (read-only info + Disconnect, no raw path/suffix inputs or Manage Active Shares)
    TvSettingsCard(modifier = Modifier.testTag("tv_settings_server_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvSettingsSectionHeader(title = stringResource(R.string.tv_settings_connected_server))

            val (activeText, activeColor, activeIcon) = when (netInfo.activeRoute) {
                ActiveRouteType.LOCAL_WIFI -> Triple(
                    stringResource(R.string.local_server_active),
                    MaterialTheme.colorScheme.primary,
                    Icons.Default.Wifi
                )
                ActiveRouteType.REMOTE_PRIMARY -> Triple(
                    stringResource(R.string.primary_server_active),
                    MaterialTheme.colorScheme.tertiary,
                    Icons.Default.Cloud
                )
                ActiveRouteType.NONE -> Triple(
                    stringResource(R.string.offline),
                    MaterialTheme.colorScheme.error,
                    Icons.Default.Close
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = activeColor.copy(alpha = 0.14f),
                contentColor = activeColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = activeIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = activeColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val activeUrl = viewModel.getActiveServerUrl().ifBlank { viewModel.prefs.serverUrl }
        val displayServerUrl = activeUrl.ifBlank { stringResource(R.string.tv_settings_server_not_configured) }
        val displayUsername = if (viewModel.prefs.username.isEmpty()) {
            stringResource(R.string.demo_public_user)
        } else {
            viewModel.prefs.username
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayServerUrl,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.logged_in_as, displayUsername),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.logout() },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .tvFocus(shape = RoundedCornerShape(16.dp))
                .testTag("settings_disconnect_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.disconnect_server),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    // 2. Storage & Cache Card
    TvSettingsCard(modifier = Modifier.testTag("tv_settings_storage_card")) {
        TvSettingsSectionHeader(title = stringResource(R.string.storage))
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cache),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.currently_used, cacheSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            FilledTonalButton(
                onClick = {
                    viewModel.clearCaches()
                    Toast.makeText(
                        context,
                        context.getString(R.string.cache_cleared_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .tvFocus(shape = RoundedCornerShape(16.dp))
                    .testTag("tv_settings_clear_cache_button")
            ) {
                Text(stringResource(R.string.clear))
            }
        }
    }

    // 3. About Card
    if (onOpenAbout != null) {
        Card(
            onClick = onOpenAbout,
            modifier = Modifier
                .fillMaxWidth()
                .tvFocus(shape = RoundedCornerShape(18.dp))
                .testTag("settings_about_card"),
            shape = RoundedCornerShape(18.dp),
            colors = GalleryCardDefaults.sectionCardColors(),
            elevation = GalleryCardDefaults.sectionCardElevation(),
            border = GalleryCardDefaults.sectionCardBorder()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 68.dp)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.about_pigallery2),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.about_app_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TvSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = GalleryCardDefaults.sectionCardColors(),
        elevation = GalleryCardDefaults.sectionCardElevation(),
        border = GalleryCardDefaults.sectionCardBorder()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
private fun TvSettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun TvSettingsDivider() {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun TvSettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .tvFocus(shape = RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun TvSettingsSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .tvFocus(shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 4.dp)
                .tvSlider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    steps = steps
                )
        )
    }
}
