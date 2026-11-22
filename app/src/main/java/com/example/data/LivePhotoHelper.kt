package com.example.data

/**
 * Handles Apple Live Photo pairing and companion video detection/resolution
 * for PiGallery2 v3.8+ (and legacy folder-based IMG_XXXX.HEIC + IMG_XXXX.MOV pairs).
 */
object LivePhotoHelper {

    private val PHOTO_EXTENSIONS = setOf("heic", "heif", "jpg", "jpeg", "png", "webp")
    private val LIVE_VIDEO_EXTENSIONS = setOf("mov", "mp4")

    // Typical Apple Live Photo companion clips are ~1.5-3.5 seconds; allow up to 6.5s safely.
    private const val MAX_LIVE_VIDEO_DURATION_MS = 6_500.0
    private const val MAX_LIVE_VIDEO_DURATION_SEC = 6.5

    fun isLikelyVideoFilename(pathOrName: String?): Boolean {
        if (pathOrName.isNullOrBlank()) return false
        val ext = pathOrName.substringAfterLast('.', "").lowercase()
        return ext in LIVE_VIDEO_EXTENSIONS
    }

    private fun isPhotoCandidate(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in PHOTO_EXTENSIONS
    }

    private fun isCompanionMovCandidate(media: ApiMedia): Boolean {
        val ext = media.name.substringAfterLast('.', "").lowercase()
        if (ext != "mov") return false
        val duration = media.metadata?.duration ?: return true
        // PiGallery2 may report duration in milliseconds (> 100) or seconds (<= 100)
        return if (duration > 100.0) {
            duration <= MAX_LIVE_VIDEO_DURATION_MS
        } else {
            duration <= MAX_LIVE_VIDEO_DURATION_SEC
        }
    }

    private fun normalizedDir(parentPath: String?): String {
        return (parentPath ?: "").replace('\\', '/').trim().trim('/')
    }

    private fun baseNameLower(fileName: String): String {
        return fileName.substringBeforeLast('.', fileName).lowercase()
    }

    /**
     * Pairs still photos with companion .mov Live Photo clips located in the same directory
     * (e.g. IMG_1234.HEIC + IMG_1234.MOV), marking the photo with isLivePhoto=true and
     * liveVideoPath=<movName>, while hiding the duplicate companion .mov from the grid.
     *
     * Also hides companion videos explicitly referenced by server-provided metadata.liveVideoPath.
     */
    fun pairLivePhotos(mediaList: List<ApiMedia>): List<ApiMedia> {
        if (mediaList.isEmpty()) return mediaList

        // Collect companion videos referenced directly by server metadata
        val referencedCompanionKeys = HashSet<String>()
        for (item in mediaList) {
            val path = item.metadata?.liveVideoPath
            if (!item.isVideo && isLikelyVideoFilename(path)) {
                val fileName = path!!.substringAfterLast('/').lowercase()
                referencedCompanionKeys.add("${normalizedDir(item.parentPath)}|$fileName")
            }
        }

        // Index short .mov candidates by (directory, baseName)
        val movByKey = HashMap<String, ApiMedia>()
        for (item in mediaList) {
            if (isCompanionMovCandidate(item)) {
                val key = "${normalizedDir(item.parentPath)}|${baseNameLower(item.name)}"
                movByKey.putIfAbsent(key, item)
            }
        }

        val consumedMovKeys = HashSet<String>()
        val enriched = ArrayList<ApiMedia>(mediaList.size)

        for (item in mediaList) {
            if (!item.isVideo && isPhotoCandidate(item.name)) {
                val key = "${normalizedDir(item.parentPath)}|${baseNameLower(item.name)}"
                val companionMov = movByKey[key]
                if (companionMov != null) {
                    consumedMovKeys.add("${normalizedDir(companionMov.parentPath)}|${companionMov.name.lowercase()}")
                    val existingMeta = item.metadata ?: ApiMediaMetadata()
                    val resolvedVideoPath = if (isLikelyVideoFilename(existingMeta.liveVideoPath)) {
                        existingMeta.liveVideoPath
                    } else {
                        companionMov.name
                    }
                    enriched.add(
                        item.copy(
                            metadata = existingMeta.copy(
                                isLivePhoto = true,
                                liveVideoPath = resolvedVideoPath
                            )
                        )
                    )
                } else {
                    enriched.add(item)
                }
            } else {
                enriched.add(item)
            }
        }

        if (consumedMovKeys.isEmpty() && referencedCompanionKeys.isEmpty()) {
            return enriched
        }

        return enriched.filterNot { item ->
            if (!item.isVideo) return@filterNot false
            val fullKey = "${normalizedDir(item.parentPath)}|${item.name.lowercase()}"
            fullKey in consumedMovKeys || fullKey in referencedCompanionKeys
        }
    }

    /**
     * Resolves the relative server path for a Live Photo's companion video.
     */
    fun resolveCompanionVideoPath(media: ApiMedia, fallbackParentPath: String = ""): String? {
        if (!media.isLivePhoto || media.isVideo) return null
        val parent = normalizedDir(media.parentPath ?: fallbackParentPath)
        val rawPath = media.metadata?.liveVideoPath?.replace('\\', '/')?.trim()?.trim('/')

        if (!rawPath.isNullOrBlank() && isLikelyVideoFilename(rawPath)) {
            return if (rawPath.contains('/')) {
                rawPath
            } else if (parent.isNotEmpty()) {
                "$parent/$rawPath"
            } else {
                rawPath
            }
        }

        // Fallback: same base name with .mov extension in same folder
        val baseName = media.name.substringBeforeLast('.', media.name)
        val movName = "$baseName.mov"
        return if (parent.isNotEmpty()) "$parent/$movName" else movName
    }
}
