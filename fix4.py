import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

bad = """                    val groupedMedia = remember(mediaList) {
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
    LazyVerticalGrid("""

content = content.replace(bad, "                    LazyVerticalGrid(")

bad2 = """                    val groupedMedia = remember(mediaList) {
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
    }    LazyVerticalGrid("""

content = content.replace(bad2, "                    LazyVerticalGrid(")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)

