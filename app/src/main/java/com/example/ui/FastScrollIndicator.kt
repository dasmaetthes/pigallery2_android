package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ApiMedia

@Composable
fun FastScrollIndicator(
    state: LazyGridState,
    groupedMedia: Map<String, List<ApiMedia>>,
    subfoldersCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val isScrollInProgress = state.isScrollInProgress
    val firstVisibleItemIndex = state.firstVisibleItemIndex

    val currentDateLabel = remember(firstVisibleItemIndex, groupedMedia, subfoldersCount) {
        if (groupedMedia.isEmpty()) ""
        else {
            var indexTracker = subfoldersCount
            var label = groupedMedia.keys.firstOrNull() ?: ""
            for ((header, items) in groupedMedia) {
                // Header item takes 1 slot
                indexTracker += 1
                if (firstVisibleItemIndex < indexTracker + items.size) {
                    label = header
                    break
                }
                indexTracker += items.size
            }
            label
        }
    }

    AnimatedVisibility(
        visible = isScrollInProgress && currentDateLabel.isNotEmpty(),
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier.padding(end = 12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 6.dp
        ) {
            Text(
                text = currentDateLabel,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
