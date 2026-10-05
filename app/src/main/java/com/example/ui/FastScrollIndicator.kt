package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ApiMedia
import com.example.data.ApiSubFolder
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FastScrollIndicator(
    state: LazyGridState,
    groupedMedia: Map<String, List<ApiMedia>>,
    subfolders: List<ApiSubFolder> = emptyList(),
    subfoldersCount: Int = subfolders.size,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Calculate total grid items count (subfolders + headers + media items)
    val totalGridItemsCount = remember(subfoldersCount, groupedMedia) {
        var count = subfoldersCount
        for ((_, items) in groupedMedia) {
            count += 1 + items.size // 1 for header + items.size
        }
        count
    }

    if (totalGridItemsCount <= 0) return

    val totalItems = totalGridItemsCount.coerceAtLeast(1)
    val visibleItems = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val firstVisibleIndex = state.firstVisibleItemIndex

    // Compute normal scroll fraction (0.0 .. 1.0)
    val computedScrollFraction = if (totalItems <= visibleItems) 0f else {
        (firstVisibleIndex.toFloat() / (totalItems - visibleItems).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var trackHeightPx by remember { mutableStateOf(1f) }

    val activeFraction = if (isDragging && dragFraction != null) dragFraction!! else computedScrollFraction

    // Resolve current date/section label for active index
    val activeIndex = remember(activeFraction, totalItems) {
        (activeFraction * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
    }

    val currentDateLabel = remember(activeIndex, groupedMedia, subfolders, subfoldersCount) {
        if (groupedMedia.isEmpty() && subfoldersCount == 0) ""
        else if (activeIndex < subfoldersCount) {
            val folder = subfolders.getOrNull(activeIndex)
            if (folder != null) "Folder: ${folder.name}" else "Folders"
        } else {
            var indexTracker = subfoldersCount
            var label = ""
            for ((header, items) in groupedMedia) {
                val groupStart = indexTracker
                val groupEnd = indexTracker + 1 + items.size
                if (activeIndex in groupStart until groupEnd) {
                    val itemOffset = activeIndex - groupStart
                    if (itemOffset == 0) {
                        label = header
                    } else {
                        val mediaIndex = itemOffset - 1
                        val media = items.getOrNull(mediaIndex)
                        if (media != null && media.metadata?.creationDate != null) {
                            val formattedDate = DateUtils.formatMediaDate(
                                media.metadata?.creationDate,
                                media.metadata?.creationDateOffset,
                                "MMMM yyyy"
                            )
                            label = formattedDate
                        } else {
                            label = header
                        }
                    }
                    break
                }
                indexTracker = groupEnd
            }
            if (label.isEmpty()) groupedMedia.keys.firstOrNull() ?: "" else label
        }
    }

    // Auto-hide visibility
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress, isDragging) {
        if (state.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            kotlinx.coroutines.delay(1500)
            isVisible = false
        }
    }

    val thumbHeightDp = 48.dp
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
    val maxThumbOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val currentThumbOffsetY = activeFraction * maxThumbOffsetPx

    fun updateDragPosition(touchY: Float) {
        val clampedY = touchY.coerceIn(0f, trackHeightPx)
        val fraction = (clampedY / trackHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        dragFraction = fraction
        val targetItem = (fraction * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
        coroutineScope.launch {
            state.scrollToItem(targetItem)
        }
    }

    AnimatedVisibility(
        visible = isVisible && totalItems > visibleItems,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth(Alignment.End)
                .onGloballyPositioned { coordinates ->
                    if (coordinates.size.height > 0) {
                        trackHeightPx = coordinates.size.height.toFloat()
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isDragging = true
                        updateDragPosition(down.position.y)

                        while (true) {
                            val event = awaitPointerEvent()
                            val pointerChange = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointerChange.pressed) break
                            pointerChange.consume()
                            updateDragPosition(pointerChange.position.y)
                        }

                        isDragging = false
                        dragFraction = null
                    }
                }
        ) {
            // Popup Date/Section Bubble & Thumb Handle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, currentThumbOffsetY.roundToInt()) }
                    .padding(end = 6.dp)
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

                // Draggable Thumb Handle
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isDragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    shadowElevation = if (isDragging) 8.dp else 4.dp,
                    modifier = Modifier
                        .width(32.dp)
                        .height(thumbHeightDp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = "Fast scroll thumb",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}


