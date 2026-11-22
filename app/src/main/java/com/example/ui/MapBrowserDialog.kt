package com.example.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.ApiMedia
import com.example.data.PiGalleryApi
import com.example.findActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MAP_CORS_HEADERS = mapOf(
    "Access-Control-Allow-Origin" to "*",
    "Cache-Control" to "public, max-age=86400"
)

private val MAP_ASSET_HEADERS = mapOf(
    "Access-Control-Allow-Origin" to "*",
    "Cache-Control" to "public, max-age=31536000"
)

private val ALLOWED_MAP_ASSETS = setOf(
    "leaflet.css",
    "MarkerCluster.css",
    "leaflet.js",
    "leaflet.markercluster.js",
    "leaflet.polylineDecorator.js"
)

private fun inferMapAssetMimeType(fileName: String): String {
    return when {
        fileName.endsWith(".css", ignoreCase = true) -> "text/css"
        fileName.endsWith(".js", ignoreCase = true) -> "application/javascript"
        fileName.endsWith(".png", ignoreCase = true) -> "image/png"
        else -> "application/octet-stream"
    }
}

private fun inferImageMimeType(bytes: ByteArray, length: Int, rawHeader: String?): String {
    val cleaned = rawHeader?.substringBefore(';')?.trim()?.lowercase()
    if (!cleaned.isNullOrBlank() && cleaned.startsWith("image/")) {
        return cleaned
    }
    if (length >= 4) {
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
            return "image/png"
        }
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) {
            return "image/gif"
        }
        if (length >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        ) {
            return "image/webp"
        }
    }
    return "image/jpeg"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapBrowserDialog(
    mediaList: List<ApiMedia>,
    viewModel: GalleryViewModel,
    onDismiss: () -> Unit,
    initialMediaId: String? = null
) {
    val context = LocalContext.current
    val geotaggedMedia = remember(mediaList) {
        mediaList.filter { it.metadata?.gps != null }
    }

    var showProviderMenu by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf(MapProvider.BASE) }
    var showTravelTrack by remember { mutableStateOf(true) }
    val initialActiveMedia = remember(geotaggedMedia, initialMediaId) {
        if (initialMediaId != null) {
            geotaggedMedia.find { it.id?.toString() == initialMediaId }
        } else {
            geotaggedMedia.firstOrNull()
        }
    }
    var topLevelActiveMedia by remember(initialActiveMedia) { mutableStateOf(initialActiveMedia) }

    var isPlaying by remember { mutableStateOf(false) }
    var isHeaderVisible by remember { mutableStateOf(true) }
    var headerResetTrigger by remember { mutableIntStateOf(1) }

    LaunchedEffect(headerResetTrigger, isPlaying, showProviderMenu) {
        if (isPlaying) {
            isHeaderVisible = false
        } else if (showProviderMenu) {
            isHeaderVisible = true
        } else {
            isHeaderVisible = true
            kotlinx.coroutines.delay(3500)
            isHeaderVisible = false
        }
    }

    var keepHeaderForMenu by remember { mutableStateOf(false) }
    val menuCoroutineScope = rememberCoroutineScope()
    LaunchedEffect(showProviderMenu) {
        if (showProviderMenu) {
            keepHeaderForMenu = true
        } else {
            delay(200)
            keepHeaderForMenu = false
        }
    }

    BackHandler {
        if (showProviderMenu) {
            showProviderMenu = false
        } else {
            onDismiss()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
                if (geotaggedMedia.isEmpty()) {
                        EmptyStateView()
                    } else {
                        MapViewContainer(
                            geotaggedMedia = geotaggedMedia,
                            mediaList = mediaList,
                            viewModel = viewModel,
                            selectedProvider = selectedProvider,
                            showTravelTrack = showTravelTrack,
                            isHeaderVisible = isHeaderVisible,
                            isPlaying = isPlaying,
                            onPlayingChange = { isPlaying = it },
                            onResetHeaderTimer = { headerResetTrigger++ },
                            onDismiss = onDismiss,
                            onActiveMediaChange = { topLevelActiveMedia = it },
                            initialMediaId = initialMediaId
                        )
                        
                        // Floating UI
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isHeaderVisible || keepHeaderForMenu,
                            enter = androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.fadeOut(),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Box(modifier = Modifier.statusBarsPadding().padding(16.dp)) {
                                IconButton(
                                    onClick = { 
                                        showProviderMenu = true 
                                        headerResetTrigger++
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.map_menu),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = showProviderMenu,
                                    onDismissRequest = { showProviderMenu = false },
                                    modifier = Modifier.width(220.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.travel_track)) },
                                        onClick = {
                                            showTravelTrack = !showTravelTrack
                                            headerResetTrigger++
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Route,
                                                tint = if (showTravelTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                contentDescription = null
                                            )
                                        },
                                        trailingIcon = {
                                            Switch(
                                                checked = showTravelTrack,
                                                onCheckedChange = null
                                            )
                                        }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Layers,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.map_style),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    MapProvider.values().forEach { provider ->
                                        val isSelected = selectedProvider == provider
                                        val providerLabel = stringResource(provider.labelResId)
                                        DropdownMenuItem(
                                            onClick = {
                                                if (showProviderMenu) {
                                                    showProviderMenu = false
                                                    headerResetTrigger++
                                                    menuCoroutineScope.launch {
                                                        delay(180)
                                                        selectedProvider = provider
                                                    }
                                                }
                                            },
                                            modifier = Modifier.then(
                                                if (isSelected) {
                                                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                                } else Modifier
                                            ),
                                            text = {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(width = 140.dp, height = 90.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                                            .then(
                                                                if (isSelected) {
                                                                    Modifier.border(
                                                                        width = 3.dp,
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                        shape = RoundedCornerShape(8.dp)
                                                                    )
                                                                } else Modifier
                                                            )
                                                    ) {
                                                        AsyncImage(
                                                            model = provider.previewUrl,
                                                            contentDescription = stringResource(R.string.map_preview_cd, providerLabel),
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                        )
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    
                                                    Text(
                                                        text = providerLabel,
                                                        style = if (isSelected) {
                                                            MaterialTheme.typography.labelLarge.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        } else {
                                                            MaterialTheme.typography.bodyMedium
                                                        },
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

@Composable
private fun EmptyStateView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.LocationOff,
            contentDescription = stringResource(R.string.no_location_data),
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_geotagged_media),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_geotagged_media_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MapViewContainer(
    geotaggedMedia: List<ApiMedia>,
    mediaList: List<ApiMedia>,
    viewModel: GalleryViewModel,
    selectedProvider: MapProvider,
    showTravelTrack: Boolean,
    isHeaderVisible: Boolean,
    isPlaying: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    onResetHeaderTimer: () -> Unit,
    onDismiss: () -> Unit,
    onActiveMediaChange: (ApiMedia?) -> Unit,
    initialMediaId: String? = null
) {
    val context = LocalContext.current
    val unknownLabel = stringResource(R.string.unknown)
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapLoading by remember { mutableStateOf(true) }
    var isMapReady by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(isMapLoading, reloadTrigger) {
        if (isMapLoading) {
            delay(15_000L)
            if (isMapLoading) {
                isMapLoading = false
                if (mapError == null) {
                    mapError = context.getString(R.string.map_load_error)
                }
            }
        }
    }

    // Live-switch tile layers dynamically without reloading the entire WebView state
    LaunchedEffect(selectedProvider) {
        webViewRef?.evaluateJavascript(
            "if (typeof changeTileLayer !== 'undefined') changeTileLayer('${selectedProvider.url}', '${selectedProvider.attribution.replace("'", "\\'")}')",
            null
        )
    }

    LaunchedEffect(showTravelTrack, webViewRef) {
        webViewRef?.evaluateJavascript(
            "if (typeof toggleTravelTrack !== 'undefined') toggleTravelTrack(${showTravelTrack})",
            null
        )
    }

    val lastLoadedHash = remember { IntArray(1) }
    val lastReloadTrigger = remember { IntArray(1) }

    val activeServerUrl = viewModel.getActiveServerUrl()
    val thumbSuffix = viewModel.prefs.thumbnailPathSuffix
    val thumbCacheKeyLookup by produceState<Map<String, String>>(
        initialValue = emptyMap(),
        key1 = geotaggedMedia,
        key2 = activeServerUrl,
        key3 = thumbSuffix
    ) {
        value = withContext(Dispatchers.Default) {
            val map = HashMap<String, String>(geotaggedMedia.size * 2)
            for (media in geotaggedMedia) {
                val fullThumbUrl = PiGalleryApi.ensureHttpUrl(viewModel.getThumbnailUrl(media))
                val cacheKey = "thumb_${media.id}_${media.name}"
                if (fullThumbUrl.isNotBlank()) {
                    map[fullThumbUrl] = cacheKey
                    fullThumbUrl.toUri().encodedPath?.let { path ->
                        map[path] = cacheKey
                    }
                }
            }
            map
        }
    }

    val currentThumbLookup by rememberUpdatedState(thumbCacheKeyLookup)

    val markersJson by produceState<String?>(
        initialValue = null,
        key1 = geotaggedMedia,
        key2 = activeServerUrl,
        key3 = thumbSuffix
    ) {
        value = withContext(Dispatchers.Default) {
            val arr = org.json.JSONArray()
            for (media in geotaggedMedia) {
                val gps = media.metadata?.gps ?: continue
                val id = media.id?.toString() ?: ""
                val fullThumbUrl = PiGalleryApi.ensureHttpUrl(viewModel.getThumbnailUrl(media))
                val entry = org.json.JSONArray().apply {
                    put(gps.latitude)
                    put(gps.longitude)
                    put(id)
                    put(fullThumbUrl)
                }
                arr.put(entry)
            }
            arr.toString()
        }
    }

    val initialCenter = remember(geotaggedMedia, initialMediaId) {
        val matchingMedia = if (initialMediaId != null) {
            geotaggedMedia.find { it.id?.toString() == initialMediaId }
        } else {
            null
        }
        val startGps = matchingMedia?.metadata?.gps ?: geotaggedMedia.firstOrNull()?.metadata?.gps
        if (startGps != null) "[${startGps.latitude}, ${startGps.longitude}]" else "[0, 0]"
    }

    // Prepare lightweight static HTML container for Leaflet Map (markers injected via bulk JSON)
    val htmlContent = remember(initialCenter) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="__map_assets__/leaflet.css" />
            <link rel="stylesheet" href="__map_assets__/MarkerCluster.css" />
            <script src="__map_assets__/leaflet.js"></script>
            <script src="__map_assets__/leaflet.markercluster.js"></script>
            <script src="__map_assets__/leaflet.polylineDecorator.js"></script>
            <style>
                body { padding: 0; margin: 0; background: #121212; color: #ffffff; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
                html, body, #map { height: 100%; width: 100%; }
                .leaflet-container { background: #121212; }
                .leaflet-bar { border: none !important; box-shadow: 0 4px 10px rgba(0,0,0,0.4) !important; }
                .leaflet-bar a { background-color: #2c2c2c !important; color: #ffffff !important; border-bottom: 1px solid #3c3c3c !important; }
                .leaflet-bar a:hover { background-color: #3c3c3c !important; }
                .custom-pin-icon { background: none !important; border: none !important; }
                .custom-thumb-icon { background: transparent !important; border: none !important; }
                .custom-thumb-icon.highlighted { background: transparent !important; border: none !important; z-index: 1000 !important; }

                /* Custom Premium Dark Theme Marker Cluster Styles */
                .marker-cluster-custom {
                    background: rgba(33, 150, 243, 0.22);
                    border-radius: 50%;
                    overflow: visible !important;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    transition: all 0.2s ease-in-out;
                    border: none !important;
                }
                .marker-cluster-custom:hover {
                    background: rgba(33, 150, 243, 0.35);
                    transform: scale(1.08);
                }
                .marker-cluster-custom-inner {
                    width: 36px;
                    height: 36px;
                    background: #2196F3;
                    border: 2px solid #ffffff;
                    border-radius: 50%;
                    color: #ffffff;
                    font-weight: bold;
                    font-size: 12px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    box-shadow: 0 4px 12px rgba(33, 150, 243, 0.6);
                }
                
                /* Medium size cluster (10-99 items) */
                .marker-cluster-medium-custom {
                    background: rgba(0, 188, 212, 0.22);
                }
                .marker-cluster-medium-custom .marker-cluster-custom-inner {
                    background: #00BCD4;
                    box-shadow: 0 4px 12px rgba(0, 188, 212, 0.6);
                }
                
                /* Large size cluster (100+ items) */
                .marker-cluster-large-custom {
                    background: rgba(156, 39, 176, 0.22);
                }
                .marker-cluster-large-custom .marker-cluster-custom-inner {
                    background: #9C27B0;
                    box-shadow: 0 4px 12px rgba(156, 39, 176, 0.6);
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                // Built-in Leaflet zoom controls disabled for polished native Compose UI buttons
                var map = L.map('map', {
                    zoomControl: false,
                    maxZoom: 18,
                    minZoom: 3,
                    zoomAnimation: true,
                    fadeAnimation: true,
                    markerZoomAnimation: true
                }).setView(${initialCenter}, 10);

                var currentTileLayer = L.tileLayer('${selectedProvider.url}', {
                    attribution: '${selectedProvider.attribution.replace("'", "\\'")}',
                    subdomains: 'abcd',
                    maxZoom: 20,
                    keepBuffer: 16,
                    updateWhenZooming: true,
                    updateWhenIdle: false
                }).addTo(map);

                function changeTileLayer(url, attribution) {
                    try {
                        if (currentTileLayer) {
                            map.removeLayer(currentTileLayer);
                        }
                        currentTileLayer = L.tileLayer(url, {
                            attribution: attribution,
                            subdomains: 'abcd',
                            maxZoom: 20,
                            keepBuffer: 16,
                            updateWhenZooming: true,
                            updateWhenIdle: false
                        }).addTo(map);
                    } catch (changeErr) {
                        console.error('Error changing tile layer:', changeErr);
                    }
                }

                function onMarkerClick(mediaId) {
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onMediaSelected(mediaId);
                    }
                }

                // Register map click listener to show/reset header bar
                map.on('click', function() {
                    if (window.AndroidBridge && window.AndroidBridge.onMapClick) {
                        window.AndroidBridge.onMapClick();
                    }
                });

                // Initialize Marker Cluster Group with custom layout, responsive sizing, and cluster thumbnails
                var markersGroup = L.markerClusterGroup({
                    showCoverageOnHover: false,
                    zoomToBoundsOnClick: true,
                    spiderfyOnMaxZoom: true,
                    maxClusterRadius: 50,
                    iconCreateFunction: function(cluster) {
                        var count = cluster.getChildCount();
                        var children = cluster.getAllChildMarkers();
                        var thumbUrl = null;
                        if (children && children.length > 0) {
                            for (var i = 0; i < children.length; i++) {
                                if (children[i] && children[i].thumbUrl) {
                                    thumbUrl = children[i].thumbUrl;
                                    break;
                                }
                            }
                        }

                        var mainClass = 'marker-cluster-custom';
                        var clusterColor = '#2196F3';
                        if (count >= 100) {
                            mainClass += ' marker-cluster-large-custom';
                            clusterColor = '#9C27B0';
                        } else if (count >= 10) {
                            mainClass += ' marker-cluster-medium-custom';
                            clusterColor = '#00BCD4';
                        }

                        var innerHtml = '';
                        if (thumbUrl) {
                            var safeThumb = escapeHtmlAttr(thumbUrl);
                            innerHtml = '<div style="position:relative; width:52px; height:52px; display:flex; align-items:center; justify-content:center; overflow:visible;">' +
                                '<div style="width:42px; height:42px; border-radius:50%; overflow:hidden; border:2px solid #ffffff; box-shadow:0 2px 8px rgba(0,0,0,0.6); background:' + clusterColor + '; position:relative; display:flex; align-items:center; justify-content:center; box-sizing:border-box;">' +
                                '<span style="position:relative; z-index:0; color:#ffffff; font-weight:700; font-size:13px; line-height:1;">' + count + '</span>' +
                                '<img src="' + safeThumb + '" style="position:absolute; top:0; left:0; width:100%; height:100%; object-fit:cover; display:block; z-index:1;" onerror="this.style.display=\'none\'; var b=this.parentElement&&this.parentElement.nextElementSibling; if(b) b.style.display=\'none\';" />' +
                                '</div>' +
                                '<div style="position:absolute; top:0; right:0; z-index:2; min-width:20px; height:20px; padding:0 5px; border-radius:10px; background:' + clusterColor + '; color:#ffffff; font-weight:700; font-size:11px; line-height:1; display:inline-flex; align-items:center; justify-content:center; border:2px solid #ffffff; box-shadow:0 2px 6px rgba(0,0,0,0.65); box-sizing:border-box;">' + count + '</div>' +
                                '</div>';
                        } else {
                            innerHtml = '<div class="marker-cluster-custom-inner"><span>' + count + '</span></div>';
                        }

                        return L.divIcon({
                            html: innerHtml,
                            className: mainClass,
                            iconSize: L.point(52, 52),
                            iconAnchor: L.point(26, 26)
                        });
                    }
                });

                var markers = [];
                var markersById = {};

                function escapeHtmlAttr(str) {
                    return String(str)
                        .replace(/&/g, '&amp;')
                        .replace(/"/g, '&quot;')
                        .replace(/'/g, '&#39;')
                        .replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;');
                }

                function createMarkerHtml(thumbUrl, isHighlighted) {
                    var sz = isHighlighted ? 52 : 40;
                    var borderW = isHighlighted ? 3 : 2;
                    var borderC = isHighlighted ? '#FFC107' : '#ffffff';
                    var shadow = isHighlighted ? '0 0 14px rgba(255, 193, 7, 0.9)' : '0 2px 6px rgba(0,0,0,0.6)';
                    var safeUrl = escapeHtmlAttr(thumbUrl);
                    return '<div style="width:' + sz + 'px; height:' + sz + 'px; border-radius:50%; overflow:hidden; border:' + borderW + 'px solid ' + borderC + '; box-shadow:' + shadow + '; background-color:#2196F3; box-sizing:border-box; display:flex; align-items:center; justify-content:center; position:relative;">' +
                        '<svg style="width:20px; height:20px; fill:#ffffff; position:absolute; z-index:0;" viewBox="0 0 24 24"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/></svg>' +
                        '<img src="' + safeUrl + '" style="position:absolute; top:0; left:0; width:100%; height:100%; object-fit:cover; display:block; border-radius:50%; z-index:1;" onerror="this.style.display=\'none\';" />' +
                        '</div>';
                }

                function setMarkersData(dataArray) {
                    if (!Array.isArray(dataArray)) return;
                    markersGroup.clearLayers();
                    markers = [];
                    markersById = {};
                    activeHighlightedMarker = null;

                    var batch = [];
                    for (var i = 0; i < dataArray.length; i++) {
                        var item = dataArray[i];
                        var lat = item[0];
                        var lon = item[1];
                        var id = String(item[2] || '');
                        var thumbUrl = String(item[3] || '');
                        var thumbIcon = L.divIcon({
                            html: createMarkerHtml(thumbUrl, false),
                            iconSize: [40, 40],
                            iconAnchor: [20, 20],
                            popupAnchor: [0, -20],
                            className: 'custom-thumb-icon'
                        });

                        var marker = L.marker([lat, lon], {icon: thumbIcon});
                        marker.thumbUrl = thumbUrl;
                        (function(markerId) {
                            marker.on('click', function() {
                                onMarkerClick(markerId);
                            });
                        })(id);

                        markers.push(marker);
                        markersById[id] = marker;
                        batch.push(marker);
                    }
                    if (batch.length > 0) {
                        markersGroup.addLayers(batch);
                    }
                    fitBounds();
                }

                var activeHighlightedMarker = null;
                function highlightMarker(id, durationSeconds) {
                    var baseDur = durationSeconds ? (durationSeconds * 0.8) : 4.0;
                    
                    // Reset previous highlighted marker icon and zIndex
                    if (activeHighlightedMarker) {
                        activeHighlightedMarker.setZIndexOffset(0);
                        if (activeHighlightedMarker.thumbUrl) {
                            var normalIcon = L.divIcon({
                                html: createMarkerHtml(activeHighlightedMarker.thumbUrl, false),
                                iconSize: [40, 40],
                                iconAnchor: [20, 20],
                                popupAnchor: [0, -20],
                                className: 'custom-thumb-icon'
                            });
                            activeHighlightedMarker.setIcon(normalIcon);
                        }
                    }

                    var marker = markersById[id];
                    if (marker) {
                        activeHighlightedMarker = marker;
                        marker.setZIndexOffset(1000);
                        if (marker.thumbUrl) {
                            var highlightedIcon = L.divIcon({
                                html: createMarkerHtml(marker.thumbUrl, true),
                                iconSize: [52, 52],
                                iconAnchor: [26, 26],
                                popupAnchor: [0, -26],
                                className: 'custom-thumb-icon highlighted'
                            });
                            marker.setIcon(highlightedIcon);
                        }

                        var latLng = marker.getLatLng();
                        var currentCenter = map.getCenter();
                        var dist = map.distance(currentCenter, latLng);

                        var proceedCalled = false;
                        function proceed() {
                            if (proceedCalled) return;
                            proceedCalled = true;
                            // If we are very close, use panTo for a "stable" sliding flight without zoom bouncing
                            if (dist < 2000) {
                                map.panTo(latLng, { animate: true, duration: baseDur });
                                setTimeout(function() {
                                    if (window.AndroidBridge && window.AndroidBridge.onFlyToComplete) {
                                        window.AndroidBridge.onFlyToComplete(id);
                                    }
                                }, baseDur * 1000);
                            } else {
                                // For long distances, use flyTo but scale duration by distance so map tiles have time to load
                                var calculatedDur = Math.max(baseDur, Math.min(dist / 400000, 8.0));
                                map.flyTo(latLng, targetZoom, {
                                    animate: true,
                                    duration: calculatedDur
                                });
                                
                                var completed = false;
                                function onComplete() {
                                    if (completed) return;
                                    completed = true;
                                    if (window.AndroidBridge && window.AndroidBridge.onFlyToComplete) {
                                        window.AndroidBridge.onFlyToComplete(id);
                                    }
                                }
                                map.once('moveend', onComplete);
                                // Safety timeout
                                setTimeout(onComplete, (calculatedDur + 1.0) * 1000);
                            }
                        }

                        if (map.hasLayer(marker)) {
                            proceed();
                        } else {
                            markersGroup.zoomToShowLayer(marker, function() {
                                // The marker is now visible (zoomed or spiderfied).
                                // We can gently pan to it, but we shouldn't change the zoom level
                                // as it might un-spiderfy the cluster.
                                map.panTo(latLng, { animate: true, duration: 0.5 });
                                setTimeout(function() {
                                    if (window.AndroidBridge && window.AndroidBridge.onFlyToComplete) {
                                        window.AndroidBridge.onFlyToComplete(id);
                                    }
                                }, 500);
                            });
                        }
                    } else {
                        if (window.AndroidBridge && window.AndroidBridge.onFlyToComplete) {
                            window.AndroidBridge.onFlyToComplete(id);
                        }
                    }
                }

                function fitBounds(force) {
                    if (activeHighlightedMarker && !force) {
                        return; // do not override the highlighted marker camera focus
                    }
                    if (markers.length > 0) {
                        var group = new L.featureGroup(markers);
                        map.fitBounds(group.getBounds().pad(0.15));
                    }
                }

                var travelTrackLayer = null;
                var travelTrackDecorator = null;
                function toggleTravelTrack(show) {
                    if (travelTrackLayer) {
                        map.removeLayer(travelTrackLayer);
                        travelTrackLayer = null;
                    }
                    if (travelTrackDecorator) {
                        map.removeLayer(travelTrackDecorator);
                        travelTrackDecorator = null;
                    }
                    if (show && markers.length > 1) {
                        var latlngs = markers.map(function(m) { return m.getLatLng(); });
                        travelTrackLayer = L.polyline(latlngs, {
                            color: '#2196F3',
                            weight: 3,
                            opacity: 0.8,
                            noClip: true,
                            smoothFactor: 1
                        }).addTo(map);
                        
                        // Add directional arrows
                        travelTrackDecorator = L.polylineDecorator(travelTrackLayer, {
                            patterns: [
                                {offset: 25, repeat: 100, symbol: L.Symbol.arrowHead({pixelSize: 15, pathOptions: {fillOpacity: 1, weight: 0, color: '#2196F3'}})}
                            ]
                        }).addTo(map);
                    }
                }

                map.addLayer(markersGroup);

                // Recalculate container size and fit bounds on resize, window load and layout shifts
                window.addEventListener('resize', function() {
                    map.invalidateSize();
                });

                window.addEventListener('load', function() {
                    setTimeout(function() {
                        map.invalidateSize();
                        fitBounds();
                    }, 200);
                });

                // Safety timeouts for Compose dialog layout shifts
                setTimeout(function() { map.invalidateSize(); fitBounds(); }, 50);
                setTimeout(function() { map.invalidateSize(); fitBounds(); }, 500);
                setTimeout(function() { map.invalidateSize(); fitBounds(); }, 1500);
                setTimeout(function() { map.invalidateSize(); fitBounds(); }, 3000);
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    val sortedMedia = remember(geotaggedMedia) { 
        geotaggedMedia.sortedBy { DateUtils.getLocalTimeMs(it.metadata?.creationDate, it.metadata?.creationDateOffset) } 
    }
    var activeMediaId by remember(sortedMedia, initialMediaId) { mutableStateOf(initialMediaId ?: sortedMedia.firstOrNull()?.id?.toString()) }
    val currentActiveMediaId by rememberUpdatedState(activeMediaId)
    val initialIndex = remember(sortedMedia, initialMediaId) {
        val idx = sortedMedia.indexOfFirst { it.id?.toString() == initialMediaId }
        if (idx == -1) 0 else idx
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val mapSlideDuration = viewModel.prefs.slideshowDuration
    val flightCompleteChannel = remember { Channel<String?>(Channel.CONFLATED) }
    val scope = rememberCoroutineScope()

    // Keep screen on during active track playback
    val view = androidx.compose.ui.platform.LocalView.current
    val window = remember(view) { context.findActivity()?.window }

    DisposableEffect(isPlaying, window) {
        if (isPlaying) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(window) {
        window?.let { win ->
            androidx.core.view.WindowInsetsControllerCompat(win, win.decorView).let { controller ->
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    LaunchedEffect(isMapReady, markersJson, webViewRef) {
        val json = markersJson
        val wv = webViewRef
        if (isMapReady && json != null && wv != null) {
            wv.evaluateJavascript("if (typeof setMarkersData !== 'undefined') { setMarkersData($json); }", null)
            wv.evaluateJavascript("if (typeof toggleTravelTrack !== 'undefined') { toggleTravelTrack(${showTravelTrack}); }", null)
            currentActiveMediaId?.let { mediaId ->
                val quotedId = org.json.JSONObject.quote(mediaId)
                wv.evaluateJavascript("if (typeof highlightMarker !== 'undefined') { highlightMarker($quotedId, 1); }", null)
            }
        }
    }

    LaunchedEffect(activeMediaId) {
        if (activeMediaId != null) {
            val duration = if (isPlaying) mapSlideDuration else 1
            webViewRef?.evaluateJavascript("highlightMarker('$activeMediaId', $duration)", null)
            val index = sortedMedia.indexOfFirst { it.id?.toString() == activeMediaId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
            val media = sortedMedia.find { it.id?.toString() == activeMediaId }
            onActiveMediaChange(media)
        } else {
            onActiveMediaChange(null)
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var currentIndex = sortedMedia.indexOfFirst { it.id?.toString() == activeMediaId }
            if (currentIndex == -1 || currentIndex == sortedMedia.size - 1) {
                currentIndex = 0
            }
            
            // clear any pending signals
            flightCompleteChannel.tryReceive()
            
            while (isPlaying && currentIndex < sortedMedia.size) {
                val targetId = sortedMedia[currentIndex].id?.toString()
                if (activeMediaId == targetId) {
                    // Force trigger highlight and scroll if activeMediaId matches current targetId (prevents Jetpack Compose state deduplication hang)
                    if (targetId != null) {
                        val duration = mapSlideDuration
                        webViewRef?.evaluateJavascript("highlightMarker('$targetId', $duration)", null)
                        val index = sortedMedia.indexOfFirst { it.id?.toString() == targetId }
                        if (index >= 0) {
                            listState.animateScrollToItem(index)
                        }
                    }
                } else {
                    activeMediaId = targetId
                }
                
                withTimeoutOrNull(25000L) {
                    flightCompleteChannel.receive()
                }
                
                delay((mapSlideDuration * 0.2 * 1000L).toLong().coerceAtLeast(100L))
                currentIndex++
                if (currentIndex >= sortedMedia.size) {
                    onPlayingChange(false)
                }
            }
        }
    }

    val activeMedia = remember(activeMediaId, sortedMedia) {
        sortedMedia.find { it.id?.toString() == activeMediaId }
    }

    var isMapInteractionEnabled by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Map Section taking exactly 60% of the screen height when activeMedia is present
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (activeMedia != null) 0.6f else 1.0f)
        ) {
            var isMapFocused by remember { mutableStateOf(false) }
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { 
                        isMapFocused = it.isFocused 
                        if (!it.isFocused) {
                            isMapInteractionEnabled = false // disable interaction when focus lost
                        }
                    }
                    .focusable()
                    .border(
                        width = if (isMapFocused) 4.dp else 0.dp,
                        color = if (isMapInteractionEnabled) MaterialTheme.colorScheme.secondary else if (isMapFocused) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                    ),
                factory = { ctx ->
                    val ensureCacheDirs = { context: android.content.Context ->
                        try {
                            val jsDir = java.io.File(context.cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
                            if (!jsDir.exists()) jsDir.mkdirs()
                            val wasmDir = java.io.File(context.cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
                            if (!wasmDir.exists()) wasmDir.mkdirs()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    ensureCacheDirs(ctx)
                    WebView(ctx).apply {
                        webViewRef = this
                        isFocusable = true
                        isFocusableInTouchMode = true
                        setOnKeyListener { _, keyCode, event ->
                            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                                if (isMapInteractionEnabled) {
                                    when (keyCode) {
                                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                            evaluateJavascript("if (typeof map !== 'undefined') map.panBy([0, -100]);", null)
                                            true
                                        }
                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            evaluateJavascript("if (typeof map !== 'undefined') map.panBy([0, 100]);", null)
                                            true
                                        }
                                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            evaluateJavascript("if (typeof map !== 'undefined') map.panBy([-100, 0]);", null)
                                            true
                                        }
                                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            evaluateJavascript("if (typeof map !== 'undefined') map.panBy([100, 0]);", null)
                                            true
                                        }
                                        android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                                            isMapInteractionEnabled = false
                                            true
                                        }
                                        android.view.KeyEvent.KEYCODE_BACK -> {
                                            isMapInteractionEnabled = false
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    when (keyCode) {
                                        android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                                            isMapInteractionEnabled = true
                                            true
                                        }
                                        else -> false
                                    }
                                }
                            } else {
                                false
                            }
                        }
                        setBackgroundColor("#121212".toColorInt())
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // Periodically reinforce folder creation to ensure it exists during asynchronous engine initialization
                        postDelayed({ ensureCacheDirs(ctx) }, 500)
                        postDelayed({ ensureCacheDirs(ctx) }, 2000)
                        postDelayed({ ensureCacheDirs(ctx) }, 5000)

                        webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: android.webkit.RenderProcessGoneDetail?
                            ): Boolean {
                                val crashed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    detail?.didCrash()
                                } else {
                                    null
                                }
                                android.util.Log.e("MapBrowserWebView", "WebView render process gone (didCrash: $crashed). Handled safely.")
                                view?.let { webView ->
                                    val parent = webView.parent as? android.view.ViewGroup
                                    parent?.removeView(webView)
                                    webView.destroy()
                                }
                                return true // Return true so main app process does not crash
                            }

                            @Suppress("WebViewClientOnReceivedSslError")
                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: android.webkit.SslErrorHandler?,
                                error: android.net.http.SslError?
                            ) {
                                if (viewModel.prefs.allowInsecureSsl) {
                                    handler?.proceed()
                                } else {
                                    handler?.cancel()
                                }
                            }

                            @OptIn(coil.annotation.ExperimentalCoilApi::class)
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: android.webkit.WebResourceRequest
                            ): android.webkit.WebResourceResponse? {
                                val url = request.url.toString()
                                val requestPath = request.url.encodedPath

                                // 1. Serve bundled local Leaflet & cluster assets directly from APK assets
                                if (url.contains("__map_assets__/")) {
                                    val assetName = url.substringAfter("__map_assets__/").substringBefore('?').trimStart('/')
                                    if (assetName in ALLOWED_MAP_ASSETS) {
                                        return try {
                                            val stream = context.assets.open("map/$assetName")
                                            android.webkit.WebResourceResponse(
                                                inferMapAssetMimeType(assetName),
                                                "UTF-8",
                                                200,
                                                "OK",
                                                MAP_ASSET_HEADERS,
                                                stream
                                            )
                                        } catch (e: Exception) {
                                            android.util.Log.e("MapBrowserWebView", "Missing local map asset: $assetName", e)
                                            null
                                        }
                                    }
                                    return android.webkit.WebResourceResponse(
                                        inferMapAssetMimeType(assetName),
                                        null,
                                        200,
                                        "OK",
                                        MAP_ASSET_HEADERS,
                                        java.io.ByteArrayInputStream(ByteArray(0))
                                    )
                                }

                                val lookup = currentThumbLookup
                                val cacheKey = lookup[url] ?: requestPath?.let { lookup[it] }
                                if (cacheKey != null || url.contains("/gallery/content/")) {
                                    val diskCache = coil.Coil.imageLoader(context).diskCache

                                    // 2. Try serving instantaneously from Coil's disk cache if already loaded
                                    try {
                                        val snapshot = (cacheKey?.let { diskCache?.openSnapshot(it) }) ?: diskCache?.openSnapshot(url)
                                        snapshot?.use { snap ->
                                            val cachedBytes = snap.data.toFile().readBytes()
                                            if (cachedBytes.isNotEmpty()) {
                                                val mime = inferImageMimeType(cachedBytes, cachedBytes.size, null)
                                                return android.webkit.WebResourceResponse(
                                                    mime,
                                                    null,
                                                    200,
                                                    "OK",
                                                    MAP_CORS_HEADERS,
                                                    java.io.ByteArrayInputStream(cachedBytes)
                                                )
                                            }
                                        }
                                    } catch (_: Exception) {
                                        // Fall through to network fetch
                                    }

                                    // 3. Fetch via PiGalleryApi OkHttpClient and persist to Coil's disk cache
                                    try {
                                        val cookies = viewModel.prefs.cookies
                                        val builder = okhttp3.Request.Builder()
                                            .url(url)
                                            .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                                        if (cookies.isNotBlank()) {
                                            builder.header("Cookie", cookies)
                                        }
                                        viewModel.api.client.newCall(builder.build()).execute().use { response ->
                                            if (response.isSuccessful) {
                                                val bodyBytes = response.body?.bytes()
                                                if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                                                    val targetCacheKey = cacheKey ?: url
                                                    try {
                                                        diskCache?.openEditor(targetCacheKey)?.let { editor ->
                                                            try {
                                                                editor.data.toFile().writeBytes(bodyBytes)
                                                                editor.commit()
                                                            } catch (_: Exception) {
                                                                editor.abort()
                                                            }
                                                        }
                                                    } catch (_: Exception) {
                                                    }
                                                    val mimeType = inferImageMimeType(bodyBytes, bodyBytes.size, response.header("Content-Type"))
                                                    return android.webkit.WebResourceResponse(
                                                        mimeType,
                                                        null,
                                                        200,
                                                        "OK",
                                                        MAP_CORS_HEADERS,
                                                        java.io.ByteArrayInputStream(bodyBytes)
                                                    )
                                                }
                                            }
                                            return android.webkit.WebResourceResponse(
                                                "text/plain",
                                                "UTF-8",
                                                response.code.coerceAtLeast(400),
                                                response.message.ifBlank { "Error" },
                                                MAP_CORS_HEADERS,
                                                java.io.ByteArrayInputStream(ByteArray(0))
                                            )
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("MapBrowserWebView", "Failed to load thumbnail: $url", e)
                                        return android.webkit.WebResourceResponse(
                                            "text/plain",
                                            "UTF-8",
                                            500,
                                            "Error",
                                            MAP_CORS_HEADERS,
                                            java.io.ByteArrayInputStream(ByteArray(0))
                                        )
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                return false // Handle internally
                            }
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isMapReady = false
                                isMapLoading = true
                                mapError = null
                                view?.context?.let { ensureCacheDirs(it) }
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript("(typeof L !== 'undefined' && typeof L.markerClusterGroup !== 'undefined').toString()") { jsResult ->
                                    val loadedOk = jsResult?.contains("true") == true
                                    isMapLoading = false
                                    if (!loadedOk) {
                                        isMapReady = false
                                        mapError = context.getString(R.string.map_load_error)
                                    } else {
                                        mapError = null
                                        isMapReady = true
                                    }
                                }
                                view?.context?.let { ensureCacheDirs(it) }
                            }
                            override fun onReceivedError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    isMapLoading = false
                                    mapError = context.getString(R.string.map_load_error)
                                }
                            }
                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                val reqUrl = request?.url?.toString() ?: ""
                                val statusCode = errorResponse?.statusCode ?: 0
                                if ((request?.isForMainFrame == true || reqUrl.contains("leaflet.js") || reqUrl.contains("leaflet.markercluster")) && statusCode >= 400) {
                                    isMapLoading = false
                                    mapError = context.getString(R.string.map_load_error)
                                }
                            }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                android.util.Log.d("MapBrowserWebView", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                                return true
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        
                        // Crucial: Set standard browser User-Agent so tile CDNs (Carto/OSM) don't block requests
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                        
                        // Setup javascript bridge
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onMediaSelected(mediaId: String) {
                                scope.launch {
                                    activeMediaId = mediaId
                                    onResetHeaderTimer()
                                }
                            }

                            @JavascriptInterface
                            fun onMapClick() {
                                scope.launch {
                                    onResetHeaderTimer()
                                }
                            }
                            
                            @JavascriptInterface
                            fun onFlyToComplete(mediaId: String?) {
                                flightCompleteChannel.trySend(mediaId)
                                scope.launch {
                                    mediaId?.let { id ->
                                        val activeMedia = sortedMedia.find { it.id?.toString() == id }
                                        onActiveMediaChange(activeMedia)
                                    }
                                }
                            }
                        }, "AndroidBridge")
                        
                        webViewRef = this
                    }
                },
                update = { webView ->
                    // Inject Cookies for authorized image loading
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, true)

                    val primaryUrl = viewModel.prefs.serverUrl.trim().let {
                        if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it
                    }.trimEnd('/')
                    val resolvedUrl = viewModel.getActiveServerUrl().trim().let {
                        if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it
                    }.trimEnd('/')

                    val rawCookies = viewModel.prefs.cookies
                    if (rawCookies.isNotBlank()) {
                        val cookieParts = rawCookies.split(";").map { it.trim() }.filter { it.contains("=") }
                        for (targetUrl in listOf(resolvedUrl, primaryUrl).distinct().filter { it.isNotBlank() }) {
                            for (part in cookieParts) {
                                cookieManager.setCookie(targetUrl, part)
                            }
                        }
                        cookieManager.flush()
                    }

                    val baseUrl = when {
                        resolvedUrl.isNotBlank() -> "$resolvedUrl/"
                        primaryUrl.isNotBlank() -> "$primaryUrl/"
                        else -> "https://localhost/"
                    }

                    // Only load once to prevent flickering on recomposition, 
                    // unless content or manual reload trigger changed.
                    if (lastLoadedHash[0] != htmlContent.hashCode() || lastReloadTrigger[0] != reloadTrigger) {
                        webView.loadDataWithBaseURL(
                            baseUrl,
                            htmlContent,
                            "text/html",
                            "UTF-8",
                            null
                        )
                        lastLoadedHash[0] = htmlContent.hashCode()
                        lastReloadTrigger[0] = reloadTrigger
                    }
                }
            )

            if (isMapLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = stringResource(R.string.loading_map), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            mapError?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = err, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            mapError = null
                            isMapLoading = true
                            reloadTrigger++
                        }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            // Floating Close button
            androidx.compose.animation.AnimatedVisibility(
                visible = isHeaderVisible,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close_map),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Floating Controls Column (Zoom +, Zoom -, and Re-center)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Zoom In
                SmallFloatingActionButton(
                    onClick = {
                        webViewRef?.evaluateJavascript("map.zoomIn()", null)
                        onResetHeaderTimer()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.zoom_in)
                    )
                }

                // Zoom Out
                SmallFloatingActionButton(
                    onClick = {
                        webViewRef?.evaluateJavascript("map.zoomOut()", null)
                        onResetHeaderTimer()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.zoom_out)
                    )
                }

                // Center Map
                SmallFloatingActionButton(
                    onClick = {
                        if (activeMediaId != null) {
                            webViewRef?.evaluateJavascript("highlightMarker('$activeMediaId', 1.0)", null)
                        } else {
                            webViewRef?.evaluateJavascript("fitBounds()", null)
                        }
                        onResetHeaderTimer()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomInMap,
                        contentDescription = stringResource(R.string.center_map)
                    )
                }
            }

            // Map interaction overlay for TV
            androidx.compose.animation.AnimatedVisibility(
                visible = isMapFocused,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isMapInteractionEnabled) stringResource(R.string.tv_map_pan_hint) else stringResource(R.string.tv_map_enter_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Active Photo Preview Panel (instead of popup, place photo above timeline, name and timestamp under previewimage)
        if (activeMedia != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .clickable {
                        // Click to view the full screen media viewer
                        viewModel.selectMedia(activeMedia, mediaList)
                    }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val cookies = viewModel.prefs.cookies
                val thumbUrl = remember(activeMedia.id, activeMedia.parentPath, activeMedia.name, activeServerUrl, thumbSuffix) {
                    viewModel.getThumbnailUrl(activeMedia).let {
                        if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it
                    }
                }
                val cacheKey = "thumb_${activeMedia.id}_${activeMedia.name}"
                val imgRequest = remember(thumbUrl, cookies, cacheKey) {
                    ImageRequest.Builder(context)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .data(thumbUrl)
                        .apply {
                            if (cookies.isNotBlank()) {
                                setHeader("Cookie", cookies)
                            }
                        }
                        .crossfade(true)
                        .build()
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    AsyncImage(
                        model = imgRequest,
                        contentDescription = stringResource(R.string.active_media_preview),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = activeMedia.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    
                    if (activeMedia.metadata?.creationDate != null) {
                        val dateStr = DateUtils.formatMediaDate(activeMedia.metadata?.creationDate, activeMedia.metadata?.creationDateOffset, unknownLabel = unknownLabel)
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    val currentIndex = geotaggedMedia.indexOf(activeMedia) + 1
                    Text(
                        text = stringResource(R.string.map_counter_format, currentIndex, geotaggedMedia.size, mediaList.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Timeline Section (LazyRow of thumbnails)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val currentIndex = sortedMedia.indexOfFirst { it.id?.toString() == activeMediaId }
                val progress = if (sortedMedia.isEmpty()) 0f else {
                    if (currentIndex < 0) 0f else (currentIndex.toFloat() / (sortedMedia.size - 1).coerceAtLeast(1).toFloat())
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var isPlayFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { 
                            onPlayingChange(!isPlaying) 
                            onResetHeaderTimer()
                        },
                        modifier = Modifier
                            .onFocusChanged { isPlayFocused = it.isFocused }
                            .border(
                                width = if (isPlayFocused) 3.dp else 0.dp,
                                color = if (isPlayFocused) MaterialTheme.colorScheme.secondary else androidx.compose.ui.graphics.Color.Transparent,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    LazyRow(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedMedia, key = { it.id ?: it.hashCode() }) { media ->
                            val isSelected = activeMediaId == media.id?.toString()
                            val cookies = viewModel.prefs.cookies
                            val thumbUrl = remember(media.id, media.parentPath, media.name, activeServerUrl, thumbSuffix) {
                                viewModel.getThumbnailUrl(media).let {
                                    if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it
                                }
                            }
                            val cacheKey = "thumb_${media.id}_${media.name}"
                            val imgRequest = remember(thumbUrl, cookies, cacheKey) {
                                ImageRequest.Builder(context)
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .memoryCacheKey(cacheKey)
                                    .diskCacheKey(cacheKey)
                                    .data(thumbUrl)
                                    .apply {
                                        if (cookies.isNotBlank()) {
                                            setHeader("Cookie", cookies)
                                        }
                                    }
                                    .crossfade(true)
                                    .build()
                            }

                            var isFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .border(
                                        width = if (isFocused || isSelected) 3.dp else 0.dp,
                                        color = if (isFocused) MaterialTheme.colorScheme.secondary else if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        activeMediaId = media.id?.toString()
                                        onPlayingChange(false)
                                        onResetHeaderTimer()
                                    }
                                    .focusable()
                            ) {
                                AsyncImage(
                                    model = imgRequest,
                                    contentDescription = stringResource(R.string.thumbnail_cd),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                        }
                    }
                } // end Row
            } // end Column
        } // end Box
    } // end Column
}

enum class MapProvider(@StringRes val labelResId: Int, val url: String, val attribution: String) {
    BASE(
        R.string.map_provider_base,
        "https://api.maptiler.com/maps/basic-v2/{z}/{x}/{y}.png?key=lZTC1a9vLiM26GQ9Vxmu",
        "&copy; MapTiler &copy; OpenStreetMap contributors"
    ),
    DARK_MATTER(
        R.string.map_provider_dark_matter,
        "https://api.maptiler.com/maps/dataviz-dark/{z}/{x}/{y}.png?key=lZTC1a9vLiM26GQ9Vxmu",
        "&copy; MapTiler &copy; OpenStreetMap contributors"
    ),
    STREETS(
        R.string.map_provider_streets,
        "https://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key=lZTC1a9vLiM26GQ9Vxmu",
        "&copy; MapTiler &copy; OpenStreetMap contributors"
    ),
    TOPO(
        R.string.map_provider_topo,
        "https://api.maptiler.com/maps/topo-v2/{z}/{x}/{y}.png?key=lZTC1a9vLiM26GQ9Vxmu",
        "&copy; MapTiler &copy; OpenStreetMap contributors"
    ),
    SATELLITE(
        R.string.map_provider_satellite,
        "https://api.maptiler.com/maps/hybrid/{z}/{x}/{y}.jpg?key=lZTC1a9vLiM26GQ9Vxmu",
        "&copy; MapTiler &copy; OpenStreetMap contributors"
    );

    val previewUrl: String
        get() = when (this) {
            BASE -> "https://api.maptiler.com/maps/basic-v2/3/4/2.png?key=lZTC1a9vLiM26GQ9Vxmu"
            DARK_MATTER -> "https://api.maptiler.com/maps/dataviz-dark/3/4/2.png?key=lZTC1a9vLiM26GQ9Vxmu"
            STREETS -> "https://api.maptiler.com/maps/streets-v2/3/4/2.png?key=lZTC1a9vLiM26GQ9Vxmu"
            TOPO -> "https://api.maptiler.com/maps/topo-v2/3/4/2.png?key=lZTC1a9vLiM26GQ9Vxmu"
            SATELLITE -> "https://api.maptiler.com/maps/hybrid/3/4/2.jpg?key=lZTC1a9vLiM26GQ9Vxmu"
        }
}

