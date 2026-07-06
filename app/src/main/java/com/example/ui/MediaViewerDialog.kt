package com.example.ui

import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.MediaController
import android.widget.VideoView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons







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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaViewerDialog(
    media: ApiMedia,
    viewModel: GalleryViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val cookies = viewModel.getCookiesHeader()
    val activeMediaList by viewModel.activeMediaList.collectAsState()

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

    var showMetadata by remember { mutableStateOf(false) }
    var showBars by remember { mutableStateOf(true) }
    var showFaceRegions by remember { mutableStateOf(false) }
    
    val slideshowDuration = viewModel.slideshowDuration.collectAsState().value
    var isSlideshowPlaying by remember { mutableStateOf(false) }

    // Auto-hide bars after 4 seconds of inactivity
    LaunchedEffect(showBars, isSlideshowPlaying) {
        if (showBars && !isSlideshowPlaying) {
            kotlinx.coroutines.delay(4000)
            showBars = false
        }
    }

    val imageLoader = context.imageLoader
    LaunchedEffect(pagerState.currentPage, mediaList) {
        val start = (pagerState.currentPage - 3).coerceAtLeast(0)
        val end = (pagerState.currentPage + 3).coerceAtMost(mediaList.size - 1)
        for (i in start..end) {
            if (i != pagerState.currentPage) {
                val prefetchMedia = mediaList[i]
                if (!prefetchMedia.isVideo) {
                    val prefetchUrl = viewModel.getOriginalMediaUrl(prefetchMedia)
                    val request = ImageRequest.Builder(context)
                        .data(prefetchUrl)
                        .addHeader("Cookie", cookies)
                        .build()
                    imageLoader.enqueue(request)
                }
            }
        }
    }

    LaunchedEffect(isSlideshowPlaying) {
        while (isSlideshowPlaying) {
            kotlinx.coroutines.delay(slideshowDuration * 1000L)
            if (pagerState.currentPage < mediaList.size - 1) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                // Loop back to the first image
                pagerState.animateScrollToPage(0)
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

    DisposableEffect(window) {
        window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            WindowInsetsControllerCompat(win, win.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            window?.let { win ->
                WindowInsetsControllerCompat(win, win.decorView).let { controller ->
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(if (showMetadata && isLandscape) 0.6f else 1f)
                        .fillMaxHeight()
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val pageMedia = mediaList[page]
                        val pageRotation = rotationMap[page] ?: 0f
                        MediaViewerItem(
                            media = pageMedia,
                            viewModel = viewModel,
                            cookies = cookies,
                            context = context,
                            rotation = pageRotation,
                            showFaceRegions = showFaceRegions,
                            onToggleBars = {
                                showBars = !showBars
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
                TopAppBar(
                    title = { },
                    windowInsets = WindowInsets.safeDrawing,
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            showMetadata = !showMetadata
                        }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Metadata",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            viewModel.shareSingleMedia(context, currentMedia)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = {
                            downloadFile(context, mediaUrl, currentMedia.name, cookies)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = Color.White
                            )
                        }
                        
                        var showMoreMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White)
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
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
                                    text = { androidx.compose.material3.Text("Rotate") },
                                    onClick = {
                                        val page = pagerState.currentPage
                                        rotationMap[page] = ((rotationMap[page] ?: 0f) + 90f) % 360f
                                        showMoreMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.RotateRight,
                                            contentDescription = null
                                        )
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        titleContentColor = Color.White
                    )
                )
            }

            // Bottom Bar Overlay
            AnimatedVisibility(
                visible = showBars,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
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

@Composable
fun MetadataContent(media: ApiMedia, onClose: () -> Unit) {
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
                modifier = Modifier.size(24.dp)
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

        MetadataRow(icon = Icons.Outlined.InsertDriveFile, label = "Filename", value = media.name)
        media.parentPath?.let {
            MetadataRow(icon = Icons.Outlined.Folder, label = "Folder", value = it)
        }

        val size = media.metadata?.size
        if (size != null && size.width != null && size.height != null) {
            MetadataRow(icon = Icons.Outlined.AspectRatio, label = "Dimensions", value = "${size.width} × ${size.height} px")
        }

        val dateStr = formatCreationDate(media.metadata?.creationDate)
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
        
        val keywords = media.metadata?.keywords
        if (!keywords.isNullOrEmpty()) {
            MetadataBlock(icon = Icons.Outlined.Label, label = "Keywords", value = keywords.joinToString(", "))
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

@Composable
fun MediaViewerItem(
    media: ApiMedia,
    viewModel: GalleryViewModel,
    cookies: String,
    context: Context,
    rotation: Float,
    showFaceRegions: Boolean,
    onToggleBars: () -> Unit
) {
    val mediaUrl = viewModel.getOriginalMediaUrl(media)

    // Pinch to Zoom states (for images)
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Clean up temp files upon entering and exiting
    DisposableEffect(media.id) {
        onDispose {
            try {
                context.cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("temp_video_${media.id}")) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (media.isVideo) {
            var isPreparing by remember { mutableStateOf(true) }
            var isBuffering by remember { mutableStateOf(false) }
            var hasError by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val mediaController = MediaController(ctx)
                            mediaController.setAnchorView(this)
                            setMediaController(mediaController)

                            if (cookies.isNotEmpty()) {
                                setVideoURI(Uri.parse(mediaUrl), mapOf("Cookie" to cookies))
                            } else {
                                setVideoURI(Uri.parse(mediaUrl))
                            }

                            setOnPreparedListener {
                                isPreparing = false
                                start()
                            }
                            setOnInfoListener { _, what, _ ->
                                if (what == 701) { // MediaPlayer.MEDIA_INFO_BUFFERING_START
                                    isBuffering = true
                                } else if (what == 702) { // MediaPlayer.MEDIA_INFO_BUFFERING_END
                                    isBuffering = false
                                }
                                true
                            }
                            setOnCompletionListener {
                                isPreparing = false
                                isBuffering = false
                            }
                            setOnErrorListener { _, _, _ ->
                                hasError = true
                                isPreparing = false
                                isBuffering = false
                                false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Transparent tap overlay covering upper 80% of the screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.8f)
                        .align(Alignment.TopCenter)
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
            
            if ((isPreparing || isBuffering) && !hasError) {
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
            // Image View with Pinch to Zoom & dynamic Client-Side Rotation
            val builder = ImageRequest.Builder(context)
                .data(mediaUrl)
                .placeholderMemoryCacheKey(viewModel.getThumbnailUrl(media))
                .crossfade(true)

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
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onToggleBars() })
                    }
                    .pointerInput(scale) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val pointersCount = event.changes.size

                                if (pointersCount > 1 || scale > 1f) {
                                    val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
                                    scale = nextScale
                                    if (nextScale > 1f) {
                                        offsetX += panChange.x * nextScale
                                        offsetY += panChange.y * nextScale
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                    
                                    // Consume to avoid swipe interference
                                    event.changes.forEach {
                                        if (it.positionChanged()) {
                                            it.consume()
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

fun formatCreationDate(timestamp: Long?): String {
    if (timestamp == null) return "Unknown"
    val ms = if (timestamp < 10000000000L) timestamp * 1000L else timestamp
    return try {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault())
        sdf.format(java.util.Date(ms))
    } catch (e: Exception) {
        "Unknown"
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