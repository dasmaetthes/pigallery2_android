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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.example.data.ApiMedia
import androidx.activity.compose.BackHandler

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
    var headerResetTrigger by remember { mutableStateOf(1) }

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

    BackHandler { onDismiss() }

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
                            visible = isHeaderVisible,
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
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Map Menu",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = showProviderMenu,
                                    onDismissRequest = { showProviderMenu = false },
                                    modifier = Modifier.width(220.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Travel Track") },
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
                                            text = "Map Style",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    MapProvider.values().forEach { provider ->
                                        val isSelected = selectedProvider == provider
                                        DropdownMenuItem(
                                            onClick = {
                                                selectedProvider = provider
                                                showProviderMenu = false
                                                headerResetTrigger++
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
                                                            contentDescription = "${provider.label} preview",
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                        )
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    
                                                    Text(
                                                        text = provider.label,
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
            contentDescription = "No Location Data",
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Geotagged Media Found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "None of the files in this collection contain EXIF location coordinates.",
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
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Live-switch tile layers dynamically without reloading the entire WebView state
    LaunchedEffect(selectedProvider) {
        webViewRef?.evaluateJavascript(
            "changeTileLayer('${selectedProvider.url}', '${selectedProvider.attribution.replace("'", "\\'")}')",
            null
        )
    }

    LaunchedEffect(showTravelTrack, webViewRef) {
        webViewRef?.evaluateJavascript(
            "if (typeof toggleTravelTrack !== 'undefined') toggleTravelTrack(${showTravelTrack})",
            null
        )
    }

    val lastLoadedHash = remember { IntArray(1) { 0 } }
    
    // Prepare HTML content for Leaflet Map
    val htmlContent = remember(geotaggedMedia, initialMediaId) {
        val markersBuilder = StringBuilder()
        val matchingMedia = if (initialMediaId != null) {
            geotaggedMedia.find { it.id?.toString() == initialMediaId }
        } else {
            null
        }
        val startGps = matchingMedia?.metadata?.gps ?: geotaggedMedia.firstOrNull()?.metadata?.gps
        val initialCenter = if (startGps != null) "[${startGps.latitude}, ${startGps.longitude}]" else "[0, 0]"
        
        for (media in geotaggedMedia) {
            val gps = media.metadata?.gps ?: continue
            val id = media.id?.toString() ?: ""
            val creationDate = media.metadata?.creationDate
            val dateTitle = if (creationDate != null) {
                val ms = if (creationDate < 10000000000L) creationDate * 1000L else creationDate
                java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ms))
            } else {
                ""
            }
            val safeName = media.name.replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ").replace("\r", "")
            val title = if (dateTitle.isNotBlank() && safeName.isNotBlank()) "$safeName<br/>$dateTitle" else dateTitle.ifBlank { safeName }
            val rawThumbUrl = viewModel.getThumbnailUrl(media)
            val fullThumbUrl = rawThumbUrl.let { if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it }
            val thumbUrl = fullThumbUrl.replace("'", "\\'").replace("\"", "\\\"")
            markersBuilder.append("addMarker(${gps.latitude}, ${gps.longitude}, '${id}', \"${title}\", '${thumbUrl}');\n")
        }

        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css" />
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/MarkerCluster.css" />
            <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js"></script>
            <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/leaflet.markercluster.js"></script>
            <script src="https://unpkg.com/leaflet-polylinedecorator@1.6.0/dist/leaflet.polylineDecorator.js"></script>
            <style>
                body { padding: 0; margin: 0; background: #121212; color: #ffffff; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
                html, body, #map { height: 100%; width: 100%; }
                .leaflet-container { background: #121212; }
                .leaflet-bar { border: none !important; box-shadow: 0 4px 10px rgba(0,0,0,0.4) !important; }
                .leaflet-bar a { background-color: #2c2c2c !important; color: #ffffff !important; border-bottom: 1px solid #3c3c3c !important; }
                .leaflet-bar a:hover { background-color: #3c3c3c !important; }
                .custom-pin-icon { background: none !important; border: none !important; }

                /* Custom Premium Dark Theme Marker Cluster Styles */
                .marker-cluster-custom {
                    background: rgba(33, 150, 243, 0.2);
                    border-radius: 50%;
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
                    width: 32px;
                    height: 32px;
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
                    background: rgba(0, 188, 212, 0.2);
                }
                .marker-cluster-medium-custom .marker-cluster-custom-inner {
                    background: #00BCD4;
                    box-shadow: 0 4px 12px rgba(0, 188, 212, 0.6);
                }
                
                /* Large size cluster (100+ items) */
                .marker-cluster-large-custom {
                    background: rgba(156, 39, 176, 0.2);
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

                // Initialize Marker Cluster Group with custom layout and responsive sizing
                var markersGroup = L.markerClusterGroup({
                    showCoverageOnHover: false,
                    zoomToBoundsOnClick: true,
                    spiderfyOnMaxZoom: true,
                    maxClusterRadius: 50,
                    iconCreateFunction: function(cluster) {
                        var count = cluster.getChildCount();
                        var mainClass = 'marker-cluster-custom';
                        if (count >= 100) {
                            mainClass += ' marker-cluster-large-custom';
                        } else if (count >= 10) {
                            mainClass += ' marker-cluster-medium-custom';
                        }
                        return L.divIcon({
                            html: '<div class="marker-cluster-custom-inner"><span>' + count + '</span></div>',
                            className: mainClass,
                            iconSize: L.point(44, 44),
                            iconAnchor: L.point(22, 22)
                        });
                    }
                });

                var markers = [];
                var markersById = {};
                function addMarker(lat, lon, id, title, thumbUrl) {
                    var thumbIcon = L.divIcon({
                        html: '<div style="width: 40px; height: 40px; border-radius: 50%; overflow: hidden; border: 2px solid white; box-shadow: 0 2px 5px rgba(0,0,0,0.5); background-color: #ccc;"><img src="' + thumbUrl + '" style="width: 100%; height: 100%; object-fit: cover; display: block;" /></div>',
                        iconSize: [44, 44],
                        iconAnchor: [22, 22],
                        popupAnchor: [0, -22],
                        className: 'custom-thumb-icon'
                    });

                    var marker = L.marker([lat, lon], {icon: thumbIcon});
                    marker.thumbUrl = thumbUrl;
                    marker.on('click', function() {
                        onMarkerClick(id);
                    });
                    
                    markersGroup.addLayer(marker);
                    markers.push(marker);
                    markersById[id] = marker;
                }

                function cacheTrackTiles(startLatLng, endLatLng, targetZoom) {
                    // Empty to prevent network exhaustion and freezes
                }

                var activeHighlightedMarker = null;
                function highlightMarker(id, durationSeconds) {
                    var baseDur = durationSeconds ? (durationSeconds * 0.8) : 4.0;
                    
                    // Reset previous highlighted marker icon and zIndex
                    if (activeHighlightedMarker) {
                        activeHighlightedMarker.setZIndexOffset(0);
                        if (activeHighlightedMarker.thumbUrl) {
                            var normalIcon = L.divIcon({
                                html: '<div style="width: 40px; height: 40px; border-radius: 50%; overflow: hidden; border: 2px solid white; box-shadow: 0 2px 5px rgba(0,0,0,0.5); background-color: #ccc;"><img src="' + activeHighlightedMarker.thumbUrl + '" style="width: 100%; height: 100%; object-fit: cover; display: block;" /></div>',
                                iconSize: [44, 44],
                                iconAnchor: [22, 22],
                                popupAnchor: [0, -22],
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
                                html: '<div style="width: 52px; height: 52px; border-radius: 50%; overflow: hidden; border: 4px solid #FFC107; box-shadow: 0 0 15px #FFC107; background-color: #ccc;"><img src="' + marker.thumbUrl + '" style="width: 100%; height: 100%; object-fit: cover; display: block;" /></div>',
                                iconSize: [60, 60],
                                iconAnchor: [30, 30],
                                popupAnchor: [0, -30],
                                className: 'custom-thumb-icon highlighted'
                            });
                            marker.setIcon(highlightedIcon);
                        }

                        var latLng = marker.getLatLng();
                        var currentCenter = map.getCenter();
                        var dist = map.distance(currentCenter, latLng);
                        var targetZoom = Math.max(map.getZoom(), 17);
                        
                        cacheTrackTiles(currentCenter, latLng, targetZoom);
                        
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
                    if (show) {
                        if (!travelTrackLayer && markers.length > 1) {
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
                    } else {
                        if (travelTrackLayer) {
                            map.removeLayer(travelTrackLayer);
                            travelTrackLayer = null;
                        }
                        if (travelTrackDecorator) {
                            map.removeLayer(travelTrackDecorator);
                            travelTrackDecorator = null;
                        }
                    }
                }

                // Injected markers execution
                ${markersBuilder.toString()}
                map.addLayer(markersGroup);
                fitBounds();

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
        geotaggedMedia.sortedBy { it.metadata?.creationDate ?: 0L } 
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
                        setBackgroundColor(android.graphics.Color.parseColor("#121212"))
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // Periodically reinforce folder creation to ensure it exists during asynchronous engine initialization
                        postDelayed({ ensureCacheDirs(ctx) }, 500)
                        postDelayed({ ensureCacheDirs(ctx) }, 2000)
                        postDelayed({ ensureCacheDirs(ctx) }, 5000)

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: android.webkit.WebResourceRequest
                            ): android.webkit.WebResourceResponse? {
                                val url = request.url.toString()
                                val cookies = viewModel.prefs.cookies
                                if (cookies.isNotBlank() && url.contains("/gallery/content/")) {
                                    try {
                                        val okRequest = okhttp3.Request.Builder()
                                            .url(url)
                                            .header("Cookie", cookies)
                                            .build()
                                        val response = viewModel.api.client.newCall(okRequest).execute()
                                        if (response.isSuccessful) {
                                            val contentType = response.header("Content-Type", "image/jpeg") ?: "image/jpeg"
                                            val mimeType = contentType.substringBefore(';')
                                            val encoding = if (contentType.contains("charset=")) contentType.substringAfter("charset=") else "UTF-8"
                                            return android.webkit.WebResourceResponse(mimeType, encoding, response.body?.byteStream())
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                return false // Handle internally
                            }
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                view?.context?.let { ensureCacheDirs(it) }
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.evaluateJavascript("if (typeof toggleTravelTrack !== 'undefined') toggleTravelTrack(${showTravelTrack})", null)
                                currentActiveMediaId?.let { mediaId ->
                                    view?.evaluateJavascript("if (typeof highlightMarker !== 'undefined') highlightMarker('$mediaId', 1)", null)
                                }
                                view?.context?.let { ensureCacheDirs(it) }
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
                    
                    var serverUrl = viewModel.prefs.serverUrl
                    if (serverUrl.isNotBlank()) {
                        if (!serverUrl.startsWith("http")) {
                            serverUrl = "http://$serverUrl"
                        }
                        cookieManager.setCookie(serverUrl, viewModel.prefs.cookies)
                        cookieManager.flush()
                    }

                    val baseUrl = if (serverUrl.isNotBlank()) serverUrl else "https://localhost/"
                    
                    // Only load once to prevent flickering on recomposition, 
                    // unless we want to rebuild the map entirely.
                    if (lastLoadedHash[0] != htmlContent.hashCode()) {
                        webView.loadDataWithBaseURL(
                            baseUrl,
                            htmlContent,
                            "text/html",
                            "UTF-8",
                            null
                        )
                        lastLoadedHash[0] = htmlContent.hashCode()
                    }
                }
            )

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
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Map",
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
                        contentDescription = "Zoom In"
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
                        contentDescription = "Zoom Out"
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
                        contentDescription = "Center Map"
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
                        text = if (isMapInteractionEnabled) "Use D-Pad to pan. Press OK or BACK to exit." else "Press OK to interact with map",
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
                val rawThumbUrl = viewModel.getThumbnailUrl(activeMedia)
                val thumbUrl = rawThumbUrl.let { if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it }
                val serverUrl = viewModel.prefs.serverUrl.let { if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it }
                val cookies = viewModel.prefs.cookies
                val imgRequest = ImageRequest.Builder(LocalContext.current)
                    .data(thumbUrl)
                    .apply {
                        if (serverUrl.isNotBlank() && thumbUrl.startsWith(serverUrl)) {
                            addHeader("Cookie", cookies)
                        }
                    }
                    .build()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    AsyncImage(
                        model = imgRequest,
                        contentDescription = "Active Media Preview",
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
                    
                    val creationDate = activeMedia.metadata?.creationDate
                    if (creationDate != null) {
                        val ms = if (creationDate < 10000000000L) creationDate * 1000L else creationDate
                        val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ms))
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    val currentIndex = geotaggedMedia.indexOf(activeMedia) + 1
                    Text(
                        text = "$currentIndex / ${geotaggedMedia.size} (${mediaList.size} all)",
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
                            contentDescription = if (isPlaying) "Pause" else "Play",
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
                            val rawThumbUrl = viewModel.getThumbnailUrl(media)
                            val thumbUrl = rawThumbUrl.let { if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it }
                            val serverUrl = viewModel.prefs.serverUrl.let { if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it }
                            val cookies = viewModel.prefs.cookies
                            val imgRequest = ImageRequest.Builder(LocalContext.current)
                                .data(thumbUrl)
                                .apply {
                                    if (serverUrl.isNotBlank() && thumbUrl.startsWith(serverUrl)) {
                                        addHeader("Cookie", cookies)
                                    }
                                }
                                .build()

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
                                    contentDescription = "Thumbnail",
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

enum class MapProvider(val label: String, val url: String, val attribution: String) {
    BASE(
        "Base",
        "https://api.maptiler.com/maps/basic-v2/{z}/{x}/{y}.png?key=lZTC1a9vLiM26GQ9Vxmu",
        "&copy; MapTiler &copy; OpenStreetMap contributors"
    ),
    DARK_MATTER(
        "Dark Matter",
        "https://api.maptiler.com/maps/dataviz-dark/{z}/{x}/{y}.png?key=lZTC1a9vLiM26GQ9Vxmu",
        "&copy; MapTiler &copy; OpenStreetMap contributors"
    ),
    STREETS(
        "Streets",
        "https://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key=lZTC1a9vLiM26GQ9Vxmu",
        "&copy; MapTiler &copy; OpenStreetMap contributors"
    ),
    TOPO(
        "Topo",
        "https://api.maptiler.com/maps/topo-v2/{z}/{x}/{y}.png?key=lZTC1a9vLiM26GQ9Vxmu",
        "&copy; MapTiler &copy; OpenStreetMap contributors"
    ),
    SATELLITE(
        "Satellite Hybrid",
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

