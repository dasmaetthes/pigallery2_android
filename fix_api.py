with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

content = content.replace(
"""data class ApiSize(
    val width: Int?,
import android.content.Context
import okhttp3.Cache
import java.io.File

class PiGalleryApi(private val context: Context) {""",
"""data class ApiSize(
    val width: Int?,
    val height: Int?
)

class PiGalleryApi(private val context: android.content.Context) {""")

content = "import android.content.Context\nimport okhttp3.Cache\nimport java.io.File\n" + content

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

content = content.replace("import coil.request.ImageRequest\n\nimport coil.request.ImageRequest", "import coil.request.ImageRequest")
# Check if there are still multiple coil.request.ImageRequest
lines = content.splitlines()
out = []
seen = set()
for l in lines:
    if l.startswith("import coil.request.ImageRequest") or l.startswith("import coil.imageLoader"):
        if l not in seen:
            seen.add(l)
            out.append(l)
    else:
        out.append(l)

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write("\n".join(out))
