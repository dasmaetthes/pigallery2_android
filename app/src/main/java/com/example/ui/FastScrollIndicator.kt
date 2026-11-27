package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ApiMedia
import com.example.data.ApiSubFolder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.math.roundToInt

@Composable
fun FastScrollIndicator(
    state: LazyGridState,
    groupedMedia: Map<String, List<ApiMedia>>,
    modifier: Modifier = Modifier,
    subfolders: List<ApiSubFolder> = emptyList(),
    subfoldersCount: Int = subfolders.size,
    hasHeaders: Boolean = true,
    mediaList: List<ApiMedia> = emptyList()
) {
    val isTv = com.example.util.rememberIsAndroidTv()
    if (isTv) return

    val context = LocalContext.current
    val unknownLabel = stringResource(R.string.unknown)
    val foldersLabel = stringResource(R.string.folders_label)
    val density = LocalDensity.current

    // Calculate total grid items count (subfolders + headers + media items)
    val totalGridItemsCount = remember(subfoldersCount, groupedMedia, hasHeaders, mediaList.size) {
        var count = subfoldersCount
        if (hasHeaders) {
            for ((_, items) in groupedMedia) {
                count += 1 + items.size // 1 for header + items.size
            }
        } else {
            count += mediaList.size
        }
        count
    }

    if (totalGridItemsCount <= 0) return

    val totalItems = totalGridItemsCount.coerceAtLeast(1)
    var isDragging by remember { mutableStateOf(false) }

    // Only activate fast-scroll on lists with substantial scroll overflow (>= 2 full viewports)
    // so short/medium album lists never spawn an edge handle that could interfere with normal scrolling.
    val hasSubstantialOverflow by remember(state, totalItems) {
        derivedStateOf {
            val layoutTotal = state.layoutInfo.totalItemsCount.takeIf { it > 0 } ?: totalItems
            val visibleItems = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            isDragging || (layoutTotal > visibleItems * 2 && (state.canScrollForward || state.canScrollBackward))
        }
    }

    val maxScrollableFirstIndex by remember(state, totalItems) {
        derivedStateOf {
            val layoutTotal = state.layoutInfo.totalItemsCount.takeIf { it > 0 } ?: totalItems
            val visibleItems = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            (layoutTotal - visibleItems).coerceAtLeast(1)
        }
    }

    val computedScrollFraction by remember(state, totalItems) {
        derivedStateOf {
            when {
                !state.canScrollBackward -> 0f
                !state.canScrollForward -> 1f
                else -> (state.firstVisibleItemIndex.toFloat() / maxScrollableFirstIndex.toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var trackTopInRoot by remember { mutableFloatStateOf(0f) }
    var thumbCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Serialize fast-scroll jumps via a CONFLATED channel + collectLatest so rapid upward
    // drags always execute the latest target (including repeated 0,0) and never race backward.
    val dragTargetChannel = remember(state) { Channel<Int>(Channel.CONFLATED) }
    LaunchedEffect(state, dragTargetChannel) {
        dragTargetChannel.receiveAsFlow().collectLatest { target ->
            state.scrollToItem(index = target, scrollOffset = 0)
        }
    }

    val activeFraction = if (isDragging) (dragFraction ?: computedScrollFraction) else computedScrollFraction

    // Resolve current date/section label for active index
    val activeIndex = remember(activeFraction, totalItems) {
        (activeFraction * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
    }

    val currentDateLabel = remember(activeIndex, groupedMedia, subfolders, subfoldersCount, hasHeaders, mediaList, unknownLabel, foldersLabel) {
        if (groupedMedia.isEmpty() && subfoldersCount == 0 && mediaList.isEmpty()) ""
        else if (activeIndex < subfoldersCount) {
            val folder = subfolders.getOrNull(activeIndex)
            if (folder != null) context.getString(R.string.folder_colon_name, folder.name) else foldersLabel
        } else {
            if (hasHeaders) {
                var indexTracker = subfoldersCount
                var label = ""
                for ((header, items) in groupedMedia) {
                    val groupStart = indexTracker
                    val groupEnd = indexTracker + 1 + items.size
                    if (activeIndex in groupStart until groupEnd) {
                        val itemOffset = activeIndex - groupStart
                        val media = if (itemOffset == 0) items.firstOrNull() else items.getOrNull(itemOffset - 1)
                        if (media != null && media.metadata?.creationDate != null) {
                            label = DateUtils.formatMediaDate(
                                media.metadata?.creationDate,
                                media.metadata?.creationDateOffset,
                                "d. MMMM yyyy",
                                unknownLabel
                            )
                        } else {
                            label = header
                        }
                        break
                    }
                    indexTracker = groupEnd
                }
                if (label.isEmpty()) groupedMedia.keys.firstOrNull() ?: "" else label
            } else {
                val mediaIndex = activeIndex - subfoldersCount
                val media = mediaList.getOrNull(mediaIndex)
                media?.name ?: ""
            }
        }
    }

    // Auto-hide visibility promptly once scrolling settles
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress, isDragging) {
        if (state.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            kotlinx.coroutines.delay(900)
            isVisible = false
        }
    }

    val thumbHeightDp = 48.dp
    val thumbTouchHeightDp = 56.dp
    val thumbTouchHeightPx = with(density) { thumbTouchHeightDp.toPx() }
    val maxThumbOffsetPx = (trackHeightPx - thumbTouchHeightPx).coerceAtLeast(0f)
    val currentThumbOffsetY = activeFraction * maxThumbOffsetPx

    // Keep always-fresh references inside pointerInput without restarting mid-drag
    val latestMaxThumbOffsetPx = rememberUpdatedState(maxThumbOffsetPx)
    val latestTrackTopInRoot = rememberUpdatedState(trackTopInRoot)
    val latestMaxScrollableFirstIndex = rememberUpdatedState(maxScrollableFirstIndex)
    val latestTotalItems = rememberUpdatedState(
        state.layoutInfo.totalItemsCount.takeIf { it > 0 } ?: totalItems
    )

    // Always-mounted non-intercepting track container so track bounds are accurate before fade-in.
    // Normal touch scrolling anywhere outside the edge-docked thumb passes straight to LazyVerticalGrid.
    Box(
        modifier = modifier
            .fillMaxHeight()
            .wrapContentWidth(Alignment.End)
            .onGloballyPositioned { coordinates ->
                if (coordinates.size.height > 0) {
                    trackHeightPx = coordinates.size.height.toFloat()
                    trackTopInRoot = coordinates.positionInRoot().y
                }
            }
    ) {
        AnimatedVisibility(
            visible = isVisible && hasSubstantialOverflow && trackHeightPx > 0f,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            // Popup Date/Section Bubble & Edge-Docked Draggable Thumb Handle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .offset { IntOffset(0, currentThumbOffsetY.roundToInt()) }
            ) {
                // Floating Date/Section Bubble (only shown when actively dragging handle)
                if (isDragging && currentDateLabel.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shadowElevation = 6.dp,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = currentDateLabel,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }

                // Compact edge-docked drag handle flush against the right screen edge so normal
                // right-thumb scrolling across the rightmost grid column never hits the scrollbar.
                Box(
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier
                        .width(32.dp)
                        .height(thumbTouchHeightDp)
                        .testTag("fast_scroll_thumb")
                        .onGloballyPositioned { coords ->
                            thumbCoords = coords
                        }
                        .pointerInput(state) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Capture grab offset BEFORE touchSlop so upward drags have zero downward bias
                                val grabOffsetInThumbPx = down.position.y
                                val slopChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                                    change.consume()
                                } ?: return@awaitEachGesture

                                isDragging = true
                                try {
                                    fun updateDragPosition(localY: Float) {
                                        val coords = thumbCoords ?: return
                                        if (!coords.isAttached) return
                                        val maxOffset = latestMaxThumbOffsetPx.value
                                        if (maxOffset <= 0f) return

                                        val fingerRootY = coords.localToRoot(
                                            androidx.compose.ui.geometry.Offset(0f, localY)
                                        ).y
                                        val targetThumbTopPx = (fingerRootY - latestTrackTopInRoot.value - grabOffsetInThumbPx)
                                            .coerceIn(0f, maxOffset)
                                        val fraction = (targetThumbTopPx / maxOffset).coerceIn(0f, 1f)
                                        dragFraction = fraction

                                        val maxIndex = latestMaxScrollableFirstIndex.value
                                        val lastIndex = (latestTotalItems.value - 1).coerceAtLeast(0)
                                        val targetItem = when {
                                            fraction <= 0.03f -> 0
                                            fraction >= 0.97f -> lastIndex
                                            else -> (fraction * maxIndex).roundToInt().coerceIn(0, maxIndex)
                                        }
                                        dragTargetChannel.trySend(targetItem)
                                    }

                                    updateDragPosition(slopChange.position.y)
                                    verticalDrag(slopChange.id) { change ->
                                        change.consume()
                                        updateDragPosition(change.position.y)
                                    }
                                } finally {
                                    isDragging = false
                                    dragFraction = null
                                }
                            }
                        }
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                        color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isDragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        shadowElevation = if (isDragging) 8.dp else 4.dp,
                        modifier = Modifier
                            .width(if (isDragging) 32.dp else 26.dp)
                            .height(thumbHeightDp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = stringResource(R.string.fast_scroll_thumb),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
