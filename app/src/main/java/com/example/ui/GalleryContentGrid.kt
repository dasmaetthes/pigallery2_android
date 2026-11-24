package com.example.ui

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.ApiMedia
import com.example.data.ApiSubFolder
import com.example.findActivity
import com.example.util.BiometricHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryContentGrid(
    subfolders: List<ApiSubFolder>,
    mediaList: List<ApiMedia>,
    viewModel: GalleryViewModel,
    onFolderClick: (ApiSubFolder) -> Unit,
    onMediaClick: (ApiMedia) -> Unit,
    state: LazyGridState = rememberLazyGridState(),
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    val context = LocalContext.current
    val cookies = viewModel.getCookiesHeader()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val selectedMediaForShare by viewModel.selectedMediaForShare.collectAsState()

    val spacing by viewModel.spacing.collectAsState()
    val itemsPerRowPortrait by viewModel.itemsPerRowPortrait.collectAsState()
    val itemsPerRowLandscape by viewModel.itemsPerRowLandscape.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val itemsPerRow = if (isLandscape) itemsPerRowLandscape else itemsPerRowPortrait
    val cornerRadius by viewModel.cornerRadius.collectAsState()
    val showCount by viewModel.showDirectoryItemCount.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()

    val spacingDp = spacing.dp
    val cornerRadiusDp = cornerRadius.dp

    val currentMediaSort = viewModel.getCurrentMediaSortBy().lowercase()
    val isDateSort = currentMediaSort == "date"
    val unknownLabel = stringResource(R.string.unknown)
    val protectedFolders by viewModel.protectedFolders.collectAsState()

    val smallGroupedMedia = remember(mediaList, isDateSort, unknownLabel) {
        if (!isDateSort || mediaList.isEmpty() || mediaList.size > 150) {
            null
        } else {
            val grouped = LinkedHashMap<String, MutableList<ApiMedia>>()
            for (media in mediaList) {
                val key = DateUtils.formatMediaDate(media.metadata?.creationDate, media.metadata?.creationDateOffset, "MMMM yyyy", unknownLabel)
                grouped.getOrPut(key) { mutableListOf() }.add(media)
            }
            grouped
        }
    }

    val largeGroupedMedia by produceState<Map<String, List<ApiMedia>>>(
        initialValue = emptyMap(),
        key1 = mediaList,
        key2 = isDateSort,
        key3 = unknownLabel
    ) {
        value = if (!isDateSort || mediaList.size <= 150) {
            emptyMap()
        } else {
            withContext(Dispatchers.Default) {
                val grouped = LinkedHashMap<String, MutableList<ApiMedia>>()
                for (media in mediaList) {
                    val key = DateUtils.formatMediaDate(media.metadata?.creationDate, media.metadata?.creationDateOffset, "MMMM yyyy", unknownLabel)
                    grouped.getOrPut(key) { mutableListOf() }.add(media)
                }
                grouped
            }
        }
    }

    val groupedMedia: Map<String, List<ApiMedia>> = smallGroupedMedia ?: largeGroupedMedia

    val isTv = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    LaunchedEffect(subfolders, mediaList) {
        if (isTv && (subfolders.isNotEmpty() || mediaList.isNotEmpty()) && state.firstVisibleItemIndex == 0) {
            try {
                delay(100)
                firstItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(itemsPerRow),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(spacingDp),
            horizontalArrangement = Arrangement.spacedBy(spacingDp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Render Subfolders
            items(count = subfolders.size, key = { index -> "folder_${subfolders[index].path}_$index" }) { index ->
                val folder = subfolders[index]
                val coverUrl = remember(folder.path, folder.cache?.cover) {
                    viewModel.getFolderCoverUrl(folder)
                }
                val currentPathSnapshot = viewModel.currentPath
                val isFolderProtected = remember(folder.path, currentPathSnapshot, protectedFolders) {
                    viewModel.isFolderProtected(folder.path)
                }
                val isCurrentFolderAlreadyProtected = remember(currentPathSnapshot, protectedFolders) {
                    currentPathSnapshot.isNotEmpty() && viewModel.isFolderProtected(currentPathSnapshot)
                }
                val shouldShowLockAndBlur = isFolderProtected && !isCurrentFolderAlreadyProtected

                val handleFolderCardClick = {
                    if (shouldShowLockAndBlur) {
                        val activity = context.findActivity() as? FragmentActivity
                        if (activity != null) {
                            BiometricHelper.authenticate(
                                activity = activity,
                                title = context.getString(R.string.protected_folder),
                                subtitle = context.getString(R.string.authenticate_open_folder, folder.name),
                                onSuccess = { onFolderClick(folder) },
                                onError = { err ->
                                    if (err != BiometricHelper.ERROR_CANCELED) {
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        } else {
                            onFolderClick(folder)
                        }
                    } else {
                        onFolderClick(folder)
                    }
                }

                val focusModifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(cornerRadiusDp))
                        .then(focusModifier)
                        .then(
                            if (!isSelectMode) {
                                Modifier
                                    .tvFocus(shape = RoundedCornerShape(cornerRadiusDp))
                                    .clickable { handleFolderCardClick() }
                            } else {
                                Modifier.graphicsLayer(alpha = 0.4f)
                            }
                        ),
                    shape = RoundedCornerShape(cornerRadiusDp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (coverUrl != null) {
                            val imageRequest = remember(coverUrl, cookies) {
                                val cacheKey = "folder_${folder.path}"
                                val builder = ImageRequest.Builder(context)
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .memoryCacheKey(cacheKey)
                                    .diskCacheKey(cacheKey)
                                    .data(coverUrl)
                                    .crossfade(true)
                                if (cookies.isNotEmpty()) {
                                    builder.addHeader("Cookie", cookies)
                                }
                                builder.build()
                            }

                            AsyncImage(
                                model = imageRequest,
                                contentDescription = folder.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (shouldShowLockAndBlur) Modifier.blur(25.dp) else Modifier)
                            )

                            if (shouldShowLockAndBlur) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = stringResource(R.string.protected_folder),
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            ThumbnailOverlay(
                                name = folder.name,
                                count = if (showCount && folder.mediaCount != null) "${folder.mediaCount}" else ""
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = stringResource(R.string.folder),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                if (showCount && folder.mediaCount != null) {
                                    Text(
                                        text = "${folder.mediaCount}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                }
                                Text(
                                    text = folder.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Render Media grid
            val hasSubfolders = subfolders.isNotEmpty()
            if (isDateSort) {
                groupedMedia.forEach { (monthYear, mediaItems) ->
                    item(key = "header_$monthYear", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = monthYear,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .padding(start = 4.dp, top = 24.dp, bottom = 8.dp, end = 4.dp)
                                .fillMaxWidth()
                        )
                    }
                    items(count = mediaItems.size, key = { index -> "media_${mediaItems[index].id ?: "none"}_${mediaItems[index].parentPath ?: ""}_${mediaItems[index].name}_$index" }) { index ->
                        val media = mediaItems[index]
                        val isFirstOverallMedia = !hasSubfolders && monthYear == groupedMedia.keys.firstOrNull() && index == 0
                        GalleryMediaCardItem(
                            media = media,
                            aspectRatio = aspectRatio,
                            cornerRadiusDp = cornerRadiusDp,
                            isSelectMode = isSelectMode,
                            isSelected = selectedMediaForShare.contains(media),
                            cookies = cookies,
                            isFirstOverallMedia = isFirstOverallMedia,
                            firstItemFocusRequester = firstItemFocusRequester,
                            onMediaClick = onMediaClick,
                            onToggleSelect = { viewModel.toggleSelectMedia(media) },
                            viewModel = viewModel
                        )
                    }
                }
            } else {
                items(count = mediaList.size, key = { index -> "media_${mediaList[index].id ?: "none"}_${mediaList[index].parentPath ?: ""}_${mediaList[index].name}_$index" }) { index ->
                    val media = mediaList[index]
                    val isFirstOverallMedia = !hasSubfolders && index == 0
                    GalleryMediaCardItem(
                        media = media,
                        aspectRatio = aspectRatio,
                        cornerRadiusDp = cornerRadiusDp,
                        isSelectMode = isSelectMode,
                        isSelected = selectedMediaForShare.contains(media),
                        cookies = cookies,
                        isFirstOverallMedia = isFirstOverallMedia,
                        firstItemFocusRequester = firstItemFocusRequester,
                        onMediaClick = onMediaClick,
                        onToggleSelect = { viewModel.toggleSelectMedia(media) },
                        viewModel = viewModel
                    )
                }
            }
        }

        FastScrollIndicator(
            state = state,
            groupedMedia = groupedMedia,
            subfolders = subfolders,
            subfoldersCount = subfolders.size,
            hasHeaders = isDateSort,
            mediaList = mediaList,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryMediaCardItem(
    media: ApiMedia,
    aspectRatio: Float,
    cornerRadiusDp: Dp,
    isSelectMode: Boolean,
    isSelected: Boolean,
    cookies: String,
    isFirstOverallMedia: Boolean,
    firstItemFocusRequester: FocusRequester,
    onMediaClick: (ApiMedia) -> Unit,
    onToggleSelect: () -> Unit,
    viewModel: GalleryViewModel
) {
    val context = LocalContext.current
    val focusModifier = if (isFirstOverallMedia) Modifier.focusRequester(firstItemFocusRequester) else Modifier

    val cardModifier = Modifier
        .then(focusModifier)
        .fillMaxWidth()
        .let { modifier ->
            if (aspectRatio > 0f) {
                modifier.aspectRatio(aspectRatio)
            } else {
                modifier
            }
        }
        .clip(RoundedCornerShape(cornerRadiusDp))
        .tvFocus(shape = RoundedCornerShape(cornerRadiusDp))
        .combinedClickable(
            onClick = {
                if (isSelectMode) {
                    onToggleSelect()
                } else {
                    onMediaClick(media)
                }
            },
            onLongClick = {
                onToggleSelect()
            }
        )

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(cornerRadiusDp),
        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val thumbnailUrl = remember(media.id, media.parentPath, media.name) {
                viewModel.getThumbnailUrl(media)
            }
            val imageRequest = remember(thumbnailUrl, cookies) {
                val cacheKey = "thumb_${media.id}_${media.name}"
                ImageRequest.Builder(context)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .data(thumbnailUrl)
                    .apply {
                        if (cookies.isNotEmpty()) {
                            setHeader("Cookie", cookies)
                        }
                    }
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = media.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Live Photo badge (top-start)
            if (media.isLivePhoto && !media.isVideo) {
                LivePhotoBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )
            }

            // Render video overlay icon
            if (media.isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = stringResource(R.string.video_cd),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(36.dp)
                    )
                }

                val durationStr = formatVideoDuration(media.metadata?.duration)
                if (durationStr.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = durationStr,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Render Checkbox Indicator if in Select Mode or Item is Selected
            if (isSelectMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isSelected) Color.Black.copy(alpha = 0.2f) else Color.Transparent)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.selected_cd),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.White, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .border(2.dp, Color.White, CircleShape)
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        )
                    }
                }
            }
        }
    }
}
