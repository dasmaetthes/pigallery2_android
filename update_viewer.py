import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

topbar_old = """                    title = {
                        Text(
                            text = currentMedia.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },"""

topbar_new = """                    title = { },"""

if topbar_old in content:
    content = content.replace(topbar_old, topbar_new)
    print("Replaced topbar successfully")
else:
    print("Could not find topbar_old")

bottombar_old = """            // Share/Download Progress Indicator Overlay
            val shareProgress by viewModel.shareProgress.collectAsState()"""

bottombar_new = """            // Bottom Bar Overlay
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
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
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
            val shareProgress by viewModel.shareProgress.collectAsState()"""

if bottombar_old in content:
    content = content.replace(bottombar_old, bottombar_new)
    print("Replaced bottombar successfully")
else:
    print("Could not find bottombar_old")

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
