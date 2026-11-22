package com.example.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.ApiMedia
import kotlinx.coroutines.delay

@Composable
fun RediscoverTabContent(viewModel: GalleryViewModel) {
    val rediscoverState by viewModel.rediscoverState.collectAsState()
    val itemsPerRowPortrait by viewModel.itemsPerRowPortrait.collectAsState()
    val itemsPerRowLandscape by viewModel.itemsPerRowLandscape.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val itemsPerRow = if (isLandscape) itemsPerRowLandscape else itemsPerRowPortrait
    val spacing by viewModel.spacing.collectAsState()
    val cornerRadius by viewModel.cornerRadius.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

        when (val state = rediscoverState) {
            is RediscoverUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is RediscoverUiState.Error -> {
                ErrorStateView(message = state.message) {
                    viewModel.loadRediscover()
                }
            }
            is RediscoverUiState.Success -> {
                val grouped = state.groupedMedia
                if (grouped.isEmpty()) {
                    EmptyStateView(stringResource(R.string.nothing_to_rediscover), stringResource(R.string.nothing_to_rediscover_subtitle))
                } else {
                    val expandedYears = remember { mutableStateMapOf<Int, Boolean>() }

                    val isTv = LocalContext.current.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                    val firstItemFocusRequester = remember { FocusRequester() }

                    LaunchedEffect(grouped) {
                        if (isTv && grouped.isNotEmpty()) {
                            try {
                                delay(100)
                                firstItemFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(itemsPerRow),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(spacing.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing.dp)
                    ) {
                        grouped.forEach { (year, mediaList) ->
                            val isExpanded = expandedYears[year] ?: false
                            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                            val diff = currentYear - year
                            val isFirstYear = year == grouped.keys.first()

                            item(span = { GridItemSpan(itemsPerRow) }) {
                                var isFocused by remember { mutableStateOf(false) }
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    val hasMore = mediaList.size > itemsPerRow
                                    val focusModifier = if (isFirstYear) Modifier.focusRequester(firstItemFocusRequester) else Modifier

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(focusModifier)
                                            .onFocusChanged { isFocused = it.isFocused }
                                            .clip(RoundedCornerShape(8.dp))
                                            .tvFocus(shape = RoundedCornerShape(8.dp), showBorder = true)
                                            .then(if (hasMore) Modifier.clickable { expandedYears[year] = !isExpanded } else Modifier.focusable())
                                            .background(
                                                color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                            )
                                            .padding(horizontal = 8.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = if (diff == 1) stringResource(R.string.one_year_ago, year) else stringResource(R.string.years_ago, diff, year),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = stringResource(R.string.items_count, mediaList.size),
                                                fontSize = 12.sp,
                                                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                        if (hasMore) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                                                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            val displayList = if (isExpanded) mediaList else mediaList.take(itemsPerRow)
                            items(displayList) { media ->
                                RediscoverMediaItem(
                                    media = media,
                                    viewModel = viewModel,
                                    list = mediaList,
                                    cornerRadius = cornerRadius.dp,
                                    aspectRatio = aspectRatio
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RediscoverMediaItem(
    media: ApiMedia,
    viewModel: GalleryViewModel,
    list: List<ApiMedia> = emptyList(),
    cornerRadius: Dp,
    aspectRatio: Float
) {
    val context = LocalContext.current
    val cookies = viewModel.getCookiesHeader()
    val cardRatio = if (aspectRatio > 0f) aspectRatio else 1f

    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val selectedMediaForShare by viewModel.selectedMediaForShare.collectAsState()
    val isSelected = selectedMediaForShare.contains(media)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(cardRatio)
            .clip(RoundedCornerShape(cornerRadius))
            .tvFocus(shape = RoundedCornerShape(cornerRadius))
            .combinedClickable(
                onClick = {
                    if (isSelectMode) {
                        viewModel.toggleSelectMedia(media)
                    } else {
                        viewModel.selectMedia(media, list)
                    }
                },
                onLongClick = {
                    viewModel.toggleSelectMedia(media)
                }
            ),
        shape = RoundedCornerShape(cornerRadius),
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

            if (media.isLivePhoto && !media.isVideo) {
                LivePhotoBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )
            }

            if (media.isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = stringResource(R.string.video_cd),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                val durationStr = formatVideoDuration(media.metadata?.duration)
                if (durationStr.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = durationStr,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

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
