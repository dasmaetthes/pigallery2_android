import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

old_draw = """                        var originalW = media.metadata?.size?.width?.toFloat() ?: intrinsicSize.width
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
                                    val boxH = box.height * fitScale
                                    
                                    drawRect(
                                        color = Color.Yellow,
                                        topLeft = Offset(boxLeft, boxTop),
                                        size = Size(boxW, boxH),
                                        style = Stroke(width = 4f)
                                    )
                                    
                                    if (face.name != null) {
                                        drawContext.canvas.nativeCanvas.drawText(
                                            face.name,
                                            boxLeft,
                                            boxTop - 10f,
                                            android.graphics.Paint().apply {
                                                color = android.graphics.Color.YELLOW
                                                textSize = 40f
                                                isAntiAlias = true
                                            }
                                        )
                                    }
                                }
                            }
                        }"""

new_draw = """                        val metaW = media.metadata?.size?.width?.toFloat() ?: intrinsicSize.width
                        val metaH = media.metadata?.size?.height?.toFloat() ?: intrinsicSize.height
                        
                        if (showFaceRegions && intrinsicSize.width > 0f && intrinsicSize.height > 0f && metaW > 0f && metaH > 0f) {
                            val scaleX = size.width / intrinsicSize.width
                            val scaleY = size.height / intrinsicSize.height
                            val fitScale = minOf(scaleX, scaleY)
                            
                            val drawWidth = intrinsicSize.width * fitScale
                            val drawHeight = intrinsicSize.height * fitScale
                            
                            val leftOffset = (size.width - drawWidth) / 2f
                            val topOffset = (size.height - drawHeight) / 2f

                            media.metadata?.faces?.forEach { face: com.example.data.ApiFace ->
                                face.box?.let { box ->
                                    val rLeft = box.left.toFloat() / metaW
                                    val rTop = box.top.toFloat() / metaH
                                    val rWidth = box.width.toFloat() / metaW
                                    val rHeight = box.height.toFloat() / metaH
                                    
                                    val boxLeft = leftOffset + rLeft * drawWidth
                                    val boxTop = topOffset + rTop * drawHeight
                                    val boxW = rWidth * drawWidth
                                    val boxH = rHeight * drawHeight
                                    
                                    androidx.compose.ui.graphics.drawscope.drawIntoCanvas { canvas ->
                                        // Draw face box (PiGallery2 style: white border, 2px, 5px radius)
                                        drawRoundRect(
                                            color = Color.White,
                                            topLeft = Offset(boxLeft, boxTop),
                                            size = Size(boxW, boxH),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                        
                                        // Draw face name (PiGallery2 style: white text, transparent dark background)
                                        if (face.name != null) {
                                            val paint = android.graphics.Paint().apply {
                                                color = android.graphics.Color.WHITE
                                                textSize = 14.sp.toPx()
                                                isAntiAlias = true
                                                isFakeBoldText = true
                                                textAlign = android.graphics.Paint.Align.CENTER
                                            }
                                            
                                            val textWidth = paint.measureText(face.name)
                                            val textHeight = paint.descent() - paint.ascent()
                                            
                                            // PiGallery2 centers the text at the bottom or inside?
                                            // Actually it just shows below the box usually, or inside it.
                                            // Let's place it just above the box, centered.
                                            val textCenterX = boxLeft + boxW / 2f
                                            val textTop = boxTop - textHeight - 8.dp.toPx()
                                            val bgLeft = textCenterX - textWidth / 2f - 4.dp.toPx()
                                            val bgRight = textCenterX + textWidth / 2f + 4.dp.toPx()
                                            val bgTop = textTop
                                            val bgBottom = textTop + textHeight + 8.dp.toPx()
                                            
                                            // Background
                                            drawRoundRect(
                                                color = Color(0x33000000), // rgba(0,0,0,0.2)
                                                topLeft = Offset(bgLeft, bgTop),
                                                size = Size(bgRight - bgLeft, bgBottom - bgTop),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                                            )
                                            
                                            canvas.nativeCanvas.drawText(
                                                face.name,
                                                textCenterX,
                                                bgBottom - 4.dp.toPx() - paint.descent(),
                                                paint
                                            )
                                        }
                                    }
                                }
                            }
                        }"""

content = content.replace(old_draw, new_draw)
with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
