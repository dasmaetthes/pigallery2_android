import sys

file_path = "/app/applet/app/src/main/java/com/example/data/PiGalleryApi.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """@JsonClass(generateAdapter = true)
data class ApiMediaMetadata(
    val size: ApiSize?,
    val creationDate: Long?,
    val cameraData: ApiCameraData?,
    val keywords: List<String>?,
    val faces: List<ApiFace>?
)"""

replacement = """@JsonClass(generateAdapter = true)
data class ApiMediaMetadata(
    val size: ApiSize? = null,
    val creationDate: Long? = null,
    val cameraData: ApiCameraData? = null,
    val keywords: List<String>? = null,
    val faces: List<ApiFace>? = null
)"""

if target in content:
    content = content.replace(target, replacement)
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched ApiMediaMetadata defaults")
else:
    print("Target not found")
