import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

# 1. Add showFaceRegions state
show_bars_state = "    var showBars by remember { mutableStateOf(true) }"
face_regions_state = "    var showFaceRegions by remember { mutableStateOf(false) }"
if "showFaceRegions" not in content:
    content = content.replace(show_bars_state, show_bars_state + "\n" + face_regions_state)

# 2. Add Face import
face_import = "import androidx.compose.material.icons.filled.Face"
if face_import not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Close", "import androidx.compose.material.icons.filled.Close\nimport androidx.compose.material.icons.filled.Face\nimport androidx.compose.material.icons.filled.MoreVert")


pattern = re.compile(r"actions = \{.*?(?=colors = TopAppBarDefaults)", re.DOTALL)

new_actions = """actions = {
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
                    """
content = pattern.sub(new_actions, content)

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
