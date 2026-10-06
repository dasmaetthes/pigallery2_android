package com.example.ui

import com.example.data.PreferencesManager
import androidx.compose.ui.unit.dp
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Label







import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.imageLoader
import coil.request.ImageRequest
import coil.compose.AsyncImage
import com.example.data.ApiMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.*
import androidx.compose.foundation.focusable







import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.LazyPagingItems

val CameraLensSide: ImageVector
    get() = ImageVector.Builder(
        name = "CameraLensSide",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)) {
            // Mount
            moveTo(4f, 7f)
            lineTo(6f, 7f)
            lineTo(6f, 17f)
            lineTo(4f, 17f)
            close()
            
            // First ring
            moveTo(7f, 5f)
            lineTo(13f, 5f)
            lineTo(13f, 19f)
            lineTo(7f, 19f)
            close()
            
            // Second ring (curved front)
            moveTo(14f, 4f)
            lineTo(16f, 4f)
            curveTo(19f, 4f, 20f, 8f, 20f, 12f)
            curveTo(20f, 16f, 19f, 20f, 16f, 20f)
            lineTo(14f, 20f)
            close()
        }
    }.build()

val ApertureIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ApertureIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        ) {
            // Outer circle
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()

            // 8 Aperture Blades dividing lines
            moveTo(12f, 2f)
            curveTo(14.5f, 4.5f, 15.5f, 8f, 13.5f, 12f)

            moveTo(19.07f, 4.93f)
            curveTo(18.5f, 8f, 15f, 12.5f, 11f, 13.5f)

            moveTo(22f, 12f)
            curveTo(18f, 14.5f, 13.5f, 15f, 9.5f, 13.5f)

            moveTo(19.07f, 19.07f)
            curveTo(15.5f, 19.07f, 11f, 15.5f, 10f, 11.5f)

            moveTo(12f, 22f)
            curveTo(9.5f, 19.5f, 8.5f, 16f, 10.5f, 12f)

            moveTo(4.93f, 19.07f)
            curveTo(5.5f, 16f, 9f, 11.5f, 13f, 10.5f)

            moveTo(2f, 12f)
            curveTo(6f, 9.5f, 10.5f, 9f, 14.5f, 10.5f)

            moveTo(4.93f, 4.93f)
            curveTo(8.5f, 4.93f, 13f, 8.5f, 14f, 12.5f)
        }
    }.build()

interface VideoPlayerController {
    val duration: Long
    val currentPosition: Long
    val isPlaying: Boolean
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaViewerDialog(
    media: ApiMedia,
    viewModel: GalleryViewModel,
    onDismiss: () -> Unit,
    onOpenMap: ((ApiMedia) -> Unit)? = null,
    isMapOpen: Boolean = false
) {
    val context = LocalContext.current
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val configuration = LocalConfiguration.current
    val cookies = viewModel.getCookiesHeader()
    val activeMediaList by viewModel.activeMediaList.collectAsState()

    val displayMetrics = LocalContext.current.resources.displayMetrics
    val maxScreenDim = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
    val safeMaxDimension = minOf(maxScreenDim * 2, 4096).coerceAtLeast(1920)

    val coroutineScope = rememberCoroutineScope()
    val scope = coroutineScope
    val focusRequester = remember { FocusRequester() }
    val topBarFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
        } catch (e: Exception) {}
    }

    // Decouple list for robust swiping
    val mediaList = if (activeMediaList.isNotEmpty()) activeMediaList else listOf(media)
    val initialIndex = remember(mediaList, media) {
        val idx = mediaList.indexOf(media)
        if (idx == -1) 0 else idx
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) {
        mediaList.size
    }

    val currentMedia = mediaList.getOrNull(pagerState.currentPage) ?: media
    val mediaUrl = viewModel.getOriginalMediaUrl(currentMedia)

    // Smart Directional Image Preloading (Preload Size First, then Full Original Resolution)
    var previousPage by remember { mutableStateOf(initialIndex) }
    var swipeDirection by remember { mutableStateOf(1) } // 1: forward, -1: backward

    LaunchedEffect(pagerState.currentPage, mediaList) {
        val page = pagerState.currentPage
        if (page > previousPage) {
            swipeDirection = 1
        } else if (page < previousPage) {
            swipeDirection = -1
        }
        previousPage = page

        if (currentMedia.isVideo) {
            return@LaunchedEffect
        }

        val offsets = if (swipeDirection >= 0) {
            listOf(1, 2, 3, -1, 4, -2)
        } else {
            listOf(-1, -2, -3, 1, -4, 2)
        }

        val targetMediaItems = offsets.mapNotNull { offset ->
            val index = page + offset
            mediaList.getOrNull(index)
        }.filter { !it.isVideo }

        val imageLoader = context.imageLoader

        // PASS 1: Preload preview display size FIRST for instant rendering
        for (targetMedia in targetMediaItems) {
            val preloadUrl = viewModel.getPreloadMediaUrl(targetMedia) ?: viewModel.getThumbnailUrl(targetMedia)
            if (!preloadUrl.isNullOrBlank()) {
                val req = ImageRequest.Builder(context)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                    .data(preloadUrl)
                    .apply {
                        if (cookies.isNotEmpty()) addHeader("Cookie", cookies)
                    }
                    .build()
                imageLoader.enqueue(req)
            }
        }

        // PASS 2: Preload full resolution original images (downsampled safely for high-res images)
        for (targetMedia in targetMediaItems) {
            val originalUrl = viewModel.getOriginalMediaUrl(targetMedia)
            if (originalUrl.isNotBlank()) {
                val req = ImageRequest.Builder(context)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                    .data(originalUrl)
                    .size(safeMaxDimension, safeMaxDimension)
                    .precision(coil.size.Precision.INEXACT)
                    .apply {
                        if (cookies.isNotEmpty()) addHeader("Cookie", cookies)
                    }
                    .build()
                imageLoader.enqueue(req)
            }
        }
    }

    var showMetadata by remember { mutableStateOf(false) }
    var showBars by remember { mutableStateOf(true) }
    var showFaceRegions by remember { mutableStateOf(false) }
    var isVideoPlaying by remember(currentMedia) { mutableStateOf(true) }
    var videoCurrentPosition by remember(currentMedia) { mutableStateOf(0) }
    var isDraggingVideoSlider by remember(currentMedia) { mutableStateOf(false) }
    var videoSliderValue by remember(currentMedia) { mutableStateOf(0f) }

    val preparedVideoPlayers = remember { mutableStateMapOf<Int, VideoPlayerController>() }
    val videoDurations = remember { mutableStateMapOf<Int, Int>() }

    val activeVideoPlayer = preparedVideoPlayers[pagerState.currentPage]
    val videoDuration = videoDurations[pagerState.currentPage] ?: activeVideoPlayer?.duration?.toInt() ?: 0

    LaunchedEffect(isVideoPlaying, activeVideoPlayer, pagerState.currentPage, pagerState.isScrollInProgress) {
        val player = activeVideoPlayer
        if (player != null) {
            if (isVideoPlaying && !pagerState.isScrollInProgress) {
                if (player.duration > 0 && player.currentPosition >= player.duration - 500) {
                    player.seekTo(0)
                }
                if (!player.isPlaying) {
                    player.play()
                }
            } else {
                if (player.isPlaying) {
                    player.pause()
                }
            }
            while (isVideoPlaying && !pagerState.isScrollInProgress) {
                if (!isDraggingVideoSlider) {
                    videoCurrentPosition = player.currentPosition.toInt()
                    val dur = player.duration.toInt()
                    if (dur > 0) {
                        videoDurations[pagerState.currentPage] = dur
                    }
                }
                kotlinx.coroutines.delay(250L)
            }
        }
    }
    var showMoreMenu by remember { mutableStateOf(false) }
    
    val slideshowDuration = viewModel.slideshowDuration.collectAsState().value
    var isSlideshowPlaying by remember { mutableStateOf(false) }
    
    var interactionCount by remember { mutableStateOf(0) }
    var topBarFocused by remember { mutableStateOf(false) }
    var bottomBarFocused by remember { mutableStateOf(false) }
    val isControlsFocused = topBarFocused || bottomBarFocused || showMoreMenu

    // Auto-hide bars after 4 seconds of inactivity
    LaunchedEffect(showBars, isSlideshowPlaying, interactionCount, isControlsFocused) {
        if (showBars && !isSlideshowPlaying && !isControlsFocused) {
            kotlinx.coroutines.delay(4000)
            showBars = false
        }
    }



    var videoCompletionTrigger by remember { mutableStateOf(0L) }

    LaunchedEffect(isSlideshowPlaying) {
        if (!isSlideshowPlaying) return@LaunchedEffect
        if (mediaList.size <= 1) return@LaunchedEffect
        
        while (isActive) {
            val pageAtStart = pagerState.currentPage
            val currentMedia = mediaList.getOrNull(pageAtStart)
            
            if (currentMedia != null && currentMedia.isVideo) {
                videoCompletionTrigger = 0L
                try {
                    kotlinx.coroutines.coroutineScope {
                        launch {
                            androidx.compose.runtime.snapshotFlow { videoCompletionTrigger }
                                .filter { it > 0L }
                                .first()
                            this@coroutineScope.cancel()
                        }
                        launch {
                            androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
                                .filter { it != pageAtStart }
                                .first()
                            this@coroutineScope.cancel()
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    if (!isActive) throw e
                    // Scope was cancelled when video completed or page changed
                }
            } else {
                try {
                    kotlinx.coroutines.coroutineScope {
                        launch {
                            kotlinx.coroutines.delay(slideshowDuration * 1000L)
                            this@coroutineScope.cancel()
                        }
                        launch {
                            androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
                                .filter { it != pageAtStart }
                                .first()
                            this@coroutineScope.cancel()
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    if (!isActive) throw e
                    // Scope was cancelled when timer expired or page changed
                }
            }
            
            if (!isActive) break
            
            // If we are still on the same page, we animate to the next page!
            if (pagerState.currentPage == pageAtStart) {
                val nextPage = (pageAtStart + 1) % mediaList.size
                try {
                    pagerState.animateScrollToPage(nextPage)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Ignore animation cancellation
                }
            }
        }
    }

    var isFirstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(pagerState.currentPage) {
        if (isFirstLoad) {
            isFirstLoad = false
        } else {
            showBars = false
        }
    }

    // Map to track custom client-side image rotations per page/index
    val rotationMap = remember { mutableStateMapOf<Int, Float>() }

    androidx.activity.compose.BackHandler(onBack = onDismiss)

    val view = LocalView.current
    val window = remember(view) {
        var contextActivity = context
        while (contextActivity is android.content.ContextWrapper) {
            if (contextActivity is android.app.Activity) {
                break
            }
            contextActivity = contextActivity.baseContext
        }
        (contextActivity as? android.app.Activity)?.window
    }

    // Keep screen on when slideshow is playing
    DisposableEffect(isSlideshowPlaying, window) {
        if (isSlideshowPlaying) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(window) {
        window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
        }
        onDispose {
            window?.let { win ->
                WindowInsetsControllerCompat(win, win.decorView).let { controller ->
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    DisposableEffect(showBars, window, isMapOpen) {
        window?.let { win ->
            val controller = WindowInsetsControllerCompat(win, win.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isMapOpen) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                if (showBars) {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        onDispose { }
    }

    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    androidx.activity.compose.BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(showMetadata) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        totalDragY = 0f
                    },
                    onDragEnd = {
                        if (kotlin.math.abs(totalDragY) > 80f) {
                            if (totalDragY < -80f) {
                                showMetadata = true
                            } else if (totalDragY > 80f) {
                                if (showMetadata) {
                                    showMetadata = false
                                } else {
                                    onDismiss()
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        totalDragY = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        totalDragY += dragAmount
                    }
                )
            }
    ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(if (showMetadata && isLandscape) 0.6f else 1f)
                        .fillMaxHeight()
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                interactionCount++
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> {
                                        if (pagerState.currentPage > 0) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    Key.DirectionRight -> {
                                        if (pagerState.currentPage < mediaList.size - 1) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    Key.DirectionUp -> {
                                        if (showBars) {
                                            try {
                                                topBarFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                // Ignore if not attached
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                        showBars = !showBars
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .focusable()
                ) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val pageMedia = mediaList.getOrNull(page) ?: return@HorizontalPager
                        val pageRotation = rotationMap[page] ?: 0f
                        MediaViewerItem(
                            media = pageMedia,
                            viewModel = viewModel,
                            cookies = cookies,
                            context = context,
                            rotation = pageRotation,
                            showFaceRegions = showFaceRegions,
                            isCurrentPage = (page == pagerState.currentPage),
                            isVideoPlaying = if (page == pagerState.currentPage && !pagerState.isScrollInProgress) isVideoPlaying else false,
                            onVideoPlayingChange = { if (page == pagerState.currentPage) isVideoPlaying = it },
                            onVideoCompletion = { videoCompletionTrigger = System.currentTimeMillis() },
                            onToggleBars = {
                                showBars = !showBars
                            },
                            showBars = showBars,
                            onVideoPrepared = { duration, controller ->
                                preparedVideoPlayers[page] = controller
                                if (duration > 0) {
                                    videoDurations[page] = duration
                                }
                            },
                            onDisposeVideo = {
                                preparedVideoPlayers.remove(page)
                                videoDurations.remove(page)
                            }
                        )
                    }
                }

                if (showMetadata && isLandscape) {
                    Card(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.4f)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(
                                top = if (showBars) 70.dp else 16.dp,
                                bottom = 16.dp,
                                start = 8.dp,
                                end = 16.dp
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.85f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        MetadataContent(
                            media = currentMedia,
                            onClose = { showMetadata = false }
                        )
                    }
                }
            }

            // Slide-up Metadata Overlay Sheet (Portrait only)
            if (!isLandscape) {
                AnimatedVisibility(
                    visible = showMetadata,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.85f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        MetadataContent(
                            media = currentMedia,
                            onClose = { showMetadata = false }
                        )
                    }
                }
            }

            // Top Bar Overlay
            AnimatedVisibility(
                visible = showBars,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .focusGroup()
                        .onFocusChanged { topBarFocused = it.hasFocus },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .focusRequester(topBarFocusRequester)
                            .focusProperties { down = focusRequester; right = FocusRequester.Default }
                            .tvFocus(shape = androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                showMetadata = !showMetadata
                            },
                            modifier = Modifier
                                .focusProperties { down = focusRequester; left = FocusRequester.Default }
                                .tvFocus(shape = androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Metadata",
                                tint = Color.White
                            )
                        }

                        if (currentMedia.metadata?.gps != null && onOpenMap != null && !isTv) {
                            IconButton(
                                onClick = {
                                    onOpenMap(currentMedia)
                                },
                                modifier = Modifier
                                    .focusProperties { down = focusRequester }
                                    .tvFocus(shape = androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = "Show on Map",
                                    tint = Color.White
                                )
                            }
                        }

                        if (!isTv) {
                            IconButton(
                                onClick = {
                                    viewModel.shareSingleMedia(context, currentMedia)
                                },
                                modifier = Modifier
                                    .focusProperties { down = focusRequester }
                                    .tvFocus(shape = androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    downloadFile(context, mediaUrl, currentMedia.name, cookies)
                                },
                                modifier = Modifier
                                    .focusProperties { down = focusRequester }
                                    .tvFocus(shape = androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download",
                                    tint = Color.White
                                )
                            }
                        }
                        
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier
                                .focusProperties { down = focusRequester }
                                .tvFocus(shape = androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White)
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                modifier = Modifier.tvFocus(),
                                text = { androidx.compose.material3.Text(if (isSlideshowPlaying) "Stop Diashow" else "Start Diashow") },
                                onClick = {
                                    isSlideshowPlaying = !isSlideshowPlaying
                                    if (isSlideshowPlaying) showBars = false
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isSlideshowPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                }
                            )
                            if (!currentMedia.isVideo) {
                                androidx.compose.material3.DropdownMenuItem(
                                    modifier = Modifier.tvFocus(),
                                    text = { androidx.compose.material3.Text("Rotate") },
                                    onClick = {
                                        val page = pagerState.currentPage
                                        rotationMap[page] = ((rotationMap[page] ?: 0f) + 90f) % 360f
                                        showMoreMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                            contentDescription = null
                                        )
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    modifier = Modifier.tvFocus(),
                                    text = { androidx.compose.material3.Text(if (showFaceRegions) "Hide Face Regions" else "Show Face Regions") },
                                    onClick = {
                                        showFaceRegions = !showFaceRegions
                                        showMoreMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Bar Overlay
            AnimatedVisibility(
                visible = showBars,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                        .onFocusChanged { bottomBarFocused = it.hasFocus }
                ) {
                    if (currentMedia.isVideo) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = { isVideoPlaying = !isVideoPlaying }
                            ) {
                                Icon(
                                    imageVector = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isVideoPlaying) "Pause" else "Play",
                                    tint = Color.White
                                )
                            }
                            
                            val displayPos = if (isDraggingVideoSlider) videoSliderValue.toInt() else videoCurrentPosition
                            Text(
                                text = formatTime(displayPos),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            if (!isDraggingVideoSlider) {
                                videoSliderValue = videoCurrentPosition.toFloat()
                            }
                            Slider(
                                value = videoSliderValue,
                                onValueChange = { newValue ->
                                    isDraggingVideoSlider = true
                                    videoSliderValue = newValue
                                },
                                onValueChangeFinished = {
                                    isDraggingVideoSlider = false
                                    activeVideoPlayer?.seekTo(videoSliderValue.toLong())
                                    videoCurrentPosition = videoSliderValue.toInt()
                                },
                                valueRange = 0f..videoDuration.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = formatTime(videoDuration),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentMedia.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 16.dp)
                        )
                        Text(
                            text = "${pagerState.currentPage + 1} / ${mediaList.size}",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Share/Download Progress Indicator Overlay
            val shareProgress by viewModel.shareProgress.collectAsState()
            if (shareProgress >= 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = if (showBars) 64.dp else 0.dp)
                ) {
                    if (shareProgress == 0f) {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    } else {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { shareProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
            
        }
    }

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "Unknown"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.2f MB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

@Composable
fun MetadataContent(media: ApiMedia, onClose: () -> Unit) {
    var currentFileSize by remember(media.id) { mutableStateOf(media.metadata?.fileSize) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { PreferencesManager(context) }

    LaunchedEffect(media.id, currentFileSize) {
        if (currentFileSize == null || currentFileSize == 0L) {
            val server = prefs.serverUrl
            val base = if (server.isNotBlank() && !server.startsWith("http")) "http://$server" else server
            val sanitizedBase = base.trimEnd('/')
            val apiPrefix = prefs.apiPrefix
            val encodedName = java.net.URLEncoder.encode(media.name, "UTF-8").replace("+", "%20")
            val relativePath = if (!media.parentPath.isNullOrEmpty()) {
                val pathClean = media.parentPath.trim('/').split("/").joinToString("/") {
                    java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                }
                "$pathClean/$encodedName"
            } else {
                encodedName
            }
            val mediaUrl = "$sanitizedBase$apiPrefix/gallery/content/$relativePath"
            if (sanitizedBase.isNotBlank()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val client = okhttp3.OkHttpClient()
                        val req = okhttp3.Request.Builder()
                            .url(mediaUrl)
                            .apply {
                                val userCookies = prefs.cookies
                                if (userCookies.isNotEmpty()) addHeader("Cookie", userCookies)
                            }
                            .head()
                            .build()
                        client.newCall(req).execute().use { resp ->
                            val len = resp.header("Content-Length")?.toLongOrNull()
                            if (len != null && len > 0) {
                                currentFileSize = len
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Metadata Info",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(24.dp)
                    .tvFocus(shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

        MetadataRow(icon = Icons.AutoMirrored.Outlined.InsertDriveFile, label = "Filename", value = media.name)
        media.parentPath?.let {
            val cleanedPath = it.replace(Regex("/{2,}"), "/")
            MetadataRow(icon = Icons.Outlined.Folder, label = "Folder", value = cleanedPath)
        }

        currentFileSize?.let { bytes ->
            if (bytes > 0) {
                MetadataRow(icon = Icons.Outlined.SdCard, label = "File Size", value = formatFileSize(bytes))
            }
        }

        val size = media.metadata?.size
        if (size != null && size.width != null && size.height != null) {
            MetadataRow(icon = Icons.Outlined.AspectRatio, label = "Dimensions", value = "${size.width} × ${size.height} px")
        }

        val dateStr = DateUtils.formatMediaDate(media.metadata?.creationDate, media.metadata?.creationDateOffset)
        MetadataRow(icon = Icons.Outlined.DateRange, label = "Date Taken", value = dateStr)

        MetadataRow(icon = if (media.isVideo) Icons.Outlined.Videocam else Icons.Outlined.Image, label = "Type", value = if (media.isVideo) "Video (MP4)" else "Image")
        
        media.metadata?.cameraData?.let { camera ->
            val cameraName = listOfNotNull(camera.make, camera.model).joinToString(" ")
            if (cameraName.isNotBlank()) {
                MetadataRow(icon = Icons.Outlined.CameraAlt, label = "Camera", value = cameraName)
            }
            camera.lens?.let { lens ->
                if (lens.isNotBlank()) {
                    MetadataRow(icon = CameraLensSide, label = "Lens", value = lens)
                }
            }
            camera.focalLength?.let { focal ->
                val focalLengthStr = if (focal % 1.0 == 0.0) "${focal.toInt()} mm" else "${focal} mm"
                MetadataRow(icon = Icons.Outlined.Straighten, label = "Focal Length", value = focalLengthStr)
            }
            camera.ISO?.let { MetadataRow(icon = Icons.Outlined.Iso, label = "ISO", value = it.toString()) }
            camera.fStop?.let { MetadataRow(icon = ApertureIcon, label = "Aperture", value = "f/${it}") }
            camera.exposure?.let { 
                val exposureStr = if (it < 1.0 && it > 0.0) "1/${(1.0 / it).toInt()}s" else "${it}s"
                MetadataRow(icon = Icons.Outlined.Timer, label = "Exposure Time", value = exposureStr) 
            }
        }
        
        media.metadata?.gps?.let { gps ->
            val gpsStr = String.format(java.util.Locale.US, "%.6f, %.6f", gps.latitude, gps.longitude)
            MetadataRow(icon = Icons.Outlined.LocationOn, label = "GPS Coordinates", value = gpsStr)
        }
        
        val keywords = media.metadata?.keywords?.distinct()
        if (!keywords.isNullOrEmpty()) {
            MetadataBlock(icon = Icons.AutoMirrored.Outlined.Label, label = "Keywords", value = keywords.joinToString(", "))
        }
        
        val faces = media.metadata?.faces
        if (!faces.isNullOrEmpty()) {
            val faceNames = faces.mapNotNull { it.name }.filter { it.isNotBlank() }
            if (faceNames.isNotEmpty()) {
                MetadataBlock(icon = Icons.Outlined.Person, label = "People", value = faceNames.joinToString(", "))
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MediaViewerItem(
    media: ApiMedia,
    viewModel: GalleryViewModel,
    cookies: String,
    context: Context,
    rotation: Float,
    showFaceRegions: Boolean,
    isCurrentPage: Boolean,
    isVideoPlaying: Boolean,
    onVideoPlayingChange: (Boolean) -> Unit,
    onVideoCompletion: () -> Unit,
    onToggleBars: () -> Unit,
    showBars: Boolean,
    onVideoPrepared: (duration: Int, controller: VideoPlayerController) -> Unit,
    onDisposeVideo: (() -> Unit)? = null
) {
    val isTv = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    val mediaUrl = viewModel.getOriginalMediaUrl(media)

    // Pinch to Zoom states (for images)
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (media.isVideo) {
            var isPreparing by remember(media.id) { mutableStateOf(true) }
            var isBuffering by remember(media.id) { mutableStateOf(false) }
            var hasError by remember(media.id) { mutableStateOf(false) }
            var isPlayingState by remember(media.id) { mutableStateOf(false) }

            val initialAspectRatio = remember(media) {
                val w = media.metadata?.size?.width?.toFloat()
                val h = media.metadata?.size?.height?.toFloat()
                if (w != null && h != null && w > 0f && h > 0f) w / h else null
            }
            var videoAspectRatio by remember(media) { mutableStateOf(initialAspectRatio) }

            val exoPlayer = remember(media.id) {
                // Progressive buffering strategy:
                // bufferForPlaybackMs = 1,000ms: Starts playback as soon as 1s is buffered — does not wait for all to be buffered!
                // bufferForPlaybackAfterRebufferMs = 2,500ms: Quick and stable resumption after rebuffering
                // minBufferMs = 20,000ms: Keeps 20s buffered ahead in the background for smooth continuous playback without stopping
                // maxBufferMs = 60,000ms: Upper buffer limit (60s)
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 20_000,
                        /* maxBufferMs = */ 60_000,
                        /* bufferForPlaybackMs = */ 1_000,
                        /* bufferForPlaybackAfterRebufferMs = */ 2_500
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .setBackBuffer(15_000, true)
                    .build()

                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(30000)
                    .setAllowCrossProtocolRedirects(true)
                    .apply {
                        if (cookies.isNotEmpty()) {
                            setDefaultRequestProperties(mapOf("Cookie" to cookies))
                        }
                    }

                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .setContinueLoadingCheckIntervalBytes(256 * 1024)
                    .createMediaSource(MediaItem.fromUri(mediaUrl))

                ExoPlayer.Builder(context)
                    .setLoadControl(loadControl)
                    .setAudioAttributes(
                        androidx.media3.common.AudioAttributes.Builder()
                            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                            .build(),
                        /* handleAudioFocus = */ true
                    )
                    .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
                    .build().apply {
                        setMediaSource(mediaSource)
                    }
            }

            val controller = remember(exoPlayer) {
                object : VideoPlayerController {
                    override val duration: Long get() = exoPlayer.duration.coerceAtLeast(0L)
                    override val currentPosition: Long get() = exoPlayer.currentPosition.coerceAtLeast(0L)
                    override val isPlaying: Boolean get() = exoPlayer.isPlaying
                    override fun play() { exoPlayer.play() }
                    override fun pause() { exoPlayer.pause() }
                    override fun seekTo(positionMs: Long) { exoPlayer.seekTo(positionMs) }
                }
            }

            DisposableEffect(exoPlayer) {
                // Immediately wire up player controller to dialog state
                onVideoPrepared(if (exoPlayer.duration > 0) exoPlayer.duration.toInt() else 0, controller)

                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                isBuffering = true
                            }
                            Player.STATE_READY -> {
                                isPreparing = false
                                isBuffering = false
                                val dur = exoPlayer.duration
                                onVideoPrepared(if (dur > 0) dur.toInt() else 0, controller)
                            }
                            Player.STATE_ENDED -> {
                                isPreparing = false
                                isBuffering = false
                                onVideoPlayingChange(false)
                                onVideoCompletion()
                                viewModel.emitVideoFinished()
                            }
                            Player.STATE_IDLE -> {}
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        isPlayingState = isPlaying
                        if (isPlaying) {
                            isPreparing = false
                            isBuffering = false
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        hasError = true
                        isPreparing = false
                        isBuffering = false
                        onVideoPlayingChange(false)
                    }
                }
                exoPlayer.addListener(listener)
                onDispose {
                    exoPlayer.removeListener(listener)
                }
            }

            LaunchedEffect(isCurrentPage, isVideoPlaying) {
                if (isCurrentPage) {
                    if (exoPlayer.playbackState == Player.STATE_IDLE) {
                        exoPlayer.prepare()
                    }
                    exoPlayer.playWhenReady = isVideoPlaying
                } else {
                    exoPlayer.playWhenReady = false
                    exoPlayer.pause()
                }
            }

            DisposableEffect(media.id) {
                onDispose {
                    exoPlayer.stop()
                    exoPlayer.release()
                    onDisposeVideo?.invoke()
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = false
                            player = exoPlayer
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { playerView ->
                        val pv = playerView as? PlayerView
                        if (pv?.player != exoPlayer) {
                            pv?.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Transparent tap overlay covering full screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onToggleBars() })
                        }
                )
            }

            if (hasError) {
                Text(
                    text = "Playback failed. Stream might be unsupported.",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            if ((isPreparing || isBuffering) && !hasError && !isPlayingState) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Buffering video...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            val originalUrl = viewModel.getOriginalMediaUrl(media)
            val preloadUrl = viewModel.getPreloadMediaUrl(media)
            val thumbnailUrl = viewModel.getThumbnailUrl(media)

            var currentUrl by remember(media) {
                mutableStateOf(preloadUrl ?: originalUrl)
            }

            var preloadDrawable by remember(media) {
                mutableStateOf<android.graphics.drawable.Drawable?>(null)
            }

            val displayMetrics = LocalContext.current.resources.displayMetrics
            val maxScreenDim = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
            val safeMaxDimension = minOf(maxScreenDim * 2, 4096).coerceAtLeast(1920)

            // Image View with Pinch to Zoom & dynamic Client-Side Rotation
            val builder = ImageRequest.Builder(context)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                .data(currentUrl)
                .crossfade(true)

            if (currentUrl == originalUrl) {
                builder.size(safeMaxDimension, safeMaxDimension)
                builder.precision(coil.size.Precision.INEXACT)
                if (preloadDrawable != null) {
                    builder.placeholder(preloadDrawable)
                } else {
                    builder.placeholderMemoryCacheKey(preloadUrl ?: thumbnailUrl)
                }
            } else {
                builder.placeholderMemoryCacheKey(thumbnailUrl)
            }

            if (cookies.isNotEmpty()) {
                builder.addHeader("Cookie", cookies)
            }
            val imageRequest = builder.build()
            
            var intrinsicSize by remember { mutableStateOf(Size.Zero) }

            AsyncImage(
                model = imageRequest,
                contentDescription = media.name,
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    intrinsicSize = state.painter.intrinsicSize
                    preloadDrawable = state.result.drawable
                    if (currentUrl == preloadUrl && !isTv) {
                        currentUrl = originalUrl
                    }
                },
                onError = {
                    if (currentUrl == preloadUrl && !isTv) {
                        currentUrl = originalUrl
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
                            },
                            onTap = { onToggleBars() }
                        )
                    }
                    .pointerInput(intrinsicSize) {
                        try {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.isEmpty()) continue
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val pointersCount = event.changes.size

                                    if (pointersCount > 1 || scale > 1f) {
                                        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
                                        scale = nextScale
                                        
                                        var consumedPan = false
                                        
                                        if (nextScale > 1f && intrinsicSize.width > 0f && intrinsicSize.height > 0f) {
                                            val scaleX = size.width / intrinsicSize.width
                                            val scaleY = size.height / intrinsicSize.height
                                            val fitScale = minOf(scaleX, scaleY)
                                            val displayWidth = intrinsicSize.width * fitScale
                                            val displayHeight = intrinsicSize.height * fitScale
                                            
                                            val maxOffsetX = maxOf(0f, (displayWidth * nextScale - size.width) / 2f)
                                            val maxOffsetY = maxOf(0f, (displayHeight * nextScale - size.height) / 2f)
                                            
                                            val oldOffsetX = offsetX
                                            offsetX = (offsetX + panChange.x * nextScale).coerceIn(-maxOffsetX, maxOffsetX)
                                            offsetY = (offsetY + panChange.y * nextScale).coerceIn(-maxOffsetY, maxOffsetY)
                                            
                                            val movedX = kotlin.math.abs(offsetX - oldOffsetX) > 0.01f
                                            val isZooming = pointersCount > 1 || zoomChange != 1f
                                            if (movedX || isZooming) {
                                                consumedPan = true
                                            }
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                        
                                        if (consumedPan) {
                                            event.changes.forEach {
                                                if (it.positionChanged()) {
                                                    it.consume()
                                                }
                                            }
                                        }
                                    } else {
                                        if (scale <= 1f) {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Safely handle gesture scope cancellation during page swiping
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                        rotationZ = rotation
                    )
                    .drawWithContent {
                        drawContent()
                        val metaW = media.metadata?.size?.width?.toFloat() ?: intrinsicSize.width
                        val metaH = media.metadata?.size?.height?.toFloat() ?: intrinsicSize.height
                        
                        if (showFaceRegions && intrinsicSize.width > 0f && intrinsicSize.height > 0f && metaW > 0f && metaH > 0f) {
                            val scaleX = size.width / intrinsicSize.width
                            val scaleY = size.height / intrinsicSize.height
                            val fitScale = minOf(scaleX, scaleY)
                            
                            val drawWidth = intrinsicSize.width * fitScale
                            val drawHeight = intrinsicSize.height * fitScale
                            
                            val leftOffset = (size.width - drawWidth) / 2f
                            val topOffset = (size.height - drawHeight) / 2f

                            media.metadata?.faces?.forEach { face: com.example.data.ApiFace ->
                                face.box?.let { box ->
                                    val rLeft = box.left.toFloat() / metaW
                                    val rTop = box.top.toFloat() / metaH
                                    val rWidth = box.width.toFloat() / metaW
                                    val rHeight = box.height.toFloat() / metaH
                                    
                                    val boxLeft = leftOffset + rLeft * drawWidth
                                    val boxTop = topOffset + rTop * drawHeight
                                    val boxW = rWidth * drawWidth
                                    val boxH = rHeight * drawHeight
                                    
                                    // Draw face box (PiGallery2 style: white border, 2px, 5px radius)
                                    drawRoundRect(
                                        color = Color.White,
                                        topLeft = Offset(boxLeft, boxTop),
                                        size = Size(boxW, boxH),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    
                                    // Draw face name (PiGallery2 style: white text, transparent dark background)
                                    if (face.name != null) {
                                        val paint = android.graphics.Paint().apply {
                                            color = android.graphics.Color.WHITE
                                            textSize = 14.sp.toPx()
                                            isAntiAlias = true
                                            isFakeBoldText = true
                                            textAlign = android.graphics.Paint.Align.CENTER
                                        }
                                        
                                        val textWidth = paint.measureText(face.name)
                                        val textHeight = paint.descent() - paint.ascent()
                                        
                                        val textCenterX = boxLeft + boxW / 2f
                                        val textTop = boxTop + boxH + 4.dp.toPx() // Below the box
                                        val bgLeft = textCenterX - textWidth / 2f - 4.dp.toPx()
                                        val bgRight = textCenterX + textWidth / 2f + 4.dp.toPx()
                                        val bgTop = textTop
                                        val bgBottom = textTop + textHeight + 8.dp.toPx()
                                        
                                        // Background
                                        drawRoundRect(
                                            color = Color(0x80000000), // rgba(0,0,0,0.2)
                                            topLeft = Offset(bgLeft, bgTop),
                                            size = Size(bgRight - bgLeft, bgBottom - bgTop),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                                        )
                                        
                                        drawContext.canvas.nativeCanvas.drawText(
                                            face.name,
                                            textCenterX,
                                            bgBottom - 4.dp.toPx() - paint.descent(),
                                            paint
                                        )
                                    }
                                }
                            }
                        }
                    }
            )
        }
    }
}

@Composable
fun MetadataRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun MetadataBlock(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

private fun downloadFile(context: Context, url: String, fileName: String, cookies: String) {
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Downloading file from PiGallery2")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .apply {
                if (cookies.isNotEmpty()) {
                    addRequestHeader("Cookie", cookies)
                }
            }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = (ms.coerceAtLeast(0)) / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}