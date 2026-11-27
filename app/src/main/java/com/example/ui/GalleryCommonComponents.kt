package com.example.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.util.rememberIsAndroidTv

/**
 * Creates and persists a [LazyGridState] for [scrollKey] in [viewModel] and Compose's
 * saveable state registry.
 *
 * Safeguards:
 * 1. Uses a per-key owner token so outgoing [Crossfade] / rapid-navigation disposals never
 *    overwrite a newly mounted grid's scroll position.
 * 2. Holds a [pendingRestore] target whenever a non-zero saved position exists so an initial
 *    partial/empty layout pass (where `totalItemsCount <= saved.index`) never clamps and
 *    overwrites the saved scroll position; once enough items are laid out, it restores the
 *    exact target (unless the user already started scrolling manually).
 * 3. Flushes the final scroll position on dispose once restoration has settled.
 */
private data class ScrollSnapshot(
    val index: Int,
    val offset: Int,
    val totalCount: Int,
    val isScrolling: Boolean
)

@Composable
fun rememberPersistedLazyGridState(
    viewModel: GalleryViewModel,
    scrollKey: String
): LazyGridState {
    val ownerToken = remember(scrollKey) { viewModel.claimScrollKeyOwner(scrollKey) }
    val initialSaved = remember(scrollKey) { viewModel.getScrollPosition(scrollKey) }
    val gridState = rememberSaveable(scrollKey, saver = LazyGridState.Saver) {
        LazyGridState(initialSaved.index, initialSaved.offset)
    }

    var pendingRestore by remember(gridState, scrollKey, ownerToken) {
        mutableStateOf(
            if (initialSaved.index > 0 || initialSaved.offset > 0) initialSaved else null
        )
    }

    LaunchedEffect(gridState, scrollKey, ownerToken) {
        snapshotFlow {
            ScrollSnapshot(
                index = gridState.firstVisibleItemIndex,
                offset = gridState.firstVisibleItemScrollOffset,
                totalCount = gridState.layoutInfo.totalItemsCount,
                isScrolling = gridState.isScrollInProgress
            )
        }.collect { snap ->
            if (snap.isScrolling) {
                pendingRestore = null
                if (snap.totalCount > 0) {
                    viewModel.saveScrollPosition(scrollKey, snap.index, snap.offset, ownerToken)
                }
                return@collect
            }

            val target = pendingRestore
            if (target != null) {
                if (snap.totalCount > target.index) {
                    if (snap.index != target.index || snap.offset != target.offset) {
                        gridState.scrollToItem(target.index, target.offset)
                    }
                    pendingRestore = null
                    viewModel.saveScrollPosition(
                        scrollKey,
                        gridState.firstVisibleItemIndex,
                        gridState.firstVisibleItemScrollOffset,
                        ownerToken
                    )
                }
                // While totalCount <= target.index, do NOT overwrite saved position with clamped values.
            } else if (snap.totalCount > 0) {
                viewModel.saveScrollPosition(scrollKey, snap.index, snap.offset, ownerToken)
            }
        }
    }

    DisposableEffect(gridState, scrollKey, ownerToken) {
        onDispose {
            if (pendingRestore == null && gridState.layoutInfo.totalItemsCount > 0) {
                viewModel.saveScrollPosition(
                    scrollKey,
                    gridState.firstVisibleItemIndex,
                    gridState.firstVisibleItemScrollOffset,
                    ownerToken
                )
            }
        }
    }

    return gridState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptivePullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isTv = rememberIsAndroidTv()
    if (isTv) {
        Box(modifier = modifier, content = content)
    } else {
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (!isRefreshing) {
                    onRefresh()
                }
            },
            state = pullToRefreshState,
            modifier = modifier,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
fun Modifier.tvFocus(
    shape: Shape = RoundedCornerShape(12.dp),
    showBorder: Boolean = true,
    scaleOnFocus: Boolean = false
): Modifier = this.composed {
    val isTv = rememberIsAndroidTv()
    var isFocused by remember { mutableStateOf(false) }

    val focusScale by animateFloatAsState(
        targetValue = if (isTv && scaleOnFocus && isFocused) 1.035f else 1f,
        label = "tvFocusScale"
    )
    val borderWidth = if (isTv) 3.dp else 4.dp
    val borderColor = MaterialTheme.colorScheme.primary
    val borderModifier = if (showBorder && isFocused) {
        Modifier.border(borderWidth, borderColor, shape)
    } else {
        Modifier
    }

    this
        .zIndex(if (isTv && isFocused) 1f else 0f)
        .graphicsLayer {
            scaleX = focusScale
            scaleY = focusScale
            this.shape = shape
            clip = true
        }
        .onFocusChanged { isFocused = it.hasFocus }
        .then(borderModifier)
}

fun Modifier.tvSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0
): Modifier = this.onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown) {
        val stepSize = if (steps > 0) {
            (valueRange.endInclusive - valueRange.start) / (steps + 1)
        } else {
            (valueRange.endInclusive - valueRange.start) / 10f
        }
        val epsilon = 0.0001f
        when (event.key) {
            Key.DirectionLeft -> {
                if (value > valueRange.start + epsilon) {
                    onValueChange((value - stepSize).coerceIn(valueRange))
                    true
                } else {
                    // Allow D-pad Left to exit slider cleanly at minimum value (prevents focus trap)
                    false
                }
            }
            Key.DirectionRight -> {
                if (value < valueRange.endInclusive - epsilon) {
                    onValueChange((value + stepSize).coerceIn(valueRange))
                    true
                } else {
                    // Allow D-pad Right to exit slider cleanly at maximum value
                    false
                }
            }
            else -> false
        }
    } else {
        false
    }
}

@Composable
fun ThumbnailOverlay(
    name: String,
    count: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.45f to Color.Transparent,
                    0.75f to Color.Black.copy(alpha = 0.35f),
                    1.0f to Color.Black.copy(alpha = 0.82f)
                )
            )
    )
    Column(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.Start
    ) {
        if (count.isNotBlank()) {
            Text(
                text = count,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LivePhotoBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("live_photo_badge"),
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.62f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MotionPhotosOn,
                contentDescription = stringResource(R.string.live_photo),
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "LIVE",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun BreadcrumbBar(
    pathHistory: List<String>,
    galleryViewMode: GalleryViewMode,
    onBreadcrumbClick: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(pathHistory.size) {
        if (pathHistory.isNotEmpty()) {
            listState.animateScrollToItem(pathHistory.lastIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .testTag("breadcrumb_bar")
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(pathHistory, key = { index, path -> "bc_${index}_$path" }) { index, path ->
            val folderName = if (galleryViewMode == GalleryViewMode.DATE) {
                when {
                    index == 0 -> stringResource(R.string.date_label)
                    path.startsWith("year:") && !path.contains("month:") -> path.substringAfter("year:")
                    path.contains("month:") -> {
                        val monthNum = path.substringAfter("month:").toIntOrNull() ?: 1
                        when (monthNum) {
                            1 -> stringResource(R.string.month_1)
                            2 -> stringResource(R.string.month_2)
                            3 -> stringResource(R.string.month_3)
                            4 -> stringResource(R.string.month_4)
                            5 -> stringResource(R.string.month_5)
                            6 -> stringResource(R.string.month_6)
                            7 -> stringResource(R.string.month_7)
                            8 -> stringResource(R.string.month_8)
                            9 -> stringResource(R.string.month_9)
                            10 -> stringResource(R.string.month_10)
                            11 -> stringResource(R.string.month_11)
                            12 -> stringResource(R.string.month_12)
                            else -> "$monthNum"
                        }
                    }
                    else -> stringResource(R.string.root)
                }
            } else {
                if (path.isEmpty()) stringResource(R.string.root) else path.substringAfterLast('/')
            }
            val isActive = index == pathHistory.lastIndex

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("breadcrumb_item_$index")
                        .clip(RoundedCornerShape(8.dp))
                        .tvFocus(shape = RoundedCornerShape(8.dp))
                        .clickable { onBreadcrumbClick(index) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = folderName,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun DateQuickJumpBar(
    currentPath: String,
    availableYears: List<Int>,
    getMonthName: (Int) -> String,
    onJumpToDate: (year: Int?, month: Int?) -> Unit,
    onSearchInPeriod: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedYear = remember(currentPath) {
        com.example.data.search.DateSearchQueryHelper.parseYearFromPath(currentPath)
    }
    val selectedMonth = remember(currentPath) {
        com.example.data.search.DateSearchQueryHelper.parseMonthFromPath(currentPath)
    }
    val currentCalYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val yearOptions = remember(availableYears, selectedYear, currentCalYear) {
        val base = if (availableYears.isNotEmpty()) {
            availableYears
        } else {
            (currentCalYear downTo (currentCalYear - 20)).toList()
        }
        if (selectedYear != null && selectedYear !in base) {
            (base + selectedYear).sortedDescending()
        } else {
            base
        }
    }

    var showYearMenu by remember { mutableStateOf(false) }
    var showMonthMenu by remember { mutableStateOf(false) }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("date_quick_jump_bar")
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "year_chip") {
            Box {
                FilterChip(
                    selected = selectedYear != null,
                    onClick = { showYearMenu = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .tvFocus(shape = RoundedCornerShape(8.dp))
                        .testTag("date_quick_year_chip"),
                    label = {
                        Text(
                            text = selectedYear?.toString() ?: stringResource(R.string.year_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = stringResource(R.string.year_label),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                DropdownMenu(
                    expanded = showYearMenu,
                    onDismissRequest = { showYearMenu = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.all_years),
                                fontWeight = if (selectedYear == null) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        trailingIcon = if (selectedYear == null) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        onClick = {
                            showYearMenu = false
                            onJumpToDate(null, null)
                        },
                        modifier = Modifier
                            .tvFocus(shape = RoundedCornerShape(8.dp))
                            .testTag("date_year_option_all")
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    yearOptions.forEach { year ->
                        val isSelected = selectedYear == year
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = year.toString(),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null,
                            onClick = {
                                showYearMenu = false
                                onJumpToDate(year, null)
                            },
                            modifier = Modifier
                                .tvFocus(shape = RoundedCornerShape(8.dp))
                                .testTag("date_year_option_$year")
                        )
                    }
                }
            }
        }

        item(key = "month_chip") {
            Box {
                FilterChip(
                    selected = selectedMonth != null,
                    onClick = { showMonthMenu = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .tvFocus(shape = RoundedCornerShape(8.dp))
                        .testTag("date_quick_month_chip"),
                    label = {
                        Text(
                            text = selectedMonth?.let { getMonthName(it) } ?: stringResource(R.string.month_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.month_label),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                DropdownMenu(
                    expanded = showMonthMenu,
                    onDismissRequest = { showMonthMenu = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    if (selectedYear != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.all_months),
                                    fontWeight = if (selectedMonth == null) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            trailingIcon = if (selectedMonth == null) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null,
                            onClick = {
                                showMonthMenu = false
                                onJumpToDate(selectedYear, null)
                            },
                            modifier = Modifier
                                .tvFocus(shape = RoundedCornerShape(8.dp))
                                .testTag("date_month_option_all")
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    }
                    (1..12).forEach { month ->
                        val isSelected = selectedMonth == month
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = getMonthName(month),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null,
                            onClick = {
                                showMonthMenu = false
                                val targetYear = selectedYear ?: yearOptions.firstOrNull() ?: currentCalYear
                                onJumpToDate(targetYear, month)
                            },
                            modifier = Modifier
                                .tvFocus(shape = RoundedCornerShape(8.dp))
                                .testTag("date_month_option_$month")
                        )
                    }
                }
            }
        }

        if (selectedYear != null) {
            item(key = "search_in_period_chip") {
                AssistChip(
                    onClick = onSearchInPeriod,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .tvFocus(shape = RoundedCornerShape(8.dp))
                        .testTag("date_search_in_period_chip"),
                    label = {
                        Text(
                            text = stringResource(R.string.search_in_period),
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_in_period),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ErrorStateView(
    message: String,
    actionFocusRequester: FocusRequester? = null,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.error_occurred),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .then(if (actionFocusRequester != null) Modifier.focusRequester(actionFocusRequester) else Modifier)
                    .tvFocus(shape = RoundedCornerShape(24.dp))
            ) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

enum class EmptyStateIllustration(
    @DrawableRes val drawableRes: Int,
    val fallbackIcon: ImageVector
) {
    SEARCH(R.drawable.img_empty_search, Icons.Outlined.SearchOff),
    ALBUMS(R.drawable.img_empty_albums, Icons.Outlined.PhotoLibrary),
    PEOPLE(R.drawable.img_empty_people, Icons.Outlined.Person),
    MEMORIES(R.drawable.img_empty_memories, Icons.Outlined.History)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Default.Image,
    illustration: EmptyStateIllustration? = null,
    guidanceText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    actionFocusRequester: FocusRequester? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag("empty_state_container"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (illustration != null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .size(width = 200.dp, height = 150.dp)
                        .testTag("empty_state_illustration")
                ) {
                    Image(
                        painter = painterResource(id = illustration.drawableRes),
                        contentDescription = stringResource(R.string.empty_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(R.string.empty_cd),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("empty_state_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .testTag("empty_state_subtitle")
            )

            if (!guidanceText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_state_guidance")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(18.dp)
                        )
                        Text(
                            text = guidanceText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            val hasPrimaryAction = actionLabel != null && onAction != null
            val hasSecondaryAction = secondaryActionLabel != null && onSecondaryAction != null
            if (hasPrimaryAction || hasSecondaryAction) {
                Spacer(modifier = Modifier.height(22.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (hasPrimaryAction) {
                        FilledTonalButton(
                            onClick = onAction!!,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .then(if (actionFocusRequester != null) Modifier.focusRequester(actionFocusRequester) else Modifier)
                                .testTag("empty_state_action_button")
                                .tvFocus(shape = RoundedCornerShape(24.dp))
                        ) {
                            Text(actionLabel!!)
                        }
                    }
                    if (hasSecondaryAction) {
                        OutlinedButton(
                            onClick = onSecondaryAction!!,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("empty_state_secondary_action_button")
                                .tvFocus(shape = RoundedCornerShape(24.dp))
                        ) {
                            Text(secondaryActionLabel!!)
                        }
                    }
                }
            }
        }
    }
}

fun formatVideoDuration(durationMs: Double?): String {
    if (durationMs == null || durationMs <= 0.0) return ""
    var totalSecs = (durationMs / 1000.0).toLong()
    if (totalSecs == 0L && durationMs > 0.0) {
        totalSecs = durationMs.toLong()
    }
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}

@Composable
private fun SortDialogShell(
    title: String,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sort_dialog")
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                content()

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("sort_cancel_button")
                            .tvFocus(shape = RoundedCornerShape(24.dp))
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = onApply,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("sort_apply_button")
                            .tvFocus(shape = RoundedCornerShape(24.dp))
                    ) {
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun SortDirectionRow(
    sortBy: String,
    sortDir: String,
    onDirectionChange: (String) -> Unit
) {
    if (sortBy == "random") return
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = sortDir == "asc",
            onClick = { onDirectionChange("asc") },
            modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text(
                    if (sortBy == "name") stringResource(R.string.sort_a_to_z)
                    else stringResource(R.string.sort_ascending)
                )
            }
        )
        FilterChip(
            selected = sortDir == "desc",
            onClick = { onDirectionChange("desc") },
            modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text(
                    if (sortBy == "name") stringResource(R.string.sort_z_to_a)
                    else stringResource(R.string.sort_descending)
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortDialog(
    viewModel: GalleryViewModel,
    onDismiss: () -> Unit
) {
    val activeTab by viewModel.activeTab.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedPerson by viewModel.selectedPerson.collectAsState()

    if (activeTab == ActiveTab.ALBUMS && selectedAlbum == null) {
        var albumSortBy by remember { mutableStateOf(viewModel.prefs.getAlbumSortBy("name")) }
        var albumSortDir by remember { mutableStateOf(viewModel.prefs.getAlbumSortDirection("asc")) }

        SortDialogShell(
            title = stringResource(R.string.sort_albums),
            onDismiss = onDismiss,
            onApply = {
                viewModel.updateAlbumSort(albumSortBy, albumSortDir)
                onDismiss()
            }
        ) {
            Text(
                text = stringResource(R.string.sort_albums_by),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = albumSortBy == "name", onClick = { albumSortBy = "name" }, label = { Text(stringResource(R.string.sort_name)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
                FilterChip(selected = albumSortBy == "date", onClick = { albumSortBy = "date" }, label = { Text(stringResource(R.string.sort_date)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
                FilterChip(selected = albumSortBy == "count", onClick = { albumSortBy = "count" }, label = { Text(stringResource(R.string.sort_count)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
                FilterChip(selected = albumSortBy == "random", onClick = { albumSortBy = "random" }, label = { Text(stringResource(R.string.sort_random)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
            }
            SortDirectionRow(sortBy = albumSortBy, sortDir = albumSortDir, onDirectionChange = { albumSortDir = it })
        }
    } else if (activeTab == ActiveTab.PERSONS && selectedPerson == null) {
        var personSortBy by remember { mutableStateOf(viewModel.prefs.getPersonSortBy("name")) }
        var personSortDir by remember { mutableStateOf(viewModel.prefs.getPersonSortDirection("asc")) }

        SortDialogShell(
            title = stringResource(R.string.sort_persons),
            onDismiss = onDismiss,
            onApply = {
                viewModel.updatePersonSort(personSortBy, personSortDir)
                onDismiss()
            }
        ) {
            Text(
                text = stringResource(R.string.sort_persons_by),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = personSortBy == "name", onClick = { personSortBy = "name" }, label = { Text(stringResource(R.string.sort_name)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
                FilterChip(selected = personSortBy == "count", onClick = { personSortBy = "count" }, label = { Text(stringResource(R.string.sort_count)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
                FilterChip(selected = personSortBy == "random", onClick = { personSortBy = "random" }, label = { Text(stringResource(R.string.sort_random)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
            }
            SortDirectionRow(sortBy = personSortBy, sortDir = personSortDir, onDirectionChange = { personSortDir = it })
        }
    } else {
        val isInsideAlbum = activeTab == ActiveTab.ALBUMS && selectedAlbum != null
        val isInsidePerson = activeTab == ActiveTab.PERSONS && selectedPerson != null
        val isGallery = activeTab == ActiveTab.GALLERY

        val isDateMode = viewModel.galleryViewMode.collectAsState().value == GalleryViewMode.DATE
        val defaultFSort = if (isDateMode) "date" else "name"
        val defaultFDir = if (isDateMode) "desc" else "asc"
        val defaultMSort = "date"
        val defaultMDir = if (isDateMode) "desc" else "asc"

        val currentPath = viewModel.getSortPath()
        val globalPath = if (isDateMode) "date_mode_global" else "global"

        val g_fSort = viewModel.prefs.getFolderSortBy(globalPath, defaultFSort)
        val g_fDir = viewModel.prefs.getFolderSortDirection(globalPath, defaultFDir)
        val g_mSort = viewModel.prefs.getMediaSortBy(globalPath, defaultMSort)
        val g_mDir = viewModel.prefs.getMediaSortDirection(globalPath, defaultMDir)

        var folderSortBy by remember {
            mutableStateOf(
                if (viewModel.prefs.hasFolderSort(currentPath)) viewModel.prefs.getFolderSortBy(currentPath, g_fSort)
                else g_fSort
            )
        }
        var folderSortDir by remember {
            mutableStateOf(
                if (viewModel.prefs.hasFolderSort(currentPath)) viewModel.prefs.getFolderSortDirection(currentPath, g_fDir)
                else g_fDir
            )
        }

        var mediaSortBy by remember {
            mutableStateOf(
                if (viewModel.prefs.hasMediaSort(currentPath)) viewModel.prefs.getMediaSortBy(currentPath, g_mSort)
                else g_mSort
            )
        }
        var mediaSortDir by remember {
            mutableStateOf(
                if (viewModel.prefs.hasMediaSort(currentPath)) viewModel.prefs.getMediaSortDirection(currentPath, g_mDir)
                else g_mDir
            )
        }

        var currentFolderOnly by remember { mutableStateOf(true) }

        val dialogTitle = when {
            isInsideAlbum -> stringResource(R.string.sort_album_media)
            isInsidePerson -> stringResource(R.string.sort_person_media)
            else -> stringResource(R.string.sort_gallery)
        }

        val checkboxLabel = when {
            isInsideAlbum -> stringResource(R.string.this_album_only)
            isInsidePerson -> stringResource(R.string.this_person_only)
            viewModel.currentPath.isBlank() -> stringResource(R.string.only_root_folder)
            else -> stringResource(R.string.only_this_folder)
        }

        SortDialogShell(
            title = dialogTitle,
            onDismiss = onDismiss,
            onApply = {
                viewModel.updateSort(folderSortBy, folderSortDir, mediaSortBy, mediaSortDir, currentFolderOnly)
                onDismiss()
            }
        ) {
            if (isGallery) {
                Text(
                    text = stringResource(R.string.sort_folders_by),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = folderSortBy == "name", onClick = { folderSortBy = "name" }, label = { Text(stringResource(R.string.sort_name)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
                    FilterChip(selected = folderSortBy == "date", onClick = { folderSortBy = "date" }, label = { Text(stringResource(R.string.sort_date)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
                    FilterChip(selected = folderSortBy == "count", onClick = { folderSortBy = "count" }, label = { Text(stringResource(R.string.sort_count)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
                    FilterChip(selected = folderSortBy == "random", onClick = { folderSortBy = "random" }, label = { Text(stringResource(R.string.sort_random)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
                }
                SortDirectionRow(sortBy = folderSortBy, sortDir = folderSortDir, onDirectionChange = { folderSortDir = it })
                Spacer(modifier = Modifier.height(14.dp))
            }

            Text(
                text = stringResource(R.string.sort_media_by),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = mediaSortBy == "name", onClick = { mediaSortBy = "name" }, label = { Text(stringResource(R.string.sort_name)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
                FilterChip(selected = mediaSortBy == "date", onClick = { mediaSortBy = "date" }, label = { Text(stringResource(R.string.sort_date)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
                FilterChip(selected = mediaSortBy == "random", onClick = { mediaSortBy = "random" }, label = { Text(stringResource(R.string.sort_random)) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)))
            }
            SortDirectionRow(sortBy = mediaSortBy, sortDir = mediaSortDir, onDirectionChange = { mediaSortDir = it })

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .tvFocus(shape = RoundedCornerShape(12.dp))
                    .clickable { currentFolderOnly = !currentFolderOnly }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(checked = currentFolderOnly, onCheckedChange = { currentFolderOnly = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = checkboxLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
