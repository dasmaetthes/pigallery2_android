import sys

file_path = "/app/applet/app/src/main/java/com/example/ui/GalleryScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """fun RediscoverMediaItem(
    media: ApiMedia,
    viewModel: GalleryViewModel,
    list: List<ApiMedia> = emptyList(),
    cornerRadius: Dp,
    aspectRatio: Float
) {
    val context = LocalContext.current
    val cookies = viewModel.getCookiesHeader()
    val cardRatio = if (aspectRatio > 0f) aspectRatio else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(cardRatio)
            .clip(RoundedCornerShape(cornerRadius))
            .clickable { viewModel.selectMedia(media, list) },
        shape = RoundedCornerShape(cornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {"""

replacement = """@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    ) {"""

if target in content:
    content = content.replace(target, replacement)
    
    target_box = """            if (media.isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}"""
    
    replacement_box = """            if (media.isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
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
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                                .background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                }
            }
        }
    }
}"""

    content = content.replace(target_box, replacement_box)
    
    # Just to be sure, remove any double @Composable if it had one.
    content = content.replace("@Composable\n@OptIn", "@OptIn")
    
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched RediscoverMediaItem")
else:
    print("Target not found")
