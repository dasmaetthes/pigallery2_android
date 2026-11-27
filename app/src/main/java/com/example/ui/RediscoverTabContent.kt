package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.util.rememberTvCapabilities
import kotlinx.coroutines.delay

@Composable
fun RediscoverTabContent(
    viewModel: GalleryViewModel,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    val rediscoverState by viewModel.rediscoverState.collectAsState()
    val isRefreshing by viewModel.isRediscoverRefreshing.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val selectedMediaForShare by viewModel.selectedMediaForShare.collectAsState()
    val cookies = viewModel.getCookiesHeader()

    val itemsPerRowPortrait by viewModel.itemsPerRowPortrait.collectAsState()
    val itemsPerRowLandscape by viewModel.itemsPerRowLandscape.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val itemsPerRow = if (isLandscape) itemsPerRowLandscape else itemsPerRowPortrait
    val spacing by viewModel.spacing.collectAsState()
    val cornerRadius by viewModel.cornerRadius.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()

    AdaptivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshRediscover() },
        modifier = Modifier.fillMaxSize()
    ) {
        when (val state = rediscoverState) {
            is RediscoverUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is RediscoverUiState.Error -> {
                ErrorStateView(
                    message = state.message,
                    actionFocusRequester = firstItemFocusRequester
                ) {
                    viewModel.loadRediscover()
                }
            }
            is RediscoverUiState.Success -> {
                val grouped = state.groupedMedia
                if (grouped.isEmpty()) {
                    EmptyStateView(
                        title = stringResource(R.string.nothing_to_rediscover),
                        subtitle = stringResource(R.string.nothing_to_rediscover_subtitle),
                        icon = Icons.Outlined.History,
                        illustration = EmptyStateIllustration.MEMORIES,
                        guidanceText = stringResource(R.string.empty_guidance_rediscover),
                        actionLabel = stringResource(R.string.empty_action_open_settings),
                        onAction = {
                            viewModel.setSelectedSettingsTab(1)
                            viewModel.setActiveTab(ActiveTab.SETTINGS)
                        },
                        actionFocusRequester = firstItemFocusRequester
                    )
                } else {
                    val expandedYears by viewModel.rediscoverExpandedYears.collectAsState()

                    val tvCapabilities = rememberTvCapabilities()
                    val isTv = tvCapabilities.isTvMode
                    val effectiveIsSelectMode = tvCapabilities.isSelectModeActive(isSelectMode)
                    val scrollKey = "rediscover"
                    val gridState = rememberPersistedLazyGridState(viewModel, scrollKey)
                    var hasAutoFocused by remember { mutableStateOf(false) }

                    LaunchedEffect(grouped.isNotEmpty(), isTv) {
                        if (isTv && grouped.isNotEmpty() && !hasAutoFocused && gridState.firstVisibleItemIndex == 0) {
                            hasAutoFocused = true
                            try {
                                delay(100)
                                firstItemFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    }

                    val gridPadding = if (isTv) 20.dp else 16.dp
                    val effectiveSpacing = if (isTv) spacing.dp.coerceAtLeast(10.dp) else spacing.dp

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(itemsPerRow),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusGroup(),
                        contentPadding = PaddingValues(gridPadding),
                        verticalArrangement = Arrangement.spacedBy(effectiveSpacing),
                        horizontalArrangement = Arrangement.spacedBy(effectiveSpacing)
                    ) {
                        grouped.forEach { (year, mediaList) ->
                            val isExpanded = year in expandedYears
                            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                            val diff = currentYear - year
                            val isFirstYear = year == grouped.keys.first()

                            item(key = "rediscover_year_$year", span = { GridItemSpan(itemsPerRow) }) {
                                var isFocused by remember { mutableStateOf(false) }
                                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                    val hasMore = mediaList.size > itemsPerRow
                                    val focusModifier = if (isFirstYear) Modifier.focusRequester(firstItemFocusRequester) else Modifier

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .then(focusModifier)
                                            .onFocusChanged { isFocused = it.hasFocus }
                                            .clip(RoundedCornerShape(12.dp))
                                            .tvFocus(shape = RoundedCornerShape(12.dp), showBorder = true)
                                            .then(if (hasMore) Modifier.clickable { viewModel.toggleRediscoverYear(year) } else Modifier.focusable())
                                            .background(
                                                color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                            )
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = if (diff == 1) stringResource(R.string.one_year_ago, year) else stringResource(R.string.years_ago, diff, year),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = stringResource(R.string.items_count, mediaList.size),
                                                fontSize = 12.sp,
                                                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (hasMore) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                                                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            val displayList = if (isExpanded) mediaList else mediaList.take(itemsPerRow)
                            items(
                                count = displayList.size,
                                key = { index ->
                                    val m = displayList[index]
                                    "rediscover_${year}_${m.id ?: "none"}_${m.parentPath ?: ""}_${m.name}_$index"
                                }
                            ) { index ->
                                val media = displayList[index]
                                MediaGridCard(
                                    media = media,
                                    aspectRatio = aspectRatio,
                                    cornerRadiusDp = cornerRadius.dp,
                                    isSelectMode = effectiveIsSelectMode,
                                    isSelected = tvCapabilities.allowMultiSelect && selectedMediaForShare.contains(media),
                                    cookies = cookies,
                                    viewModel = viewModel,
                                    onMediaClick = { clicked -> viewModel.selectMedia(clicked, mediaList) },
                                    onToggleSelect = { if (tvCapabilities.allowMultiSelect) viewModel.toggleSelectMedia(media) },
                                    fallbackSquareAspectRatio = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
