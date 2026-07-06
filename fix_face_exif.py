import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

old_draw = """                        val originalW = media.metadata?.size?.width?.toFloat() ?: intrinsicSize.width
                        val originalH = media.metadata?.size?.height?.toFloat() ?: intrinsicSize.height
                        if (showFaceRegions && originalW > 0f && originalH > 0f) {"""

new_draw = """                        var originalW = media.metadata?.size?.width?.toFloat() ?: intrinsicSize.width
                        var originalH = media.metadata?.size?.height?.toFloat() ?: intrinsicSize.height
                        
                        if (intrinsicSize.width > 0 && intrinsicSize.height > 0 && originalW > 0 && originalH > 0) {
                            val intrinsicIsPortrait = intrinsicSize.width < intrinsicSize.height
                            val originalIsPortrait = originalW < originalH
                            if (intrinsicIsPortrait != originalIsPortrait) {
                                val temp = originalW
                                originalW = originalH
                                originalH = temp
                            }
                        }
                        
                        if (showFaceRegions && originalW > 0f && originalH > 0f) {"""

content = content.replace(old_draw, new_draw)

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
