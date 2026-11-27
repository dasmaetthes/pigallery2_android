package com.example.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.ApiMedia
import com.example.util.rememberTvCapabilities

/**
 * Immutable Material 3 Surface configuration for grid media and collection cards.
 */
@Immutable
data class MediaCardSurfaceStyle(
    val shape: Shape,
    val containerColor: Color,
    val contentColor: Color,
    val tonalElevation: Dp,
    val shadowElevation: Dp,
    val border: BorderStroke
)

/**
 * Centralized Material 3 card styling, Surface elevation (tonal + shadow), shape, and border tokens
 * ensuring a cohesive visual hierarchy across Gallery, Albums, Persons, Rediscover, Map, Search,
 * and Settings:
 * - Featured preview cards: 4.dp elevation, surfaceContainerHigh
 * - Collection cards (Folders, Albums, Persons): 3.dp tonal & shadow elevation (6.dp active), surfaceContainerHigh
 * - Photo / Media item cards: 1.dp tonal / 2.dp shadow elevation (4.dp selected, 6.dp active), surfaceContainer
 * - Section / list cards (Settings, About, Search): 1.dp elevation, surfaceContainerLow
 */
object GalleryCardDefaults {
    val DefaultMediaCornerRadius: Dp = 12.dp

    val CollectionElevation: Dp = 3.dp
    val CollectionPressedElevation: Dp = 6.dp
    val MediaElevation: Dp = 2.dp
    val MediaTonalElevation: Dp = 1.dp
    val MediaSelectedElevation: Dp = 4.dp
    val MediaPressedElevation: Dp = 6.dp
    val PreviewElevation: Dp = 4.dp
    val SectionElevation: Dp = 1.dp

    @Composable
    fun mediaCardShape(cornerRadius: Dp? = null): Shape =
        if (cornerRadius != null && cornerRadius >= 0.dp) {
            RoundedCornerShape(cornerRadius)
        } else {
            MaterialTheme.shapes.medium
        }

    @Composable
    fun collectionCardShape(cornerRadius: Dp? = null): Shape =
        mediaCardShape(cornerRadius)

    @Composable
    fun mediaCardSurfaceStyle(
        cornerRadius: Dp? = null,
        isSelected: Boolean = false,
        isFocused: Boolean = false,
        isPressed: Boolean = false
    ): MediaCardSurfaceStyle {
        val tonalElevation = when {
            isPressed || isFocused -> MediaPressedElevation
            isSelected -> MediaSelectedElevation
            else -> MediaTonalElevation
        }
        val shadowElevation = when {
            isPressed || isFocused -> MediaPressedElevation
            isSelected -> MediaSelectedElevation
            else -> MediaElevation
        }
        return MediaCardSurfaceStyle(
            shape = mediaCardShape(cornerRadius),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            border = mediaCardBorder(isSelected = isSelected, isFocused = isFocused)
        )
    }

    @Composable
    fun collectionCardSurfaceStyle(
        cornerRadius: Dp? = null,
        isFocused: Boolean = false,
        isPressed: Boolean = false
    ): MediaCardSurfaceStyle {
        val elevation = if (isPressed || isFocused) CollectionPressedElevation else CollectionElevation
        return MediaCardSurfaceStyle(
            shape = collectionCardShape(cornerRadius),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = elevation,
            shadowElevation = elevation,
            border = collectionCardBorder()
        )
    }

    @Composable
    fun collectionCardColors(): CardColors {
        val container = MaterialTheme.colorScheme.surfaceContainerHigh
        val content = MaterialTheme.colorScheme.onSurface
        return CardDefaults.cardColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content
        )
    }

    @Composable
    fun collectionCardElevation(): CardElevation = CardDefaults.cardElevation(
        defaultElevation = CollectionElevation,
        pressedElevation = CollectionPressedElevation,
        focusedElevation = CollectionPressedElevation,
        hoveredElevation = 4.dp,
        disabledElevation = CollectionElevation
    )

    @Composable
    fun collectionCardBorder(): BorderStroke = BorderStroke(
        width = 0.75.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )

    @Composable
    fun mediaCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    @Composable
    fun mediaCardElevation(): CardElevation = CardDefaults.cardElevation(
        defaultElevation = MediaElevation,
        pressedElevation = MediaPressedElevation,
        focusedElevation = MediaPressedElevation,
        hoveredElevation = 3.dp
    )

    @Composable
    fun mediaCardBorder(
        isSelected: Boolean,
        isFocused: Boolean = false
    ): BorderStroke = when {
        isFocused -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        isSelected -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    @Composable
    fun previewCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    @Composable
    fun previewCardElevation(): CardElevation = CardDefaults.cardElevation(
        defaultElevation = PreviewElevation,
        pressedElevation = 6.dp,
        focusedElevation = 6.dp,
        hoveredElevation = 5.dp
    )

    @Composable
    fun previewCardBorder(isSelected: Boolean = false): BorderStroke = if (isSelected) {
        BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }

    @Composable
    fun sectionCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    @Composable
    fun sectionCardElevation(): CardElevation = CardDefaults.cardElevation(
        defaultElevation = SectionElevation,
        pressedElevation = 2.dp,
        focusedElevation = 2.dp
    )

    @Composable
    fun sectionCardBorder(): BorderStroke = BorderStroke(
        width = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}

/**
 * Shared collection tile used uniformly by Gallery subfolders, Albums, and Persons.
 * Uses a Material 3 Surface with animated tonal & shadow elevation and uniform shape configuration,
 * and always renders a polished placeholder backdrop underneath the AsyncImage.
 */
@Composable
fun CollectionCard(
    name: String,
    count: String,
    coverUrl: String?,
    cacheKey: String,
    cookies: String,
    placeholderIcon: ImageVector,
    cornerRadius: Dp,
    aspectRatio: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isInteractionEnabled: Boolean = true,
    isDimmed: Boolean = false,
    isBlurredLocked: Boolean = false,
    topEndOverlay: (@Composable BoxScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val tvCapabilities = rememberTvCapabilities()
    val effectiveBlurredLocked = isBlurredLocked && tvCapabilities.allowItemLocking
    val cardRatio = if (aspectRatio > 0f) aspectRatio else 1f
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val surfaceStyle = GalleryCardDefaults.collectionCardSurfaceStyle(
        cornerRadius = cornerRadius,
        isFocused = isFocused && isInteractionEnabled,
        isPressed = isPressed && isInteractionEnabled
    )
    val animatedTonalElevation by animateDpAsState(
        targetValue = surfaceStyle.tonalElevation,
        label = "collectionCardTonalElevation"
    )
    val animatedShadowElevation by animateDpAsState(
        targetValue = surfaceStyle.shadowElevation,
        label = "collectionCardShadowElevation"
    )

    Surface(
        onClick = onClick,
        enabled = isInteractionEnabled,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(cardRatio)
            .then(if (isInteractionEnabled) Modifier.tvFocus(shape = surfaceStyle.shape, scaleOnFocus = true) else Modifier)
            .then(if (isDimmed) Modifier.graphicsLayer(alpha = 0.4f) else Modifier),
        shape = surfaceStyle.shape,
        color = surfaceStyle.containerColor,
        contentColor = surfaceStyle.contentColor,
        tonalElevation = animatedTonalElevation,
        shadowElevation = animatedShadowElevation,
        border = surfaceStyle.border
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Unified Material 3 placeholder backdrop (visible when coverUrl is null, loading, or failed)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = placeholderIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            if (coverUrl != null) {
                val imageRequest = remember(coverUrl, cookies, cacheKey) {
                    ImageRequest.Builder(context)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .data(coverUrl)
                        .apply {
                            if (cookies.isNotEmpty()) {
                                setHeader("Cookie", cookies)
                            }
                        }
                        .crossfade(true)
                        .build()
                }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (effectiveBlurredLocked) Modifier.blur(25.dp) else Modifier)
                )
            }

            if (effectiveBlurredLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .testTag("collection_card_lock_overlay"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
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
                name = name,
                count = count
            )

            if (topEndOverlay != null) {
                topEndOverlay()
            }
        }
    }
}

/**
 * Shared media item tile used uniformly across Gallery, Album Detail, Person Detail, and Rediscover grids.
 * Uses a Material 3 Surface with uniform shape configuration and animated tonal & shadow elevation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridCard(
    media: ApiMedia,
    aspectRatio: Float,
    cornerRadiusDp: Dp,
    isSelectMode: Boolean,
    isSelected: Boolean,
    cookies: String,
    viewModel: GalleryViewModel,
    onMediaClick: (ApiMedia) -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
    isFirstOverallMedia: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
    fallbackSquareAspectRatio: Boolean = false
) {
    val context = LocalContext.current
    val tvCapabilities = rememberTvCapabilities()
    val effectiveSelectMode = tvCapabilities.isSelectModeActive(isSelectMode)
    val effectiveSelected = tvCapabilities.allowMultiSelect && isSelected
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val surfaceStyle = GalleryCardDefaults.mediaCardSurfaceStyle(
        cornerRadius = cornerRadiusDp,
        isSelected = effectiveSelected,
        isFocused = isFocused,
        isPressed = isPressed
    )
    val animatedTonalElevation by animateDpAsState(
        targetValue = surfaceStyle.tonalElevation,
        label = "mediaCardTonalElevation"
    )
    val animatedShadowElevation by animateDpAsState(
        targetValue = surfaceStyle.shadowElevation,
        label = "mediaCardShadowElevation"
    )

    val focusModifier = if (isFirstOverallMedia && firstItemFocusRequester != null) {
        Modifier.focusRequester(firstItemFocusRequester)
    } else {
        Modifier
    }

    val effectiveRatio = remember(aspectRatio, fallbackSquareAspectRatio, media.metadata?.size?.width, media.metadata?.size?.height) {
        when {
            aspectRatio > 0f -> aspectRatio
            fallbackSquareAspectRatio -> 1f
            else -> {
                val w = media.metadata?.size?.width
                val h = media.metadata?.size?.height
                if (w != null && h != null && w > 0 && h > 0) {
                    (w.toFloat() / h.toFloat()).coerceIn(0.5f, 2.5f)
                } else {
                    1f
                }
            }
        }
    }

    val clickModifier = if (!tvCapabilities.allowMultiSelect) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { onMediaClick(media) }
        )
    } else {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                if (effectiveSelectMode) {
                    onToggleSelect()
                } else {
                    onMediaClick(media)
                }
            },
            onLongClick = {
                onToggleSelect()
            }
        )
    }

    val surfaceModifier = modifier
        .then(focusModifier)
        .fillMaxWidth()
        .aspectRatio(effectiveRatio)
        .tvFocus(shape = surfaceStyle.shape, scaleOnFocus = true)
        .then(clickModifier)

    Surface(
        modifier = surfaceModifier,
        shape = surfaceStyle.shape,
        color = surfaceStyle.containerColor,
        contentColor = surfaceStyle.contentColor,
        tonalElevation = animatedTonalElevation,
        shadowElevation = animatedShadowElevation,
        border = surfaceStyle.border
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .indication(interactionSource = interactionSource, indication = ripple())
        ) {
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

            // Unified video play overlay & duration pill
            if (media.isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.Black.copy(alpha = 0.52f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.video_cd),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                val durationStr = formatVideoDuration(media.metadata?.duration)
                if (durationStr.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = durationStr,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Selection mode indicator (disabled on Android TV)
            if (effectiveSelectMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (effectiveSelected) Color.Black.copy(alpha = 0.24f) else Color.Transparent)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    if (effectiveSelected) {
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
                                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact active filter bar displayed in Albums / Persons overview when a filter is applied
 * while the TopAppBar search field is collapsed.
 */
@Composable
fun ActiveFilterBar(
    query: String,
    onClear: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        InputChip(
            selected = true,
            onClick = onEdit,
            label = {
                Text(
                    text = stringResource(R.string.active_filter_label, query),
                    maxLines = 1
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingIcon = {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onClear() }
                        .testTag("active_filter_clear_icon"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.reset_filter),
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            colors = InputChipDefaults.inputChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("active_filter_reset_chip")
                .tvFocus(shape = RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
}
