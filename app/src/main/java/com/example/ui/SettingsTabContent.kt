package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.findActivity
import com.example.util.BiometricHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsTabContent(viewModel: GalleryViewModel) {
    val context = LocalContext.current

    // Suffix states
    var thumbnailSuffix by remember { mutableStateOf(viewModel.prefs.thumbnailPathSuffix) }
    var preloadSuffix by remember { mutableStateOf(viewModel.prefs.preloadPathSuffix) }
    var videoSuffix by remember { mutableStateOf(viewModel.prefs.videoPathSuffix) }
    var defaultRootPath by remember { mutableStateOf(viewModel.prefs.defaultRootPath) }

    // Visual states
    var showItemCount by remember { mutableStateOf(viewModel.prefs.showDirectoryItemCount) }
    var itemsPerRowPortrait by remember { mutableFloatStateOf(viewModel.prefs.itemsPerRowPortrait.toFloat()) }
    var itemsPerRowLandscape by remember { mutableFloatStateOf(viewModel.prefs.itemsPerRowLandscape.toFloat()) }
    var cornerRadius by remember { mutableFloatStateOf(viewModel.prefs.cornerRadius.toFloat()) }
    var spacing by remember { mutableFloatStateOf(viewModel.prefs.spacing.toFloat()) }
    var selectedRatioIndex by remember {
        mutableIntStateOf(
            when (viewModel.prefs.aspectRatio) {
                1.0f -> 0
                1.5f -> 1 // 3:2
                1.3333333f -> 2 // 4:3
                1.7777778f -> 3 // 16:9
                0.0f -> 4 // Original
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

    val selectedSettingsTab by viewModel.selectedSettingsTab.collectAsState()
    val tabTitles = listOf(
        stringResource(R.string.settings_tab_appearance),
        stringResource(R.string.settings_tab_features),
        stringResource(R.string.settings_tab_server)
    )
    val tabIcons = listOf(Icons.Default.Palette, Icons.Default.Star, Icons.Default.Cloud)

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedSettingsTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSettingsTab == index,
                    onClick = { viewModel.setSelectedSettingsTab(index) },
                    icon = { Icon(imageVector = tabIcons[index], contentDescription = title) },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selectedSettingsTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        val settingsScrollKey = "settings_tab_$selectedSettingsTab"
        val scrollState = remember(settingsScrollKey) {
            ScrollState(viewModel.getScrollPosition(settingsScrollKey).offset)
        }
        LaunchedEffect(scrollState, settingsScrollKey) {
            snapshotFlow { scrollState.value }
                .collect { offset ->
                    viewModel.saveScrollPosition(settingsScrollKey, 0, offset)
                }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedSettingsTab == 0) {
                // --- 1. Theme Settings ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.theme),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Theme Color Picker
                        Column {
                            Text(text = stringResource(R.string.theme_color), style = MaterialTheme.typography.bodyMedium)
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
                            val scrollState = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(scrollState),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                colorsList.forEach { (name, color, colorLabel) ->
                                    val isSelected = selectedThemeColor == name
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                viewModel.setThemeColorOption(name)
                                            },
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
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (scrollState.maxValue > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), shape = CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.2f)
                                            .fillMaxHeight()
                                            .align(Alignment.CenterStart)
                                            .graphicsLayer {
                                                val max = scrollState.maxValue
                                                val value = scrollState.value
                                                val fraction = if (max > 0) value.toFloat() / max.toFloat() else 0f
                                                translationX = fraction * (size.width * 4f)
                                            }
                                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Theme Mode Selector
                        Column {
                            Text(text = stringResource(R.string.theme_mode), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(10.dp))

                            val selectedThemeMode by viewModel.themeMode.collectAsState()
                            val themeModes = listOf(
                                "Auto" to stringResource(R.string.theme_mode_auto),
                                "Light" to stringResource(R.string.theme_mode_light),
                                "Dark" to stringResource(R.string.theme_mode_dark),
                                "Black" to stringResource(R.string.theme_mode_black)
                            )
                            val themeModeScrollState = rememberScrollState()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(themeModeScrollState),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                themeModes.forEach { (mode, label) ->
                                    val isSelected = selectedThemeMode == mode || (mode == "Black" && (selectedThemeMode == "OLED Black" || selectedThemeMode == "OLED"))
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setThemeMode(mode) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 2. View Settings ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.view),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.show_folder_item_count), style = MaterialTheme.typography.bodyMedium)
                                Text(text = stringResource(R.string.show_folder_item_count_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = showItemCount,
                                onCheckedChange = {
                                    showItemCount = it
                                    viewModel.setShowDirectoryItemCount(it)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        val maxBrightnessEnabled by viewModel.maxBrightnessEnabled.collectAsState()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.max_brightness), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = stringResource(R.string.max_brightness_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = maxBrightnessEnabled,
                                onCheckedChange = { viewModel.setMaxBrightnessEnabled(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = stringResource(R.string.items_per_row_portrait), style = MaterialTheme.typography.bodyMedium)
                                Text(text = "${itemsPerRowPortrait.toInt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = itemsPerRowPortrait,
                                onValueChange = {
                                    itemsPerRowPortrait = it
                                    viewModel.setItemsPerRowPortrait(it.toInt())
                                },
                                valueRange = 1f..6f,
                                steps = 4,
                                modifier = Modifier.tvSlider(
                                    value = itemsPerRowPortrait,
                                    onValueChange = {
                                        itemsPerRowPortrait = it
                                        viewModel.setItemsPerRowPortrait(it.toInt())
                                    },
                                    valueRange = 1f..6f,
                                    steps = 4
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = stringResource(R.string.items_per_row_landscape), style = MaterialTheme.typography.bodyMedium)
                                Text(text = "${itemsPerRowLandscape.toInt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = itemsPerRowLandscape,
                                onValueChange = {
                                    itemsPerRowLandscape = it
                                    viewModel.setItemsPerRowLandscape(it.toInt())
                                },
                                valueRange = 2f..10f,
                                steps = 7,
                                modifier = Modifier.tvSlider(
                                    value = itemsPerRowLandscape,
                                    onValueChange = {
                                        itemsPerRowLandscape = it
                                        viewModel.setItemsPerRowLandscape(it.toInt())
                                    },
                                    valueRange = 2f..10f,
                                    steps = 7
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = stringResource(R.string.spacing), style = MaterialTheme.typography.bodyMedium)
                                Text(text = stringResource(R.string.dp_value, spacing.toInt()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = spacing,
                                onValueChange = {
                                    spacing = it
                                    viewModel.setSpacing(it.toInt())
                                },
                                valueRange = 0f..24f,
                                steps = 24,
                                modifier = Modifier.tvSlider(
                                    value = spacing,
                                    onValueChange = {
                                        spacing = it
                                        viewModel.setSpacing(it.toInt())
                                    },
                                    valueRange = 0f..24f,
                                    steps = 24
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = stringResource(R.string.corner_radius), style = MaterialTheme.typography.bodyMedium)
                                Text(text = stringResource(R.string.dp_value, cornerRadius.toInt()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = cornerRadius,
                                onValueChange = {
                                    cornerRadius = it
                                    viewModel.setCornerRadius(it.toInt())
                                },
                                valueRange = 0f..32f,
                                steps = 32,
                                modifier = Modifier.tvSlider(
                                    value = cornerRadius,
                                    onValueChange = {
                                        cornerRadius = it
                                        viewModel.setCornerRadius(it.toInt())
                                    },
                                    valueRange = 0f..32f,
                                    steps = 32
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Column {
                            Text(text = stringResource(R.string.aspect_ratio), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var expanded by remember { mutableStateOf(false) }
                                val ratioCoroutineScope = rememberCoroutineScope()
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { expanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(ratioOptions[selectedRatioIndex])
                                    }
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    ) {
                                        ratioOptions.forEachIndexed { index, option ->
                                            DropdownMenuItem(
                                                text = { Text(option) },
                                                onClick = {
                                                    if (expanded) {
                                                        expanded = false
                                                        val targetRatio = ratioValues[index]
                                                        ratioCoroutineScope.launch {
                                                            delay(180)
                                                            selectedRatioIndex = index
                                                            viewModel.setAspectRatio(targetRatio)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 3. Gestures Settings ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.gestures),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val dismissGestureEnabled by viewModel.dismissGestureEnabled.collectAsState()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.swipe_down_dismiss), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = stringResource(R.string.swipe_down_dismiss_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = dismissGestureEnabled,
                                onCheckedChange = { viewModel.setDismissGestureEnabled(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        val showMetadataGestureEnabled by viewModel.showMetadataGestureEnabled.collectAsState()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.swipe_up_details), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = stringResource(R.string.swipe_up_details_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showMetadataGestureEnabled,
                                onCheckedChange = { viewModel.setShowMetadataGestureEnabled(it) }
                            )
                        }
                    }
                }
            } else if (selectedSettingsTab == 1) {
                // --- 3. Features Settings ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.features),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Column {
                            Text(text = stringResource(R.string.rediscover_range), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            val daysRange by viewModel.rediscoverDays.collectAsState()
                            Text(
                                text = if (daysRange == 1) stringResource(R.string.day_singular, daysRange)
                                else stringResource(R.string.day_plural, daysRange)
                            )
                            Slider(
                                value = daysRange.toFloat(),
                                onValueChange = { viewModel.setRediscoverDays(it.toInt()) },
                                valueRange = 1f..14f,
                                steps = 13,
                                modifier = Modifier.tvSlider(
                                    value = daysRange.toFloat(),
                                    onValueChange = { viewModel.setRediscoverDays(it.toInt()) },
                                    valueRange = 1f..14f,
                                    steps = 13
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Column {
                            Text(text = stringResource(R.string.slideshow_duration), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            val slideDuration by viewModel.slideshowDuration.collectAsState()
                            Text(
                                text = if (slideDuration == 1) stringResource(R.string.second_singular, slideDuration)
                                else stringResource(R.string.second_plural, slideDuration)
                            )
                            Slider(
                                value = slideDuration.toFloat(),
                                onValueChange = { viewModel.setSlideshowDuration(it.toInt()) },
                                valueRange = 1f..10f,
                                steps = 8,
                                modifier = Modifier.tvSlider(
                                    value = slideDuration.toFloat(),
                                    onValueChange = { viewModel.setSlideshowDuration(it.toInt()) },
                                    valueRange = 1f..10f,
                                    steps = 8
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        val peopleFallback by viewModel.peopleFallbackToKeywords.collectAsState()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.keyword_fallback_persons), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = stringResource(R.string.keyword_fallback_persons_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = peopleFallback,
                                onCheckedChange = { viewModel.setPeopleFallbackToKeywords(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Security & App Protection Card
                val isBiometricEnabled by viewModel.isBiometricLockEnabled.collectAsState()
                val hideProtected by viewModel.hideProtectedItems.collectAsState()
                val activity = remember(context) { context.findActivity() as? FragmentActivity }
                val biometricStatus = remember(context) { BiometricHelper.getStatusMessage(context) }
                val isBiometricAvailable = remember(context) { BiometricHelper.isBiometricAvailable(context) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.security_app_protection),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.fingerprint_device_lock),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isBiometricAvailable) {
                                        stringResource(R.string.fingerprint_device_lock_desc)
                                    } else {
                                        stringResource(R.string.biometric_status_format, biometricStatus)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = { targetState ->
                                    if (activity != null) {
                                        BiometricHelper.authenticate(
                                            activity = activity,
                                            title = if (targetState) context.getString(R.string.enable_app_lock) else context.getString(R.string.disable_app_lock),
                                            subtitle = context.getString(R.string.confirm_biometric_subtitle),
                                            onSuccess = { viewModel.setBiometricLockEnabled(targetState) },
                                            onError = { err ->
                                                if (err != BiometricHelper.ERROR_CANCELED) {
                                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    } else {
                                        viewModel.setBiometricLockEnabled(targetState)
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.hide_protected_items),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.hide_protected_items_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = hideProtected,
                                onCheckedChange = { viewModel.setHideProtectedItems(it) }
                            )
                        }
                    }
                }
            } else if (selectedSettingsTab == 2) {
                // --- 4. Server Settings & Connection Monitor ---
                val netInfo by viewModel.networkConnectionInfo.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.testNetworkConnections()
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.server_configuration),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            val (activeText, activeColor, activeIcon) = when (netInfo.activeRoute) {
                                ActiveRouteType.LOCAL_WIFI -> Triple(
                                    stringResource(R.string.local_server_active),
                                    Color(0xFF4CAF50),
                                    Icons.Default.Wifi
                                )
                                ActiveRouteType.REMOTE_PRIMARY -> Triple(
                                    stringResource(R.string.primary_server_active),
                                    Color(0xFF2196F3),
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
                                color = activeColor.copy(alpha = 0.15f),
                                contentColor = activeColor
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val displayUsername = if (viewModel.prefs.username.isEmpty()) stringResource(R.string.demo_public_user) else viewModel.prefs.username
                        Text(
                            text = stringResource(R.string.logged_in_as, displayUsername),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        var localServerUrl by remember { mutableStateOf(viewModel.prefs.localServerUrl) }
                        OutlinedTextField(
                            value = localServerUrl,
                            onValueChange = {
                                localServerUrl = it
                                viewModel.prefs.localServerUrl = it
                                viewModel.invalidateActiveServerUrl()
                            },
                            label = { Text(stringResource(R.string.local_server_label)) },
                            placeholder = { Text(stringResource(R.string.local_server_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.disconnect_server))
                        }
                    }
                }

                // --- Path Configuration ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.path_configuration),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = defaultRootPath,
                            onValueChange = {
                                defaultRootPath = it
                                viewModel.setDefaultRootPath(it)
                            },
                            label = { Text(stringResource(R.string.default_root_path_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.leave_empty_root_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // --- API Suffix Settings ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.api_suffix_configuration),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = thumbnailSuffix,
                            onValueChange = {
                                thumbnailSuffix = it
                                viewModel.prefs.thumbnailPathSuffix = it
                            },
                            label = { Text(stringResource(R.string.thumb_suffix_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = preloadSuffix,
                            onValueChange = {
                                preloadSuffix = it
                                viewModel.prefs.preloadPathSuffix = it
                            },
                            label = { Text(stringResource(R.string.preload_suffix_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = videoSuffix,
                            onValueChange = {
                                videoSuffix = it
                                viewModel.prefs.videoPathSuffix = it
                            },
                            label = { Text(stringResource(R.string.video_suffix_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // --- Storage Settings ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.storage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val cacheSize by viewModel.cacheSize.collectAsState()
                        LaunchedEffect(selectedSettingsTab) {
                            if (selectedSettingsTab == 2) {
                                viewModel.updateCacheSize()
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = stringResource(R.string.cache), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = stringResource(R.string.currently_used, cacheSize),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    viewModel.clearCaches()
                                    Toast.makeText(context, context.getString(R.string.cache_cleared_toast), Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text(stringResource(R.string.clear))
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
fun AboutScreen(viewModel: GalleryViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val version = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "2.7"
        } catch (_: Exception) {
            "2.7"
        }
    }

    val updateState by viewModel.updateState.collectAsState()
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsState()

    LaunchedEffect(autoCheckUpdates) {
        if (autoCheckUpdates && updateState is GalleryViewModel.UpdateState.Idle) {
            viewModel.checkForUpdates()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_pigallery2), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero App Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = stringResource(R.string.app_logo_cd),
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = stringResource(R.string.version_format, version),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Software Updates Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.software_updates),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.auto_check_updates),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.auto_check_updates_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoCheckUpdates,
                            onCheckedChange = { viewModel.setAutoCheckUpdates(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (val state = updateState) {
                        is GalleryViewModel.UpdateState.Idle -> {
                            Button(
                                onClick = { viewModel.checkForUpdates(force = true) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.check_for_updates))
                            }
                        }
                        is GalleryViewModel.UpdateState.Checking -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(stringResource(R.string.checking_updates), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        is GalleryViewModel.UpdateState.UpToDate -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.up_to_date_format, state.currentVersion),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.checkForUpdates(force = true) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.check_again))
                                }
                            }
                        }
                        is GalleryViewModel.UpdateState.NewRelease -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.new_update_available_tag, state.tagName),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = state.releaseNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, state.releaseUrl.toUri())
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stringResource(R.string.view_download_release))
                                    }
                                }
                            }
                        }
                        is GalleryViewModel.UpdateState.Error -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.error_prefix, state.message),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.checkForUpdates(force = true) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                }
            }

            // Links & Resources Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.links_resources),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FilledTonalButton(
                        onClick = { uriHandler.openUri("https://github.com/dasmaetthes/pigallery2_android") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.view_source_github), fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { uriHandler.openUri("https://github.com/dasmaetthes/pigallery2_android/issues") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.report_issue_feedback))
                    }
                }
            }

            // Open Source Software Licenses Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.open_source_licenses),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val introPrefix = stringResource(R.string.open_source_intro_prefix)
                    val licenseName = stringResource(R.string.license_name)
                    val openSourceMiddle = stringResource(R.string.open_source_middle)
                    val apacheLicenseName = stringResource(R.string.apache_license_name)
                    val annotatedString = buildAnnotatedString {
                        append(introPrefix)
                        append(" ")

                        val licenseStartIndex = length
                        append(licenseName)
                        val licenseEndIndex = length

                        addStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            ),
                            start = licenseStartIndex,
                            end = licenseEndIndex
                        )
                        addLink(
                            LinkAnnotation.Url(
                                url = "https://github.com/dasmaetthes/pigallery2_android/tree/main?tab=MIT-1-ov-file#MIT-1-ov-file",
                                linkInteractionListener = LinkInteractionListener {
                                    uriHandler.openUri("https://github.com/dasmaetthes/pigallery2_android/tree/main?tab=MIT-1-ov-file#MIT-1-ov-file")
                                }
                            ),
                            start = licenseStartIndex,
                            end = licenseEndIndex
                        )

                        append(openSourceMiddle)
                        append(" ")

                        val startIndex = length
                        append(apacheLicenseName)
                        val endIndex = length

                        addStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            ),
                            start = startIndex,
                            end = endIndex
                        )
                        addLink(
                            LinkAnnotation.Url(
                                url = "https://www.apache.org/licenses/LICENSE-2.0",
                                linkInteractionListener = LinkInteractionListener {
                                    uriHandler.openUri("https://www.apache.org/licenses/LICENSE-2.0")
                                }
                            ),
                            start = startIndex,
                            end = endIndex
                        )
                        append(".")
                    }

                    Text(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun NewUpdateDialog(
    newRelease: GalleryViewModel.UpdateState.NewRelease,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.new_update_available),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.new_update_message, newRelease.tagName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (newRelease.releaseNotes.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = stringResource(R.string.release_notes_label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = newRelease.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) {
                Text(stringResource(R.string.update_now))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.later))
            }
        }
    )
}
