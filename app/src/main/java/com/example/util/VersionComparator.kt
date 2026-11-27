package com.example.util

/**
 * Compares semantic/dotted app version strings (e.g. "2.7", "v2.6", "2.7.1-beta.1")
 * so older releases are never offered as updates over newer development or release builds.
 */
object VersionComparator {

    private data class ParsedVersion(
        val coreParts: List<Long>,
        val preReleaseParts: List<String>
    )

    private val VERSION_REGEX =
        Regex("^\\s*[vV]?(\\d+(?:\\.\\d+)*)(?:[-.]?([0-9A-Za-z][0-9A-Za-z.-]*))?(?:\\+.*)?\\s*$")

    fun isRemoteNewer(remoteVersion: String?, currentVersion: String?): Boolean {
        val remote = parse(remoteVersion) ?: return false
        val current = parse(currentVersion) ?: return false
        return compare(remote, current) > 0
    }

    private fun parse(raw: String?): ParsedVersion? {
        if (raw.isNullOrBlank()) return null
        val match = VERSION_REGEX.matchEntire(raw) ?: return null
        val coreString = match.groupValues[1]
        val preString = match.groupValues[2]

        val coreParts = coreString.split('.').mapNotNull { it.toLongOrNull() }
        if (coreParts.isEmpty()) return null

        val preParts = if (preString.isBlank()) {
            emptyList()
        } else {
            preString.split('.').filter { it.isNotEmpty() }
        }
        return ParsedVersion(coreParts = coreParts, preReleaseParts = preParts)
    }

    private fun compare(a: ParsedVersion, b: ParsedVersion): Int {
        val maxCoreLen = maxOf(a.coreParts.size, b.coreParts.size)
        for (i in 0 until maxCoreLen) {
            val aPart = a.coreParts.getOrElse(i) { 0L }
            val bPart = b.coreParts.getOrElse(i) { 0L }
            if (aPart != bPart) return aPart.compareTo(bPart)
        }

        // Core versions are equal; stable release (no pre-release) > pre-release.
        if (a.preReleaseParts.isEmpty() && b.preReleaseParts.isEmpty()) return 0
        if (a.preReleaseParts.isEmpty()) return 1
        if (b.preReleaseParts.isEmpty()) return -1

        val maxPreLen = maxOf(a.preReleaseParts.size, b.preReleaseParts.size)
        for (i in 0 until maxPreLen) {
            if (i >= a.preReleaseParts.size) return -1
            if (i >= b.preReleaseParts.size) return 1

            val aId = a.preReleaseParts[i]
            val bId = b.preReleaseParts[i]
            val aNum = aId.toLongOrNull()
            val bNum = bId.toLongOrNull()

            val cmp = when {
                aNum != null && bNum != null -> aNum.compareTo(bNum)
                aNum != null -> -1 // Numeric identifiers have lower precedence than non-numeric
                bNum != null -> 1
                else -> aId.compareTo(bId, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }
}
