import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

old_list = """            } else if (item is List<*>) {
                val w = (item.getOrNull(0) as? Number)?.toInt() ?: 0
                val h = (item.getOrNull(1) as? Number)?.toInt() ?: 0
                val l = (item.getOrNull(2) as? Number)?.toInt() ?: 0
                val t = (item.getOrNull(3) as? Number)?.toInt() ?: 0
                val box = if (w > 0 && h > 0) ApiFaceBox(w, h, l, t) else null"""

new_list = """            } else if (item is List<*>) {
                val t = (item.getOrNull(0) as? Number)?.toInt() ?: 0
                val l = (item.getOrNull(1) as? Number)?.toInt() ?: 0
                val h = (item.getOrNull(2) as? Number)?.toInt() ?: 0
                val w = (item.getOrNull(3) as? Number)?.toInt() ?: 0
                val box = if (w > 0 && h > 0) ApiFaceBox(width = w, height = h, left = l, top = t) else null"""

content = content.replace(old_list, new_list)

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
