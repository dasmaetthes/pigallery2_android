import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

# Find the end of LazyVerticalGrid
target = """                    }
                }
            }
        }
    }
}

@Composable
fun BreadcrumbBar("""

replacement = """                    }
                }
            }
        }
        }
    }
}

@Composable
fun BreadcrumbBar("""
content = content.replace(target, replacement)

grouped_media_logic = """
    val groupedMedia = remember(mediaList) {
        mediaList.groupBy { media ->
            val timestamp = media.metadata?.creationDate
            if (timestamp == null) "Unbekannt"
            else {
                val ms = if (timestamp < 10000000000L) timestamp * 1000L else timestamp
                try {
                    java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(ms))
                } catch (e: Exception) {
                    "Unbekannt"
                }
            }
        }
    }

    LazyVerticalGrid(
"""

content = content.replace("    LazyVerticalGrid(\n", grouped_media_logic)

old_render = """        // Render Media grid
        items(mediaList) { media ->"""

new_render = """        // Render Media grid
        groupedMedia.forEach { (monthYear, mediaItems) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = monthYear,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp, end = 4.dp).fillMaxWidth()
                )
            }
            items(mediaItems) { media ->"""

content = content.replace(old_render, new_render)

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)

