import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

old_draw = """                                        androidx.compose.ui.graphics.drawscope.drawIntoCanvas { canvas ->
                                            canvas.nativeCanvas.drawText(
                                                face.name,
                                                textCenterX,
                                                bgBottom - 4.dp.toPx() - paint.descent(),
                                                paint
                                            )
                                        }"""

new_draw = """                                        drawContext.canvas.nativeCanvas.drawText(
                                            face.name,
                                            textCenterX,
                                            bgBottom - 4.dp.toPx() - paint.descent(),
                                            paint
                                        )"""

content = content.replace(old_draw, new_draw)

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
