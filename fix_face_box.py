import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

old_draw = """                        if (showFaceRegions && intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                            val scaleX = size.width / intrinsicSize.width
                            val scaleY = size.height / intrinsicSize.height
                            val fitScale = minOf(scaleX, scaleY)
                            
                            val drawWidth = intrinsicSize.width * fitScale
                            val drawHeight = intrinsicSize.height * fitScale
                            
                            val leftOffset = (size.width - drawWidth) / 2f
                            val topOffset = (size.height - drawHeight) / 2f

                            media.metadata?.faces?.forEach { face: com.example.data.ApiFace ->
                                face.box?.let { box ->
                                    val boxLeft = leftOffset + box.left * fitScale
                                    val boxTop = topOffset + box.top * fitScale
                                    val boxW = box.width * fitScale
                                    val boxH = box.height * fitScale"""

new_draw = """                        val originalW = media.metadata?.size?.width?.toFloat() ?: intrinsicSize.width
                        val originalH = media.metadata?.size?.height?.toFloat() ?: intrinsicSize.height
                        if (showFaceRegions && originalW > 0f && originalH > 0f) {
                            val scaleX = size.width / originalW
                            val scaleY = size.height / originalH
                            val fitScale = minOf(scaleX, scaleY)
                            
                            val drawWidth = originalW * fitScale
                            val drawHeight = originalH * fitScale
                            
                            val leftOffset = (size.width - drawWidth) / 2f
                            val topOffset = (size.height - drawHeight) / 2f

                            media.metadata?.faces?.forEach { face: com.example.data.ApiFace ->
                                face.box?.let { box ->
                                    // Sometimes bounding boxes are given relative to image size if they are very small
                                    // Or they are percentages? PiGallery2 uses absolute pixels. 
                                    // If box is somehow given in percentages (0..100) or normalized (0..1), we might need to handle it,
                                    // but we assume absolute pixels since width/height are Ints.
                                    val boxLeft = leftOffset + box.left * fitScale
                                    val boxTop = topOffset + box.top * fitScale
                                    val boxW = box.width * fitScale
                                    val boxH = box.height * fitScale"""

content = content.replace(old_draw, new_draw)

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
