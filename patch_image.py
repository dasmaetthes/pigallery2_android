import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

icon_code = """
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val CameraLensSide: ImageVector
    get() = ImageVector.Builder(
        name = "CameraLensSide",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)) {
            // Mount
            moveTo(4f, 7f)
            lineTo(6f, 7f)
            lineTo(6f, 17f)
            lineTo(4f, 17f)
            close()
            
            // First ring
            moveTo(7f, 5f)
            lineTo(13f, 5f)
            lineTo(13f, 19f)
            lineTo(7f, 19f)
            close()
            
            // Second ring (curved front)
            moveTo(14f, 4f)
            lineTo(16f, 4f)
            curveTo(19f, 4f, 20f, 8f, 20f, 12f)
            curveTo(20f, 16f, 19f, 20f, 16f, 20f)
            lineTo(14f, 20f)
            close()
        }
    }.build()
"""

if "val CameraLensSide: ImageVector" not in content:
    # Insert before the first composable or at the end of imports
    # find first Composable
    idx = content.find("@Composable")
    if idx != -1:
        content = content[:idx] + icon_code + "\n" + content[idx:]

# Replace Icons.Outlined.Camera (or Lens) with CameraLensSide
content = content.replace("Icons.Outlined.Camera,", "CameraLensSide,")
content = content.replace("Icons.Outlined.Lens,", "CameraLensSide,")

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
