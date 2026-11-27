package com.example.ui

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import com.example.findActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.example.R
import com.example.data.ApiMedia
import com.example.data.PreferencesManager
import com.example.util.DeviceUtils
import com.example.util.rememberTvCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

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
    val isMuted: Boolean
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setMuted(muted: Boolean)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun MediaViewerDialog(
    media: ApiMedia,
    viewModel: GalleryViewModel,
    onDismiss: () -> Unit,
    onOpenMap: ((ApiMedia) -> Unit)? = null,
    isMapOpen: Boolean = false
) {
    val context = LocalContext.current
    val tvCapabilities = rememberTvCapabilities()
    val isTv = tvCapabilities.isTvMode
    val isLegacyOrLowRamTv = remember(context) { DeviceUtils.isLegacyOrLowRamTv(context) }
    val cookies = viewModel.getCookiesHeader()
    val activeMediaList by viewModel.activeMediaList.collectAsState()

    val displayMetrics = LocalContext.current.resources.displayMetrics
    val maxScreenDim = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
    val safeMaxDimension = when {
        isLegacyOrLowRamTv -> minOf(maxScreenDim, 1920).coerceAtLeast(1280)
        isTv -> minOf(maxScreenDim, 2560).coerceAtLeast(1920)
        else -> minOf(maxScreenDim * 2, 4096).coerceAtLeast(1920)
    }

    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val topBarFocusRequester = remember { FocusRequester() }
    val topBarRightmostFocusRequester = remember { FocusRequester() }
    val bottomBarFocusRequester = remember { FocusRequester() }
    val metadataCloseFocusRequester = remember { FocusRequester() }
    val firstMetadataSectionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            kotlinx.coroutines.delay(100)
            if (isTv && media.isVideo) {
                bottomBarFocusRequester.requestFocus()
            } else {
                focusRequester.requestFocus()
            }
        } catch (_: Exception) {
            runCatching { focusRequester.requestFocus() }
        }
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

    val slideshowDuration = viewModel.slideshowDuration.collectAsState().value
    var isSlideshowPlaying by remember { mutableStateOf(false) }

    // Intelligent Behavior-Analyzing Preloader
    var previousPage by remember { mutableIntStateOf(initialIndex) }
    var swipeDirection by remember { mutableIntStateOf(1) } // 1: forward, -1: backward
    var lastSwipeTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var consecutiveDirectionCount by remember { mutableIntStateOf(1) }
    val activePreloadDisposables = remember { mutableListOf<coil.request.Disposable>() }

    // Clean up all pending background requests when viewer is dismissed
    DisposableEffect(Unit) {
        onDispose {
            activePreloadDisposables.forEach { it.dispose() }
            activePreloadDisposables.clear()
        }
    }

    // 1. Gesture Tracking: As soon as user drags finger towards a page, immediately warm up that page
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            val target = pagerState.targetPage
            if (target != pagerState.currentPage && target in mediaList.indices) {
                val targetMedia = mediaList[target]
                if (!targetMedia.isVideo) {
                    val imageLoader = context.imageLoader
                    val thumbUrl = viewModel.getThumbnailUrl(targetMedia)
                    val preloadUrl = viewModel.getPreloadMediaUrl(targetMedia)
                    if (thumbUrl.isNotBlank()) {
                        activePreloadDisposables.add(
                            imageLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(thumbUrl)
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .apply { if (cookies.isNotEmpty()) addHeader("Cookie", cookies) }
                                    .build()
                            )
                        )
                    }
                    if (!preloadUrl.isNullOrBlank()) {
                        activePreloadDisposables.add(
                            imageLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(preloadUrl)
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .apply { if (cookies.isNotEmpty()) addHeader("Cookie", cookies) }
                                    .build()
                            )
                        )
                    }
                }
            }
        }
    }

    // 2. Behavioral Navigation Analysis & Tiered Pipeline
    LaunchedEffect(pagerState.currentPage, mediaList, isSlideshowPlaying) {
        val now = System.currentTimeMillis()
        val timeSinceLastSwipe = now - lastSwipeTime
        lastSwipeTime = now

        val page = pagerState.currentPage
        if (page > previousPage) {
            if (swipeDirection == 1) {
                consecutiveDirectionCount++
            } else {
                swipeDirection = 1
                consecutiveDirectionCount = 1
            }
        } else if (page < previousPage) {
            if (swipeDirection == -1) {
                consecutiveDirectionCount++
            } else {
                swipeDirection = -1
                consecutiveDirectionCount = 1
            }
        }
        previousPage = page

        // CANCEL OBSOLETE REQUESTS:
        // Abort previous background downloads that are no longer in the path to free OkHttp sockets
        activePreloadDisposables.forEach { it.dispose() }
        activePreloadDisposables.clear()

        if (currentMedia.isVideo) {
            return@LaunchedEffect
        }

        val isFastSkimming = timeSinceLastSwipe < 450L && !isSlideshowPlaying
        val imageLoader = context.imageLoader

        // Determine next targets based on user momentum & direction
        val primaryDir = if (isSlideshowPlaying) 1 else swipeDirection
        val secondaryDir = -primaryDir

        val targetIndices = when {
            consecutiveDirectionCount >= 2 -> listOf(
                page + primaryDir,
                page + (primaryDir * 2),
                page + secondaryDir,
                page + (primaryDir * 3)
            )
            else -> listOf(
                page + primaryDir,
                page + secondaryDir,
                page + (primaryDir * 2)
            )
        }

        val targetItems = targetIndices.mapNotNull { idx ->
            if (idx in mediaList.indices) mediaList[idx] else null
        }.filter { !it.isVideo }

        // TIER 1: THUMBNAILS (Always loaded first, 10-25KB, guaranteed 0ms black screen)
        for (targetMedia in targetItems) {
            val thumbUrl = viewModel.getThumbnailUrl(targetMedia)
            if (thumbUrl.isNotBlank()) {
                val req = ImageRequest.Builder(context)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                    .data(thumbUrl)
                    .apply {
                        if (cookies.isNotEmpty()) addHeader("Cookie", cookies)
                    }
                    .build()
                activePreloadDisposables.add(imageLoader.enqueue(req))
            }
        }

        // Fast-skimming filter: If user is flicking quickly through pictures, do NOT choke network with 720p/HD
        if (isFastSkimming) {
            return@LaunchedEffect
        }

        // TIER 2: 720p PRELOAD PREVIEWS (for the immediate next 1-2 images in swipe direction)
        val previewTargets = targetItems.take(if (consecutiveDirectionCount >= 2) 2 else 1)
        for (targetMedia in previewTargets) {
            val preloadUrl = viewModel.getPreloadMediaUrl(targetMedia)
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
                activePreloadDisposables.add(imageLoader.enqueue(req))
            }
        }

        // TIER 3: PREDICTIVE ORIGINAL RESOLUTION
        // Only if the user pauses on the current photo for >1.2s (or in slideshow mode),
        // preload the full original image for the single next photo (+1)
        if (!isTv) {
            val waitTimeMs = if (isSlideshowPlaying) 500L else 1200L
            kotlinx.coroutines.delay(waitTimeMs)

            val nextMedia = mediaList.getOrNull(page + primaryDir)
            if (nextMedia != null && !nextMedia.isVideo) {
                val origUrl = viewModel.getOriginalMediaUrl(nextMedia)
                if (origUrl.isNotBlank()) {
                    val req = ImageRequest.Builder(context)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                        .data(origUrl)
                        .size(safeMaxDimension, safeMaxDimension)
                        .precision(coil.size.Precision.INEXACT)
                        .apply {
                            if (cookies.isNotEmpty()) addHeader("Cookie", cookies)
                        }
                        .build()
                    activePreloadDisposables.add(imageLoader.enqueue(req))
                }
            }
        }
    }

    var showMetadata by remember { mutableStateOf(false) }
    var showBars by remember { mutableStateOf(true) }
    var showFaceRegions by remember { mutableStateOf(false) }
    var isVideoPlaying by remember(currentMedia) { mutableStateOf(true) }
    var isVideoMuted by remember(currentMedia) { mutableStateOf(false) }
    var isLivePhotoPlaying by remember(currentMedia) { mutableStateOf(false) }
    var videoCurrentPosition by remember(currentMedia) { mutableIntStateOf(0) }
    var isDraggingVideoSlider by remember(currentMedia) { mutableStateOf(false) }
    var videoSliderValue by remember(currentMedia) { mutableFloatStateOf(0f) }
    var seekFeedbackLabel by remember(currentMedia) { mutableStateOf<String?>(null) }
    var seekFeedbackToken by remember(currentMedia) { mutableIntStateOf(0) }

    LaunchedEffect(seekFeedbackToken) {
        if (seekFeedbackToken > 0) {
            kotlinx.coroutines.delay(1200L)
            seekFeedbackLabel = null
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            isLivePhotoPlaying = false
        }
    }

    val preparedVideoPlayers = remember { mutableStateMapOf<Int, VideoPlayerController>() }
    val videoDurations = remember { mutableStateMapOf<Int, Int>() }

    val activeVideoPlayer = preparedVideoPlayers[pagerState.currentPage]
    val metadataDurationMs = currentMedia.metadata?.duration?.toInt()?.takeIf { it > 0 } ?: 0
    val videoDuration = (videoDurations[pagerState.currentPage] ?: activeVideoPlayer?.duration?.toInt() ?: 0)
        .takeIf { it > 0 } ?: metadataDurationMs

    val seekBackBadge = stringResource(R.string.tv_video_seek_back_badge)
    val seekForwardBadge = stringResource(R.string.tv_video_seek_forward_badge)
    val restartBadge = stringResource(R.string.tv_video_restart_badge)

    val triggerSeekFeedback: (String) -> Unit = { label ->
        seekFeedbackLabel = label
        seekFeedbackToken++
    }

    val seekVideoBy: (Long, String?) -> Unit = { deltaMs, feedback ->
        val basePos = activeVideoPlayer?.currentPosition ?: videoCurrentPosition.toLong()
        val maxDur = videoDuration.toLong().coerceAtLeast(0L)
        val target = if (maxDur > 0L) {
            (basePos + deltaMs).coerceIn(0L, maxDur)
        } else {
            (basePos + deltaMs).coerceAtLeast(0L)
        }
        activeVideoPlayer?.seekTo(target)
        videoCurrentPosition = target.toInt()
        videoSliderValue = target.toFloat()
        if (!feedback.isNullOrBlank()) {
            triggerSeekFeedback(feedback)
        }
    }

    val restartVideo: () -> Unit = {
        activeVideoPlayer?.seekTo(0L)
        videoCurrentPosition = 0
        videoSliderValue = 0f
        isVideoPlaying = true
        triggerSeekFeedback(restartBadge)
    }

    LaunchedEffect(activeVideoPlayer, isVideoMuted) {
        activeVideoPlayer?.setMuted(isVideoMuted)
    }

    LaunchedEffect(isVideoPlaying, activeVideoPlayer, pagerState.currentPage, pagerState.isScrollInProgress) {
        val player = activeVideoPlayer
        if (player != null) {
            player.setMuted(isVideoMuted)
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
    var keepTopBarForMenu by remember { mutableStateOf(false) }
    LaunchedEffect(showMoreMenu) {
        if (showMoreMenu) {
            keepTopBarForMenu = true
        } else {
            kotlinx.coroutines.delay(200)
            keepTopBarForMenu = false
        }
    }
    
    var interactionCount by remember { mutableIntStateOf(0) }
    var topBarFocused by remember { mutableStateOf(false) }
    var bottomBarFocused by remember { mutableStateOf(false) }
    var sidePanelFocused by remember { mutableStateOf(false) }
    val isControlsFocused = topBarFocused || bottomBarFocused || showMoreMenu

    // Auto-hide bars after 4 seconds of inactivity
    LaunchedEffect(showBars, isSlideshowPlaying, interactionCount, isControlsFocused) {
        if (showBars && !isSlideshowPlaying && !isControlsFocused) {
            kotlinx.coroutines.delay(4000)
            showBars = false
        }
    }



    var videoCompletionTrigger by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isSlideshowPlaying) {
        if (!isSlideshowPlaying) return@LaunchedEffect
        if (mediaList.isEmpty()) {
            isSlideshowPlaying = false
            onDismiss()
            return@LaunchedEffect
        }
        
        while (isActive && isSlideshowPlaying) {
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
            
            // Wait if user currently has the menu open so viewer/bars do not dismiss underneath the popup
            while (isActive && showMoreMenu) {
                kotlinx.coroutines.delay(200)
            }
            if (!isActive || !isSlideshowPlaying) break

            // If we are still on the same page, advance or finish and dismiss fullscreen on the last item
            if (pagerState.currentPage == pageAtStart) {
                if (pageAtStart >= mediaList.lastIndex) {
                    isSlideshowPlaying = false
                    onDismiss()
                    break
                }
                val nextPage = pageAtStart + 1
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

    androidx.activity.compose.BackHandler {
        when {
            showMoreMenu -> showMoreMenu = false
            showMetadata -> {
                showMetadata = false
                try { focusRequester.requestFocus() } catch (_: Exception) {}
            }
            else -> onDismiss()
        }
    }

    val view = LocalView.current
    val window = remember(view) { context.findActivity()?.window }

    // Keep screen on when on fullscreen media viewer
    DisposableEffect(window) {
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(window) {
        window?.let { win ->
            try {
                WindowCompat.setDecorFitsSystemWindows(win, false)
            } catch (_: Exception) {}
        }
        onDispose {
            window?.let { win ->
                try {
                    WindowInsetsControllerCompat(win, win.decorView).let { controller ->
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                } catch (_: Exception) {}
            }
        }
    }

    DisposableEffect(showBars, window, isMapOpen) {
        window?.let { win ->
            try {
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
            } catch (_: Exception) {}
        }
        onDispose { }
    }

    val dismissGestureEnabled by viewModel.dismissGestureEnabled.collectAsState()
    val showMetadataGestureEnabled by viewModel.showMetadataGestureEnabled.collectAsState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (!isTv && (dismissGestureEnabled || showMetadataGestureEnabled)) {
                    Modifier.pointerInput(showMetadata, dismissGestureEnabled, showMetadataGestureEnabled) {
                        var totalDragY = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                totalDragY = 0f
                            },
                            onDragEnd = {
                                if (kotlin.math.abs(totalDragY) > 80f) {
                                    if (totalDragY < -80f) {
                                        if (showMetadataGestureEnabled) {
                                            showMetadata = true
                                        }
                                    } else if (totalDragY > 80f) {
                                        if (showMetadata) {
                                            showMetadata = false
                                        } else if (dismissGestureEnabled) {
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
                } else {
                    Modifier
                }
            )
    ) {
        val useSideMetadataPanel = isTv || maxWidth >= 600.dp
        val sidePanelWidth = if (maxWidth >= 960.dp) {
            (maxWidth * 0.34f).coerceIn(340.dp, 460.dp)
        } else {
            (maxWidth * 0.38f).coerceIn(280.dp, 360.dp)
        }

        var wasMetadataOpen by remember { mutableStateOf(false) }
        LaunchedEffect(showMetadata, useSideMetadataPanel, isTv) {
            if (isTv) {
                kotlinx.coroutines.delay(80)
                if (showMetadata && useSideMetadataPanel) {
                    runCatching { metadataCloseFocusRequester.requestFocus() }
                } else if (wasMetadataOpen && !showMetadata) {
                    if (currentMedia.isVideo && showBars) {
                        runCatching { bottomBarFocusRequester.requestFocus() }
                    } else {
                        runCatching { focusRequester.requestFocus() }
                    }
                }
            }
            wasMetadataOpen = showMetadata
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // Left Pane: Full media canvas + viewer overlays (never overlaps right metadata panel)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("media_viewer_media_pane")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .testTag("media_viewer_canvas_focus_target")
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                interactionCount++
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> {
                                        if (pagerState.currentPage > 0) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                            }
                                        }
                                        // Always consume on TV so focus never leaks off the first media item
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        if (pagerState.currentPage < mediaList.size - 1) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                            true
                                        } else if (showMetadata && useSideMetadataPanel) {
                                            runCatching { metadataCloseFocusRequester.requestFocus() }
                                            true
                                        } else {
                                            // Consume at last item so focus stays cleanly on media canvas
                                            true
                                        }
                                    }
                                    Key.DirectionUp -> {
                                        if (!showBars) {
                                            showBars = true
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(80)
                                                runCatching { topBarFocusRequester.requestFocus() }
                                            }
                                        } else {
                                            runCatching { topBarFocusRequester.requestFocus() }
                                        }
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        if (currentMedia.isVideo) {
                                            if (!showBars) {
                                                showBars = true
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(80)
                                                    runCatching { bottomBarFocusRequester.requestFocus() }
                                                }
                                            } else {
                                                runCatching { bottomBarFocusRequester.requestFocus() }
                                            }
                                            true
                                        } else if (showMetadata && useSideMetadataPanel) {
                                            val moved = runCatching {
                                                firstMetadataSectionFocusRequester.requestFocus()
                                                true
                                            }.getOrDefault(false)
                                            if (!moved) {
                                                runCatching { metadataCloseFocusRequester.requestFocus() }
                                            }
                                            true
                                        } else {
                                            // Consume so focus never leaks out of the viewer on non-video items
                                            true
                                        }
                                    }
                                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                        if (currentMedia.isVideo) {
                                            if (isTv) {
                                                if (!showBars) {
                                                    isVideoPlaying = !isVideoPlaying
                                                    showBars = true
                                                    coroutineScope.launch {
                                                        kotlinx.coroutines.delay(80)
                                                        runCatching { bottomBarFocusRequester.requestFocus() }
                                                    }
                                                } else {
                                                    runCatching { bottomBarFocusRequester.requestFocus() }
                                                }
                                            } else if (!showBars) {
                                                isVideoPlaying = !isVideoPlaying
                                                showBars = true
                                            } else {
                                                showBars = false
                                            }
                                        } else {
                                            showBars = !showBars
                                        }
                                        true
                                    }
                                    Key.MediaPlayPause -> {
                                        if (currentMedia.isVideo) {
                                            isVideoPlaying = !isVideoPlaying
                                            showBars = true
                                        } else {
                                            isSlideshowPlaying = !isSlideshowPlaying
                                            if (isSlideshowPlaying) showBars = false
                                        }
                                        true
                                    }
                                    Key.MediaPlay -> {
                                        if (currentMedia.isVideo) {
                                            isVideoPlaying = true
                                        } else {
                                            isSlideshowPlaying = true
                                            showBars = false
                                        }
                                        true
                                    }
                                    Key.MediaPause -> {
                                        if (currentMedia.isVideo) {
                                            isVideoPlaying = false
                                            showBars = true
                                        } else {
                                            isSlideshowPlaying = false
                                            showBars = true
                                        }
                                        true
                                    }
                                    Key.MediaFastForward -> {
                                        if (currentMedia.isVideo) {
                                            seekVideoBy(10_000L, seekForwardBadge)
                                            showBars = true
                                            true
                                        } else if (pagerState.currentPage < mediaList.size - 1) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                            true
                                        } else false
                                    }
                                    Key.MediaRewind -> {
                                        if (currentMedia.isVideo) {
                                            seekVideoBy(-10_000L, seekBackBadge)
                                            showBars = true
                                            true
                                        } else if (pagerState.currentPage > 0) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                            }
                                            true
                                        } else false
                                    }
                                    Key.Info, Key.Menu -> {
                                        showMetadata = !showMetadata
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
                        key = { page ->
                            val m = mediaList.getOrNull(page)
                            if (m != null) "${m.id ?: m.name}_${m.parentPath ?: ""}_$page" else page
                        },
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
                            isLivePhotoPlaying = if (page == pagerState.currentPage && !pagerState.isScrollInProgress) isLivePhotoPlaying else false,
                            onLivePhotoPlayingChange = { if (page == pagerState.currentPage) isLivePhotoPlaying = it },
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

                // Slide-up Metadata Overlay Sheet (Compact Phones only)
                if (!useSideMetadataPanel) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showMetadata,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(16.dp)
                                .testTag("metadata_bottom_sheet"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xEB14161A)
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            MetadataContent(
                                media = currentMedia,
                                viewModel = viewModel,
                                isSidePanel = false,
                                isTv = isTv,
                                closeFocusRequester = metadataCloseFocusRequester,
                                onNavigateBackToMedia = {
                                    try { focusRequester.requestFocus() } catch (_: Exception) {}
                                },
                                onClose = { showMetadata = false }
                            )
                        }
                    }
                }

                // Top Bar Overlay (scoped to Left Pane so it never collides with Right Metadata Panel)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showBars || keepTopBarForMenu,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(
                                horizontal = if (isTv) 20.dp else 8.dp,
                                vertical = if (isTv) 8.dp else 4.dp
                            )
                            .focusGroup()
                            .onFocusChanged { topBarFocused = it.hasFocus },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .focusRequester(topBarFocusRequester)
                                    .focusProperties {
                                        up = FocusRequester.Cancel
                                        left = FocusRequester.Cancel
                                        down = focusRequester
                                    }
                                    .tvFocus(shape = CircleShape)
                                    .testTag("viewer_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.close),
                                    tint = Color.White
                                )
                            }

                            if (currentMedia.isLivePhoto && !currentMedia.isVideo) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    onClick = { isLivePhotoPlaying = !isLivePhotoPlaying },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isLivePhotoPlaying) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.White.copy(alpha = 0.16f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isLivePhotoPlaying) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 36.dp)
                                        .focusProperties {
                                            up = FocusRequester.Cancel
                                            down = focusRequester
                                        }
                                        .testTag("live_photo_toggle")
                                        .tvFocus(shape = RoundedCornerShape(20.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isLivePhotoPlaying) Icons.Default.MotionPhotosAuto else Icons.Default.MotionPhotosOn,
                                            contentDescription = if (isLivePhotoPlaying) stringResource(R.string.stop_live_photo) else stringResource(R.string.play_live_photo),
                                            tint = if (isLivePhotoPlaying) MaterialTheme.colorScheme.onPrimary else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isLivePhotoPlaying) "LIVE ●" else "LIVE",
                                            color = if (isLivePhotoPlaying) MaterialTheme.colorScheme.onPrimary else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp
                                        )
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isTv) {
                                IconButton(
                                    onClick = {
                                        val willPlay = !isSlideshowPlaying
                                        isSlideshowPlaying = willPlay
                                        if (willPlay) showBars = false
                                    },
                                    modifier = Modifier
                                        .focusProperties {
                                            up = FocusRequester.Cancel
                                            down = focusRequester
                                        }
                                        .tvFocus(shape = CircleShape)
                                        .testTag("viewer_slideshow_button")
                                ) {
                                    Icon(
                                        imageVector = if (isSlideshowPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isSlideshowPlaying) stringResource(R.string.slideshow_stop) else stringResource(R.string.slideshow_start),
                                        tint = if (isSlideshowPlaying) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    showMetadata = !showMetadata
                                },
                                modifier = Modifier
                                    .focusProperties {
                                        up = FocusRequester.Cancel
                                        down = focusRequester
                                    }
                                    .tvFocus(shape = CircleShape)
                                    .testTag("viewer_metadata_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = stringResource(R.string.metadata_cd),
                                    tint = if (showMetadata) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }

                            if (tvCapabilities.allowMapView && currentMedia.metadata?.gps != null && onOpenMap != null) {
                                IconButton(
                                    onClick = {
                                        onOpenMap(currentMedia)
                                    },
                                    modifier = Modifier
                                        .focusProperties {
                                            up = FocusRequester.Cancel
                                            down = focusRequester
                                        }
                                        .tvFocus(shape = CircleShape)
                                        .testTag("viewer_map_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = stringResource(R.string.show_on_map),
                                        tint = Color.White
                                    )
                                }
                            }

                            if (tvCapabilities.allowSharing) {
                                IconButton(
                                    onClick = {
                                        viewModel.shareSingleMedia(context, currentMedia)
                                    },
                                    modifier = Modifier
                                        .focusProperties {
                                            up = FocusRequester.Cancel
                                            down = focusRequester
                                        }
                                        .tvFocus(shape = CircleShape)
                                        .testTag("viewer_share_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = stringResource(R.string.share),
                                        tint = Color.White
                                    )
                                }
                            }

                            if (tvCapabilities.allowDownloads) {
                                IconButton(
                                    onClick = {
                                        downloadFile(context, mediaUrl, currentMedia.name, cookies)
                                    },
                                    modifier = Modifier
                                        .focusProperties {
                                            up = FocusRequester.Cancel
                                            down = focusRequester
                                        }
                                        .tvFocus(shape = CircleShape)
                                        .testTag("viewer_download_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = stringResource(R.string.download),
                                        tint = Color.White
                                    )
                                }
                            }

                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier
                                        .focusRequester(topBarRightmostFocusRequester)
                                        .focusProperties {
                                            up = FocusRequester.Cancel
                                            down = focusRequester
                                            right = if (showMetadata && useSideMetadataPanel) {
                                                metadataCloseFocusRequester
                                            } else {
                                                FocusRequester.Cancel
                                            }
                                        }
                                        .tvFocus(shape = CircleShape)
                                        .testTag("viewer_more_button")
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options), tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)),
                                        text = {
                                            Text(
                                                if (isSlideshowPlaying) stringResource(R.string.slideshow_stop)
                                                else stringResource(R.string.slideshow_start)
                                            )
                                        },
                                        onClick = {
                                            if (showMoreMenu) {
                                                showMoreMenu = false
                                                val willPlay = !isSlideshowPlaying
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(200)
                                                    isSlideshowPlaying = willPlay
                                                    if (willPlay && !showMoreMenu) {
                                                        showBars = false
                                                    }
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isSlideshowPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                    if (!currentMedia.isVideo) {
                                        DropdownMenuItem(
                                            modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)),
                                            text = { Text(stringResource(R.string.rotate)) },
                                            onClick = {
                                                if (showMoreMenu) {
                                                    showMoreMenu = false
                                                    val page = pagerState.currentPage
                                                    coroutineScope.launch {
                                                        kotlinx.coroutines.delay(200)
                                                        rotationMap[page] = ((rotationMap[page] ?: 0f) + 90f) % 360f
                                                    }
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                                    contentDescription = null
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp)),
                                            text = {
                                                Text(
                                                    if (showFaceRegions) stringResource(R.string.hide_face_regions)
                                                    else stringResource(R.string.show_face_regions)
                                                )
                                            },
                                            onClick = {
                                                if (showMoreMenu) {
                                                    showMoreMenu = false
                                                    val nextShowFaceRegions = !showFaceRegions
                                                    coroutineScope.launch {
                                                        kotlinx.coroutines.delay(200)
                                                        showFaceRegions = nextShowFaceRegions
                                                    }
                                                }
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
                }

                // Bottom Bar Overlay (scoped to Left Pane)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showBars,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    if (isTv && currentMedia.isVideo) {
                        if (!isDraggingVideoSlider) {
                            videoSliderValue = videoCurrentPosition.toFloat()
                        }
                        TvVideoControlsBar(
                            title = currentMedia.name,
                            pageIndicator = "${pagerState.currentPage + 1} / ${mediaList.size}",
                            isPlaying = isVideoPlaying,
                            isMuted = isVideoMuted,
                            currentPositionMs = videoCurrentPosition,
                            durationMs = videoDuration,
                            sliderValue = videoSliderValue,
                            isDraggingSlider = isDraggingVideoSlider,
                            seekFeedbackLabel = seekFeedbackLabel,
                            playPauseFocusRequester = bottomBarFocusRequester,
                            mediaCanvasFocusRequester = focusRequester,
                            sidePanelFocusRequester = if (showMetadata && useSideMetadataPanel) {
                                metadataCloseFocusRequester
                            } else {
                                null
                            },
                            onTogglePlayPause = { isVideoPlaying = !isVideoPlaying },
                            onSeekBackward = { seekVideoBy(-10_000L, seekBackBadge) },
                            onSeekForward = { seekVideoBy(10_000L, seekForwardBadge) },
                            onRestart = restartVideo,
                            onToggleMute = { isVideoMuted = !isVideoMuted },
                            onSliderChange = { newValue ->
                                isDraggingVideoSlider = true
                                videoSliderValue = newValue
                            },
                            onSliderChangeFinished = {
                                isDraggingVideoSlider = false
                                activeVideoPlayer?.seekTo(videoSliderValue.toLong())
                                videoCurrentPosition = videoSliderValue.toInt()
                            },
                            onTvSliderStep = { newValue ->
                                videoSliderValue = newValue
                                activeVideoPlayer?.seekTo(newValue.toLong())
                                videoCurrentPosition = newValue.toInt()
                            },
                            onUserInteraction = { interactionCount++ },
                            onFocusChanged = { bottomBarFocused = it }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.75f))
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start))
                                .padding(
                                    horizontal = if (isTv) 24.dp else 16.dp,
                                    vertical = 16.dp
                                )
                                .focusGroup()
                                .onFocusChanged { bottomBarFocused = it.hasFocus }
                        ) {
                            if (currentMedia.isVideo) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = { isVideoPlaying = !isVideoPlaying },
                                        modifier = Modifier
                                            .focusRequester(bottomBarFocusRequester)
                                            .focusProperties {
                                                up = focusRequester
                                                down = FocusRequester.Cancel
                                                left = FocusRequester.Cancel
                                            }
                                            .tvFocus(shape = CircleShape)
                                            .testTag("viewer_video_play_pause_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isVideoPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
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
                                    val maxDurationFloat = videoDuration.toFloat().coerceAtLeast(1f)
                                    val seekStep = (maxDurationFloat / 20f).coerceIn(5_000f, 30_000f)
                                    val seekSteps = ((maxDurationFloat / seekStep).toInt() - 1).coerceIn(1, 50)
                                    Slider(
                                        value = videoSliderValue.coerceIn(0f, maxDurationFloat),
                                        onValueChange = { newValue ->
                                            isDraggingVideoSlider = true
                                            videoSliderValue = newValue
                                        },
                                        onValueChangeFinished = {
                                            isDraggingVideoSlider = false
                                            activeVideoPlayer?.seekTo(videoSliderValue.toLong())
                                            videoCurrentPosition = videoSliderValue.toInt()
                                        },
                                        valueRange = 0f..maxDurationFloat,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .tvFocus(shape = RoundedCornerShape(12.dp))
                                            .padding(horizontal = 4.dp)
                                            .focusProperties {
                                                up = focusRequester
                                                down = FocusRequester.Cancel
                                                right = if (showMetadata && useSideMetadataPanel) {
                                                    metadataCloseFocusRequester
                                                } else {
                                                    FocusRequester.Cancel
                                                }
                                            }
                                            .tvSlider(
                                                value = videoSliderValue.coerceIn(0f, maxDurationFloat),
                                                onValueChange = { newValue ->
                                                    interactionCount++
                                                    videoSliderValue = newValue
                                                    activeVideoPlayer?.seekTo(newValue.toLong())
                                                    videoCurrentPosition = newValue.toInt()
                                                },
                                                valueRange = 0f..maxDurationFloat,
                                                steps = seekSteps
                                            )
                                            .testTag("viewer_video_seek_slider")
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
                }

                // Compact Diashow "x/y" indicator when playing with bars hidden
                if (isSlideshowPlaying && !showBars && mediaList.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(16.dp)
                            .testTag("diashow_counter_indicator")
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${mediaList.size}",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Share/Download Progress Indicator Overlay
                val shareProgress by viewModel.shareProgress.collectAsState()
                if (!isTv && shareProgress >= 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = if (showBars) 64.dp else 0.dp)
                    ) {
                        if (shareProgress == 0f) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { shareProgress },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }
            }

            // Right Pane: Docked Full-Height Metadata Panel on Android TV & Tablet
            AnimatedVisibility(
                visible = showMetadata && useSideMetadataPanel,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .width(sidePanelWidth)
                        .fillMaxHeight()
                        .focusGroup()
                        .onFocusChanged { sidePanelFocused = it.hasFocus }
                        .testTag("metadata_side_panel"),
                    color = Color(0xFF14161A),
                    contentColor = Color.White
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        VerticalDivider(
                            thickness = 1.dp,
                            color = Color.White.copy(alpha = 0.14f)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(
                                        WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End
                                    )
                                )
                                .then(
                                    if (isTv) Modifier.padding(end = 12.dp, top = 8.dp, bottom = 8.dp) else Modifier
                                )
                        ) {
                            MetadataContent(
                                media = currentMedia,
                                viewModel = viewModel,
                                isSidePanel = true,
                                isTv = isTv,
                                closeFocusRequester = metadataCloseFocusRequester,
                                firstSectionFocusRequester = firstMetadataSectionFocusRequester,
                                onNavigateLeftFromClose = {
                                    val movedToTopBar = if (showBars || keepTopBarForMenu) {
                                        runCatching {
                                            topBarRightmostFocusRequester.requestFocus()
                                            true
                                        }.getOrDefault(false)
                                    } else {
                                        false
                                    }
                                    if (!movedToTopBar) {
                                        runCatching { focusRequester.requestFocus() }
                                    }
                                },
                                onNavigateBackToMedia = {
                                    runCatching { focusRequester.requestFocus() }
                                },
                                onClose = {
                                    showMetadata = false
                                    runCatching { focusRequester.requestFocus() }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatFileSize(bytes: Long, unknownLabel: String = "Unknown"): String {
    if (bytes <= 0) return unknownLabel
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun MetadataContent(
    media: ApiMedia,
    viewModel: GalleryViewModel,
    isSidePanel: Boolean = false,
    isTv: Boolean = false,
    closeFocusRequester: FocusRequester = remember { FocusRequester() },
    firstSectionFocusRequester: FocusRequester = remember { FocusRequester() },
    onNavigateLeftFromClose: () -> Unit = {},
    onNavigateBackToMedia: () -> Unit = {},
    onClose: () -> Unit
) {
    var currentFileSize by remember(media.id) { mutableStateOf(media.metadata?.fileSize) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(media.id, currentFileSize) {
        if (currentFileSize == null || currentFileSize == 0L) {
            val mediaUrl = viewModel.getOriginalMediaUrl(media)
            if (mediaUrl.startsWith("http")) {
                withContext(Dispatchers.IO) {
                    try {
                        val req = Request.Builder()
                            .url(mediaUrl)
                            .apply {
                                val userCookies = viewModel.getCookiesHeader()
                                if (userCookies.isNotEmpty()) addHeader("Cookie", userCookies)
                            }
                            .head()
                            .build()
                        viewModel.api.client.newCall(req).execute().use { resp ->
                            val len = resp.header("Content-Length")?.toLongOrNull()
                            if (len != null && len > 0) {
                                currentFileSize = len
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore
                    }
                }
            }
        }
    }

    val unknownLabel = stringResource(R.string.unknown)
    val size = media.metadata?.size
    val dateStr = DateUtils.formatMediaDate(media.metadata?.creationDate, media.metadata?.creationDateOffset, unknownLabel)
    val mediaTypeLabel = when {
        media.isVideo -> stringResource(R.string.type_video)
        media.isLivePhoto -> stringResource(R.string.type_live_photo)
        else -> stringResource(R.string.type_image)
    }
    val camera = media.metadata?.cameraData
    val cameraName = camera?.let { listOfNotNull(it.make, it.model).joinToString(" ") }.orEmpty()
    val hasCameraDetails = camera != null && (
        cameraName.isNotBlank() ||
            !camera.lens.isNullOrBlank() ||
            camera.focalLength != null ||
            camera.ISO != null ||
            camera.fStop != null ||
            camera.exposure != null
        )
    val gps = media.metadata?.gps
    val keywords = media.metadata?.keywords?.distinct().orEmpty()
    val faceNames = media.metadata?.faces?.mapNotNull { it.name }?.filter { it.isNotBlank() }.orEmpty()
    val hasTagsOrPeople = keywords.isNotEmpty() || faceNames.isNotEmpty()

    val isLastFileSection = !hasCameraDetails && gps == null && !hasTagsOrPeople
    val isLastCameraSection = hasCameraDetails && gps == null && !hasTagsOrPeople
    val isLastLocationSection = gps != null && !hasTagsOrPeople

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isSidePanel) 20.dp else 16.dp, vertical = if (isSidePanel) 16.dp else 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.metadata_info),
                    color = Color.White,
                    style = if (isSidePanel) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isSidePanel) {
                    Text(
                        text = media.name,
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .focusRequester(closeFocusRequester)
                    .then(
                        if (isSidePanel) {
                            Modifier
                                .focusProperties {
                                    up = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                    down = firstSectionFocusRequester
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                                        onNavigateLeftFromClose()
                                        true
                                    } else {
                                        false
                                    }
                                }
                        } else {
                            Modifier
                        }
                    )
                    .tvFocus(shape = CircleShape)
                    .testTag(if (isSidePanel) "metadata_side_panel_close_button" else "metadata_bottom_sheet_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(
            color = Color.White.copy(alpha = 0.14f),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = isSidePanel)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(if (isSidePanel) 14.dp else 10.dp)
        ) {
            if (isSidePanel) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetadataQuickChip(label = mediaTypeLabel)
                    if (size != null && size.width != null && size.height != null) {
                        MetadataQuickChip(label = "${size.width} × ${size.height}")
                    }
                    currentFileSize?.let { bytes ->
                        if (bytes > 0) {
                            MetadataQuickChip(label = formatFileSize(bytes, unknownLabel))
                        }
                    }
                }
            }

            MetadataSectionContainer(
                title = if (isSidePanel) stringResource(R.string.meta_section_file) else null,
                isSidePanel = isSidePanel,
                sectionTag = "metadata_section_file",
                sectionFocusRequester = firstSectionFocusRequester,
                upFocusRequester = closeFocusRequester,
                isLastSection = isLastFileSection,
                scrollState = scrollState,
                onNavigateBackToMedia = onNavigateBackToMedia
            ) {
                MetadataRow(icon = Icons.AutoMirrored.Outlined.InsertDriveFile, label = stringResource(R.string.meta_filename), value = media.name)
                media.parentPath?.let {
                    val cleanedPath = it.replace(Regex("/{2,}"), "/")
                    MetadataRow(icon = Icons.Outlined.Folder, label = stringResource(R.string.meta_folder), value = cleanedPath)
                }
                currentFileSize?.let { bytes ->
                    if (bytes > 0) {
                        MetadataRow(icon = Icons.Outlined.SdCard, label = stringResource(R.string.meta_file_size), value = formatFileSize(bytes, unknownLabel))
                    }
                }
                if (size != null && size.width != null && size.height != null) {
                    MetadataRow(icon = Icons.Outlined.AspectRatio, label = stringResource(R.string.meta_dimensions), value = "${size.width} × ${size.height} px")
                }
                MetadataRow(icon = Icons.Outlined.DateRange, label = stringResource(R.string.meta_date_taken), value = dateStr)
                MetadataRow(
                    icon = when {
                        media.isVideo -> Icons.Outlined.Videocam
                        media.isLivePhoto -> Icons.Outlined.MotionPhotosOn
                        else -> Icons.Outlined.Image
                    },
                    label = stringResource(R.string.meta_type),
                    value = mediaTypeLabel
                )
            }

            if (hasCameraDetails) {
                val cameraDetails = camera!!
                MetadataSectionContainer(
                    title = if (isSidePanel) stringResource(R.string.meta_section_camera) else null,
                    isSidePanel = isSidePanel,
                    sectionTag = "metadata_section_camera",
                    isLastSection = isLastCameraSection,
                    scrollState = scrollState,
                    onNavigateBackToMedia = onNavigateBackToMedia
                ) {
                    if (cameraName.isNotBlank()) {
                        MetadataRow(icon = Icons.Outlined.CameraAlt, label = stringResource(R.string.meta_camera), value = cameraName)
                    }
                    cameraDetails.lens?.let { lens ->
                        if (lens.isNotBlank()) {
                            MetadataRow(icon = CameraLensSide, label = stringResource(R.string.meta_lens), value = lens)
                        }
                    }
                    cameraDetails.focalLength?.let { focal ->
                        val focalLengthStr = if (focal % 1.0 == 0.0) "${focal.toInt()} mm" else "$focal mm"
                        MetadataRow(icon = Icons.Outlined.Straighten, label = stringResource(R.string.meta_focal_length), value = focalLengthStr)
                    }
                    cameraDetails.ISO?.let { MetadataRow(icon = Icons.Outlined.Iso, label = stringResource(R.string.meta_iso), value = it.toString()) }
                    cameraDetails.fStop?.let { MetadataRow(icon = ApertureIcon, label = stringResource(R.string.meta_aperture), value = "f/$it") }
                    cameraDetails.exposure?.let {
                        val exposureStr = if (it < 1.0 && it > 0.0) "1/${(1.0 / it).toInt()}s" else "${it}s"
                        MetadataRow(icon = Icons.Outlined.Timer, label = stringResource(R.string.meta_exposure_time), value = exposureStr)
                    }
                }
            }

            if (gps != null) {
                MetadataSectionContainer(
                    title = if (isSidePanel) stringResource(R.string.meta_section_location) else null,
                    isSidePanel = isSidePanel,
                    sectionTag = "metadata_section_location",
                    isLastSection = isLastLocationSection,
                    scrollState = scrollState,
                    onNavigateBackToMedia = onNavigateBackToMedia
                ) {
                    val gpsStr = String.format(java.util.Locale.US, "%.6f, %.6f", gps.latitude, gps.longitude)
                    MetadataRow(icon = Icons.Outlined.LocationOn, label = stringResource(R.string.meta_gps_coordinates), value = gpsStr)
                }
            }

            if (hasTagsOrPeople) {
                MetadataSectionContainer(
                    title = if (isSidePanel) stringResource(R.string.meta_section_tags) else null,
                    isSidePanel = isSidePanel,
                    sectionTag = "metadata_section_tags",
                    isLastSection = true,
                    scrollState = scrollState,
                    onNavigateBackToMedia = onNavigateBackToMedia
                ) {
                    if (keywords.isNotEmpty()) {
                        MetadataRow(icon = Icons.AutoMirrored.Outlined.Label, label = stringResource(R.string.meta_keywords), value = keywords.joinToString(", "))
                    }
                    if (faceNames.isNotEmpty()) {
                        MetadataRow(icon = Icons.Outlined.Person, label = stringResource(R.string.meta_people), value = faceNames.joinToString(", "))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataQuickChip(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun MetadataSectionContainer(
    title: String?,
    isSidePanel: Boolean,
    sectionTag: String,
    sectionFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    isLastSection: Boolean = false,
    scrollState: androidx.compose.foundation.ScrollState? = null,
    onNavigateBackToMedia: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    if (!isSidePanel) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        return
    }
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title.uppercase(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            )
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isFocused) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.06f),
            border = BorderStroke(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.09f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (sectionFocusRequester != null) Modifier.focusRequester(sectionFocusRequester) else Modifier)
                .focusProperties {
                    right = FocusRequester.Cancel
                    if (upFocusRequester != null) {
                        up = upFocusRequester
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                onNavigateBackToMedia()
                                true
                            }
                            Key.DirectionDown -> {
                                if (isLastSection) {
                                    if (scrollState != null && scrollState.value < scrollState.maxValue) {
                                        coroutineScope.launch {
                                            scrollState.animateScrollTo((scrollState.value + 160).coerceAtMost(scrollState.maxValue))
                                        }
                                    }
                                    // Keep focus on last section rather than jumping to a dead spot
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .tvFocus(shape = RoundedCornerShape(14.dp))
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) {
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                }
                .focusable()
                .testTag(sectionTag)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
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
    isLivePhotoPlaying: Boolean = false,
    onLivePhotoPlayingChange: (Boolean) -> Unit = {},
    onVideoCompletion: () -> Unit,
    onToggleBars: () -> Unit,
    showBars: Boolean,
    onVideoPrepared: (duration: Int, controller: VideoPlayerController) -> Unit,
    onDisposeVideo: (() -> Unit)? = null
) {
    val isTv = rememberTvCapabilities().isTvMode
    val isLegacyOrLowRamTv = remember(context) { DeviceUtils.isLegacyOrLowRamTv(context) }
    val mediaUrl = viewModel.getOriginalMediaUrl(media)

    // Pinch to Zoom states (for images)
    var scale by remember(media.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(media.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(media.id) { mutableFloatStateOf(0f) }

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

            val exoPlayer = remember(media.id, viewModel.api.client, isLegacyOrLowRamTv) {
                // Lighter memory buffers on older Android 9 / low-RAM TV hardware
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ if (isLegacyOrLowRamTv) 10_000 else 20_000,
                        /* maxBufferMs = */ if (isLegacyOrLowRamTv) 30_000 else 60_000,
                        /* bufferForPlaybackMs = */ if (isLegacyOrLowRamTv) 1_500 else 1_000,
                        /* bufferForPlaybackAfterRebufferMs = */ 2_500
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .setBackBuffer(if (isLegacyOrLowRamTv) 5_000 else 15_000, true)
                    .build()

                // Reuse shared OkHttpClient pool & SSL config (supports self-signed certs), without HTTP response cache for streaming
                val videoCallFactory = viewModel.api.client.newBuilder()
                    .cache(null)
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val dataSourceFactory = OkHttpDataSource.Factory(videoCallFactory)
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
                    override val isMuted: Boolean get() = exoPlayer.volume == 0f
                    override fun play() { exoPlayer.play() }
                    override fun pause() { exoPlayer.pause() }
                    override fun seekTo(positionMs: Long) { exoPlayer.seekTo(positionMs) }
                    override fun setMuted(muted: Boolean) { exoPlayer.volume = if (muted) 0f else 1f }
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
                    exoPlayer.stop()
                    exoPlayer.release()
                    onDisposeVideo?.invoke()
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
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = stringResource(R.string.video_playback_failed),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = {
                                hasError = false
                                isPreparing = true
                                isBuffering = true
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                                onVideoPlayingChange(true)
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("video_retry_button")
                                .tvFocus(shape = RoundedCornerShape(20.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
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
                        text = stringResource(R.string.buffering_video),
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

            var isThumbnailLoaded by remember(media.id) { mutableStateOf(false) }
            var isPreloadLoaded by remember(media.id) { mutableStateOf(false) }
            var isFullImageLoaded by remember(media.id) { mutableStateOf(false) }
            var shouldLoadFullImage by remember(media.id) { mutableStateOf(false) }

            val displayMetrics = LocalContext.current.resources.displayMetrics
            val maxScreenDim = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
            val safeMaxDimension = when {
                isLegacyOrLowRamTv -> minOf(maxScreenDim, 1920).coerceAtLeast(1280)
                isTv -> minOf(maxScreenDim, 2560).coerceAtLeast(1920)
                else -> minOf(maxScreenDim * 2, 3840).coerceAtLeast(1920)
            }

            // Step 1: Minimum thumbnail request (always loaded as baseline, instant from disk cache or small network fetch)
            val thumbnailRequest = remember(thumbnailUrl, cookies) {
                ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                    .apply {
                        if (cookies.isNotEmpty()) addHeader("Cookie", cookies)
                    }
                    .build()
            }

            // Step 2: Preload image request (loaded on top of thumbnail)
            val preloadRequest = remember(preloadUrl, cookies) {
                if (!preloadUrl.isNullOrBlank() && preloadUrl != thumbnailUrl) {
                    ImageRequest.Builder(context)
                        .data(preloadUrl)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                        .crossfade(150)
                        .apply {
                            if (cookies.isNotEmpty()) addHeader("Cookie", cookies)
                        }
                        .build()
                } else null
            }

            // Progressive stage trigger:
            // 1. Minimum thumbnail always loads immediately.
            // 2. Preload loads immediately.
            // 3. Full image triggers when this page is the current active page,
            //    after preload has loaded (or timeout), or if user zooms.
            LaunchedEffect(isCurrentPage, isPreloadLoaded, scale) {
                if (isCurrentPage) {
                    if (scale > 1f || preloadRequest == null) {
                        shouldLoadFullImage = true
                    } else if (isPreloadLoaded) {
                        shouldLoadFullImage = true
                    } else {
                        // Short grace period to give preload a chance to display instantly first
                        kotlinx.coroutines.delay(800)
                        shouldLoadFullImage = true
                    }
                } else {
                    // Swiped away: cancel heavy full image to free up memory and network
                    shouldLoadFullImage = false
                    isFullImageLoaded = false
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                }
            }

            // Step 3: Full image request
            val fullImageRequest = remember(originalUrl, cookies, safeMaxDimension) {
                ImageRequest.Builder(context)
                    .data(originalUrl)
                    .size(safeMaxDimension, safeMaxDimension)
                    .precision(coil.size.Precision.INEXACT)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                    .crossfade(150)
                    .apply {
                        if (cookies.isNotEmpty()) addHeader("Cookie", cookies)
                    }
                    .build()
            }

            val initialIntrinsicSize = remember(media) {
                val w = media.metadata?.size?.width?.toFloat()
                val h = media.metadata?.size?.height?.toFloat()
                if (w != null && h != null && w > 0f && h > 0f) {
                    Size(w, h)
                } else {
                    Size.Zero
                }
            }
            var intrinsicSize by remember(media.id) { mutableStateOf(initialIntrinsicSize) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(media.id, media.isLivePhoto, isLivePhotoPlaying) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                    shouldLoadFullImage = true
                                }
                            },
                            onLongPress = {
                                if (media.isLivePhoto && !media.isVideo) {
                                    onLivePhotoPlayingChange(!isLivePhotoPlaying)
                                }
                            },
                            onTap = {
                                if (isLivePhotoPlaying) {
                                    onLivePhotoPlayingChange(false)
                                } else {
                                    onToggleBars()
                                }
                            }
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
                                        if (nextScale > 1f) {
                                            shouldLoadFullImage = true
                                        }

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
                    },
                contentAlignment = Alignment.Center
            ) {
                // 1. Minimum Thumbnail: always rendered as solid base layer (fastest to load or already in disk cache)
                AsyncImage(
                    model = thumbnailRequest,
                    contentDescription = media.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { state ->
                        isThumbnailLoaded = true
                        if (intrinsicSize == Size.Zero) {
                            intrinsicSize = state.painter.intrinsicSize
                        }
                    }
                )

                // 2. Preload Image: rendered on top of thumbnail once loaded
                if (preloadRequest != null) {
                    AsyncImage(
                        model = preloadRequest,
                        contentDescription = media.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { state ->
                            isPreloadLoaded = true
                            intrinsicSize = state.painter.intrinsicSize
                        },
                        onError = {
                            if (isCurrentPage) {
                                shouldLoadFullImage = true
                            }
                        }
                    )
                }

                // 3. Full Image: rendered on top of preload/thumbnail once loaded (capped safely on TV)
                if (shouldLoadFullImage) {
                    AsyncImage(
                        model = fullImageRequest,
                        contentDescription = media.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { state ->
                            isFullImageLoaded = true
                            intrinsicSize = state.painter.intrinsicSize
                        }
                    )
                }

                // 4. Apple Live Photo companion motion video overlay
                if (media.isLivePhoto && isLivePhotoPlaying && isCurrentPage) {
                    val liveVideoUrl = remember(media.id, media.parentPath, media.name, media.metadata?.liveVideoPath) {
                        viewModel.getLiveVideoUrl(media)
                    }
                    if (!liveVideoUrl.isNullOrBlank()) {
                        LivePhotoVideoOverlay(
                            videoUrl = liveVideoUrl,
                            cookies = cookies,
                            okHttpClient = viewModel.api.client,
                            context = context,
                            onPlaybackFinished = { onLivePhotoPlayingChange(false) },
                            onPlaybackError = {
                                onLivePhotoPlayingChange(false)
                                Toast.makeText(context, context.getString(R.string.live_photo_unavailable), Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            onLivePhotoPlayingChange(false)
                        }
                    }
                }

                // Initial loading indicator before thumbnail arrives
                if (!isThumbnailLoaded && !isPreloadLoaded && !isFullImageLoaded) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White.copy(alpha = 0.6f),
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LivePhotoVideoOverlay(
    videoUrl: String,
    cookies: String,
    okHttpClient: OkHttpClient,
    context: Context,
    onPlaybackFinished: () -> Unit,
    onPlaybackError: () -> Unit
) {
    var firstFrameRendered by remember(videoUrl) { mutableStateOf(false) }

    val exoPlayer = remember(videoUrl, cookies, okHttpClient) {
        val callFactory = okHttpClient.newBuilder()
            .cache(null)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(callFactory)
            .apply {
                if (cookies.isNotEmpty()) {
                    setDefaultRequestProperties(mapOf("Cookie" to cookies))
                }
            }

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUrl))

        ExoPlayer.Builder(context)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build().apply {
                setMediaSource(mediaSource)
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    onPlaybackFinished()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onPlaybackError()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    val overlayAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (firstFrameRendered) 1f else 0f,
        label = "live_photo_alpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    player = exoPlayer
                }
            },
            update = { view ->
                view.player = exoPlayer
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = overlayAlpha }
        )

        if (!firstFrameRendered) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = stringResource(R.string.loading_live),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
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

private fun downloadFile(context: Context, url: String, fileName: String, cookies: String) {
    try {
        val request = DownloadManager.Request(url.toUri())
            .setTitle(fileName)
            .setDescription(context.getString(R.string.download_description))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .apply {
                if (cookies.isNotEmpty()) {
                    addRequestHeader("Cookie", cookies)
                }
            }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, context.getString(R.string.download_started_single, fileName), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.download_failed_single, e.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
    }
}

internal fun formatTime(ms: Int): String {
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