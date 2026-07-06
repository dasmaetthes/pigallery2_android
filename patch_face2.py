import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

old_draw = """                                    androidx.compose.ui.graphics.drawscope.drawIntoCanvas { canvas ->
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
                                    }"""

new_draw = """                                    // Draw face box (PiGallery2 style: white border, 2px, 5px radius)
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
                                        
                                        val textCenterX = boxLeft + boxW / 2f
                                        val textTop = boxTop + boxH + 4.dp.toPx() // Below the box
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
                                        
                                        androidx.compose.ui.graphics.drawscope.drawIntoCanvas { canvas ->
                                            canvas.nativeCanvas.drawText(
                                                face.name,
                                                textCenterX,
                                                bgBottom - 4.dp.toPx() - paint.descent(),
                                                paint
                                            )
                                        }
                                    }"""

content = content.replace(old_draw, new_draw)
with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
