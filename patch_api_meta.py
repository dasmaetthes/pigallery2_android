import sys

file_path = "/app/applet/app/src/main/java/com/example/data/PiGalleryApi.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """@JsonClass(generateAdapter = true)
data class ApiMediaMetadata(
    val size: ApiSize?,
    val creationDate: Long?
)"""

replacement = """@JsonClass(generateAdapter = true)
data class ApiMediaMetadata(
    val size: ApiSize?,
    val creationDate: Long?,
    val cameraData: ApiCameraData?,
    val keywords: List<String>?,
    val faces: List<ApiFace>?
)

@JsonClass(generateAdapter = true)
data class ApiCameraData(
    val ISO: Int?,
    val make: String?,
    val model: String?,
    val fStop: Double?,
    val exposure: Double?,
    val focalLength: Double?,
    val lens: String?
)

@JsonClass(generateAdapter = true)
data class ApiFace(
    val name: String?
)"""

if target in content:
    content = content.replace(target, replacement)
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched ApiMediaMetadata")
else:
    print("Target not found")
