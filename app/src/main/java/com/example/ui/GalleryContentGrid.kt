package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.data.ApiMedia
import com.example.data.ApiSubFolder
import com.example.findActivity
import com.example.util.BiometricHelper
import com.example.util.rememberTvCapabilities
import kotlinx.coroutines.delay

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

    val groupedMedia: Map<String, List<ApiMedia>> = remember(mediaList, isDateSort, unknownLabel) {
        if (!isDateSort || mediaList.isEmpty()) {
            emptyMap()
        } else {
            DateUtils.groupMediaByMonthYear(mediaList, unknownLabel)
        }
    }

    // Prevent LazyVerticalGrid key-anchoring from silently scrolling the user downward
    // when subfolders or newer items prepend above index 0 while the user was at the top.
    val firstItemKey = remember(subfolders, isDateSort, groupedMedia, mediaList) {
        when {
            subfolders.isNotEmpty() -> "folder_${subfolders.first().path}"
            isDateSort && groupedMedia.isNotEmpty() -> "header_${groupedMedia.keys.first()}"
            mediaList.isNotEmpty() -> {
                val m = mediaList.first()
                "media_${m.id ?: "none"}_${m.parentPath ?: ""}_${m.name}"
            }
            else -> null
        }
    }
    var previousFirstItemKey by remember(state) { mutableStateOf(firstItemKey) }
    var wasPinnedToTop by remember(state) {
        mutableStateOf(state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0)
    }

    LaunchedEffect(state) {
        snapshotFlow {
            Triple(
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset,
                state.isScrollInProgress
            )
        }.collect { (index, offset, scrolling) ->
            if (index == 0 && offset == 0) {
                wasPinnedToTop = true
            } else if (scrolling) {
                wasPinnedToTop = false
            }
        }
    }

    LaunchedEffect(state, firstItemKey) {
        if (previousFirstItemKey != null && firstItemKey != null && previousFirstItemKey != firstItemKey) {
            if (wasPinnedToTop && !state.isScrollInProgress &&
                (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)
            ) {
                state.scrollToItem(0, 0)
            }
        }
        previousFirstItemKey = firstItemKey
    }

    val tvCapabilities = rememberTvCapabilities()
    val isTv = tvCapabilities.isTvMode
    val effectiveIsSelectMode = tvCapabilities.isSelectModeActive(isSelectMode)

    var hasAutoFocused by remember(state) { mutableStateOf(false) }
    val hasGridContent = subfolders.isNotEmpty() || mediaList.isNotEmpty()
    LaunchedEffect(hasGridContent, isTv) {
        if (isTv && hasGridContent && !hasAutoFocused && state.firstVisibleItemIndex == 0) {
            hasAutoFocused = true
            try {
                delay(100)
                firstItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val gridPadding = if (isTv) 20.dp else 16.dp
    val effectiveSpacing = if (isTv) spacingDp.coerceAtLeast(10.dp) else spacingDp

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(itemsPerRow),
            contentPadding = PaddingValues(gridPadding),
            verticalArrangement = Arrangement.spacedBy(effectiveSpacing),
            horizontalArrangement = Arrangement.spacedBy(effectiveSpacing),
            modifier = Modifier
                .fillMaxSize()
                .focusGroup()
        ) {
            // Render Subfolders using unified CollectionCard
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
                val shouldShowLockAndBlur = tvCapabilities.allowItemLocking && isFolderProtected && !isCurrentFolderAlreadyProtected

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

                CollectionCard(
                    name = folder.name,
                    count = if (showCount && folder.mediaCount != null) "${folder.mediaCount}" else "",
                    coverUrl = coverUrl,
                    cacheKey = "folder_${folder.path}",
                    cookies = cookies,
                    placeholderIcon = Icons.Outlined.Folder,
                    cornerRadius = cornerRadiusDp,
                    aspectRatio = aspectRatio,
                    onClick = handleFolderCardClick,
                    modifier = focusModifier,
                    isInteractionEnabled = !effectiveIsSelectMode,
                    isDimmed = effectiveIsSelectMode,
                    isBlurredLocked = shouldShowLockAndBlur
                )
            }

            // Render Media grid using unified MediaGridCard
            val hasSubfolders = subfolders.isNotEmpty()
            if (isDateSort) {
                groupedMedia.forEach { (monthYear, mediaItems) ->
                    item(key = "header_$monthYear", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = monthYear,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .padding(start = 4.dp, top = 20.dp, bottom = 6.dp, end = 4.dp)
                                .fillMaxWidth()
                        )
                    }
                    items(count = mediaItems.size, key = { index -> "media_${mediaItems[index].id ?: "none"}_${mediaItems[index].parentPath ?: ""}_${mediaItems[index].name}_$index" }) { index ->
                        val media = mediaItems[index]
                        val isFirstOverallMedia = !hasSubfolders && monthYear == groupedMedia.keys.firstOrNull() && index == 0
                        MediaGridCard(
                            media = media,
                            aspectRatio = aspectRatio,
                            cornerRadiusDp = cornerRadiusDp,
                            isSelectMode = effectiveIsSelectMode,
                            isSelected = tvCapabilities.allowMultiSelect && selectedMediaForShare.contains(media),
                            cookies = cookies,
                            viewModel = viewModel,
                            onMediaClick = onMediaClick,
                            onToggleSelect = { if (tvCapabilities.allowMultiSelect) viewModel.toggleSelectMedia(media) },
                            isFirstOverallMedia = isFirstOverallMedia,
                            firstItemFocusRequester = firstItemFocusRequester
                        )
                    }
                }
            } else {
                items(count = mediaList.size, key = { index -> "media_${mediaList[index].id ?: "none"}_${mediaList[index].parentPath ?: ""}_${mediaList[index].name}_$index" }) { index ->
                    val media = mediaList[index]
                    val isFirstOverallMedia = !hasSubfolders && index == 0
                    MediaGridCard(
                        media = media,
                        aspectRatio = aspectRatio,
                        cornerRadiusDp = cornerRadiusDp,
                        isSelectMode = effectiveIsSelectMode,
                        isSelected = tvCapabilities.allowMultiSelect && selectedMediaForShare.contains(media),
                        cookies = cookies,
                        viewModel = viewModel,
                        onMediaClick = onMediaClick,
                        onToggleSelect = { if (tvCapabilities.allowMultiSelect) viewModel.toggleSelectMedia(media) },
                        isFirstOverallMedia = isFirstOverallMedia,
                        firstItemFocusRequester = firstItemFocusRequester
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
