package com.example.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptivePullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    if (isTv) {
        Box(modifier = modifier, content = content)
    } else {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            indicator = {}, // Eliminates the upper area indicator completely
        ) {
            content()

            // Keep only the loading indicator in the center during refresh
            if (isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                        shadowElevation = 6.dp,
                        tonalElevation = 6.dp
                    ) {
                        Box(modifier = Modifier.padding(14.dp)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
fun Modifier.tvFocus(
    shape: Shape = RoundedCornerShape(12.dp),
    showBorder: Boolean = true
): Modifier = this.composed {
    var isFocused by remember { mutableStateOf(false) }

    val borderModifier = if (showBorder && isFocused) {
        Modifier.border(4.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier
    }

    this
        .onFocusChanged { isFocused = it.isFocused }
        .then(borderModifier)
        .graphicsLayer {
            // Removed shadowElevation to avoid gray border effect in dark mode
            this.shape = shape
            clip = true
        }
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
        when (event.key) {
            Key.DirectionLeft -> {
                onValueChange((value - stepSize).coerceIn(valueRange))
                true
            }
            Key.DirectionRight -> {
                onValueChange((value + stepSize).coerceIn(valueRange))
                true
            }
            else -> false
        }
    } else {
        // Consume ACTION_UP for left/right
        event.key == Key.DirectionLeft || event.key == Key.DirectionRight
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
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                    startY = 100f
                )
            )
    )
    Column(
        modifier = modifier
            .padding(8.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.Start
    ) {
        if (count.isNotBlank()) {
            Text(
                text = count,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
        }
        Text(
            text = name,
            fontSize = 13.sp,
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
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    Text(
                        text = "/",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                Text(
                    text = folderName,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .tvFocus(shape = RoundedCornerShape(6.dp))
                        .clickable { onBreadcrumbClick(index) }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.error_occurred),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String
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
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = stringResource(R.string.empty_cd),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyMedium
            )
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
        // --- 1. SORT ALBUMS (Root albums list - completely isolated from folders & persons) ---
        var albumSortBy by remember { mutableStateOf(viewModel.prefs.getAlbumSortBy("name")) }
        var albumSortDir by remember { mutableStateOf(viewModel.prefs.getAlbumSortDirection("asc")) }

        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.sort_albums), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(stringResource(R.string.sort_albums_by), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(selected = albumSortBy == "name", onClick = { albumSortBy = "name" }, label = { Text(stringResource(R.string.sort_name)) })
                        FilterChip(selected = albumSortBy == "date", onClick = { albumSortBy = "date" }, label = { Text(stringResource(R.string.sort_date)) })
                        FilterChip(selected = albumSortBy == "count", onClick = { albumSortBy = "count" }, label = { Text(stringResource(R.string.sort_count)) })
                        FilterChip(selected = albumSortBy == "random", onClick = { albumSortBy = "random" }, label = { Text(stringResource(R.string.sort_random)) })
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = albumSortDir == "asc", onClick = { albumSortDir = "asc" }, label = { if (albumSortBy == "name") Text(stringResource(R.string.sort_a_to_z)) else Text(stringResource(R.string.sort_ascending)) })
                        FilterChip(selected = albumSortDir == "desc", onClick = { albumSortDir = "desc" }, label = { if (albumSortBy == "name") Text(stringResource(R.string.sort_z_to_a)) else Text(stringResource(R.string.sort_descending)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = {
                            viewModel.updateAlbumSort(albumSortBy, albumSortDir)
                            onDismiss()
                        }) {
                            Text(stringResource(R.string.apply))
                        }
                    }
                }
            }
        }
    } else if (activeTab == ActiveTab.PERSONS && selectedPerson == null) {
        // --- 2. SORT PERSONS (Root persons list - completely isolated from folders & albums) ---
        var personSortBy by remember { mutableStateOf(viewModel.prefs.getPersonSortBy("name")) }
        var personSortDir by remember { mutableStateOf(viewModel.prefs.getPersonSortDirection("asc")) }

        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.sort_persons), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(stringResource(R.string.sort_persons_by), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(selected = personSortBy == "name", onClick = { personSortBy = "name" }, label = { Text(stringResource(R.string.sort_name)) })
                        FilterChip(selected = personSortBy == "count", onClick = { personSortBy = "count" }, label = { Text(stringResource(R.string.sort_count)) })
                        FilterChip(selected = personSortBy == "random", onClick = { personSortBy = "random" }, label = { Text(stringResource(R.string.sort_random)) })
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = personSortDir == "asc", onClick = { personSortDir = "asc" }, label = { if (personSortBy == "name") Text(stringResource(R.string.sort_a_to_z)) else Text(stringResource(R.string.sort_ascending)) })
                        FilterChip(selected = personSortDir == "desc", onClick = { personSortDir = "desc" }, label = { if (personSortBy == "name") Text(stringResource(R.string.sort_z_to_a)) else Text(stringResource(R.string.sort_descending)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = {
                            viewModel.updatePersonSort(personSortBy, personSortDir)
                            onDismiss()
                        }) {
                            Text(stringResource(R.string.apply))
                        }
                    }
                }
            }
        }
    } else {
        // --- 3. GALLERY / ALBUM MEDIA / PERSON MEDIA ---
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

        var currentFolderOnly by remember {
            mutableStateOf(true)
        }

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

        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(dialogTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isGallery) {
                        Text(stringResource(R.string.sort_folders_by), style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(selected = folderSortBy == "name", onClick = { folderSortBy = "name" }, label = { Text(stringResource(R.string.sort_name)) })
                            FilterChip(selected = folderSortBy == "date", onClick = { folderSortBy = "date" }, label = { Text(stringResource(R.string.sort_date)) })
                            FilterChip(selected = folderSortBy == "count", onClick = { folderSortBy = "count" }, label = { Text(stringResource(R.string.sort_count)) })
                            FilterChip(selected = folderSortBy == "random", onClick = { folderSortBy = "random" }, label = { Text(stringResource(R.string.sort_random)) })
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = folderSortDir == "asc", onClick = { folderSortDir = "asc" }, label = { Text(stringResource(R.string.sort_ascending)) })
                            FilterChip(selected = folderSortDir == "desc", onClick = { folderSortDir = "desc" }, label = { Text(stringResource(R.string.sort_descending)) })
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(stringResource(R.string.sort_media_by), style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mediaSortBy == "name", onClick = { mediaSortBy = "name" }, label = { Text(stringResource(R.string.sort_name)) })
                        FilterChip(selected = mediaSortBy == "date", onClick = { mediaSortBy = "date" }, label = { Text(stringResource(R.string.sort_date)) })
                        FilterChip(selected = mediaSortBy == "random", onClick = { mediaSortBy = "random" }, label = { Text(stringResource(R.string.sort_random)) })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mediaSortDir == "asc", onClick = { mediaSortDir = "asc" }, label = { if (mediaSortBy == "name") Text(stringResource(R.string.sort_a_to_z)) else Text(stringResource(R.string.sort_ascending)) })
                        FilterChip(selected = mediaSortDir == "desc", onClick = { mediaSortDir = "desc" }, label = { if (mediaSortBy == "name") Text(stringResource(R.string.sort_z_to_a)) else Text(stringResource(R.string.sort_descending)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentFolderOnly = !currentFolderOnly }
                    ) {
                        Checkbox(checked = currentFolderOnly, onCheckedChange = { currentFolderOnly = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(checkboxLabel, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = {
                            viewModel.updateSort(folderSortBy, folderSortDir, mediaSortBy, mediaSortDir, currentFolderOnly)
                            onDismiss()
                        }) {
                            Text(stringResource(R.string.apply))
                        }
                    }
                }
            }
        }
    }
}
