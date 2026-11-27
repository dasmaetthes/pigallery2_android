package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.TreeMap

data class BackedUpAlbum(
    val name: String,
    val searchQuery: Map<String, Any?>,
    val savedAtMs: Long = System.currentTimeMillis(),
    val lastKnownId: Int? = null
)

/**
 * Persists a per-server local backup snapshot of unlocked saved-search albums so that
 * albums lost during PiGallery2 updates or database resets can be selectively restored.
 *
 * Stored in dedicated SharedPreferences so logging out (`PreferencesManager.clearSession()`)
 * never wipes album backups.
 */
class AlbumBackupManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi: Moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(
        List::class.java,
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )
    )
    private val listAdapter = moshi.adapter<List<Map<String, Any?>>>(listType)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    companion object {
        private const val PREFS_NAME = "pigallery_album_backups"
        private const val KEY_PREFIX = "albums_backup_v1|"

        fun normalizeServerKey(serverUrl: String): String {
            val ensured = PiGalleryApi.ensureHttpUrl(serverUrl)
            if (ensured.isEmpty()) return ""
            return try {
                val uri = java.net.URI(ensured)
                val scheme = (uri.scheme ?: "http").lowercase()
                val host = uri.host?.lowercase()
                if (host.isNullOrBlank()) {
                    return ensured.trimEnd('/').lowercase()
                }
                val port = uri.port
                val portSuffix = if (port != -1 &&
                    !((scheme == "http" && port == 80) || (scheme == "https" && port == 443))
                ) {
                    ":$port"
                } else {
                    ""
                }
                val path = (uri.rawPath ?: "").trimEnd('/')
                "$scheme://$host$portSuffix$path"
            } catch (_: Exception) {
                ensured.trimEnd('/').lowercase()
            }
        }

        internal fun hostKey(serverUrl: String): String {
            val normalized = normalizeServerKey(serverUrl)
            if (normalized.isEmpty()) return ""
            return normalized.substringAfter("://", normalized)
        }
    }

    private fun prefKey(serverUrl: String): String? {
        val normalized = normalizeServerKey(serverUrl)
        if (normalized.isEmpty()) return null
        return "$KEY_PREFIX$normalized"
    }

    private fun findMatchingKeys(serverUrl: String): List<String> {
        val exactKey = prefKey(serverUrl) ?: return emptyList()
        val result = mutableListOf<String>()
        if (prefs.contains(exactKey)) {
            result.add(exactKey)
        }
        val targetHostKey = hostKey(serverUrl)
        if (targetHostKey.isNotEmpty()) {
            for (storedKey in prefs.all.keys) {
                if (!storedKey.startsWith(KEY_PREFIX) || storedKey == exactKey) continue
                val storedServer = storedKey.removePrefix(KEY_PREFIX)
                if (storedServer.substringAfter("://", storedServer) == targetHostKey) {
                    result.add(storedKey)
                }
            }
        }
        return result
    }

    @Synchronized
    fun getBackups(serverUrl: String): List<BackedUpAlbum> {
        val matchingKeys = findMatchingKeys(serverUrl)
        for (key in matchingKeys) {
            val parsedList = readBackupsFromKey(key)
            if (parsedList.isNotEmpty()) {
                return parsedList
            }
        }
        return emptyList()
    }

    private fun readBackupsFromKey(key: String): List<BackedUpAlbum> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        val parsed = try {
            listAdapter.fromJson(raw)
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        return parsed.mapNotNull { entry ->
            val name = (entry["name"] as? String)?.trim().orEmpty()
            @Suppress("UNCHECKED_CAST")
            val queryRaw = entry["searchQuery"] as? Map<String, Any?>
            if (name.isEmpty() || queryRaw.isNullOrEmpty()) {
                return@mapNotNull null
            }
            @Suppress("UNCHECKED_CAST")
            val cleanedQuery = cleanJsonValue(queryRaw) as? Map<String, Any?> ?: return@mapNotNull null
            val savedAtMs = (entry["savedAtMs"] as? Number)?.toLong() ?: 0L
            val lastKnownId = (entry["lastKnownId"] as? Number)?.toInt()
            BackedUpAlbum(
                name = name,
                searchQuery = cleanedQuery,
                savedAtMs = savedAtMs,
                lastKnownId = lastKnownId
            )
        }
    }

    /**
     * Copies backups from [fromServerUrl] to [toServerUrl] when the user reconnects using an
     * alternate address for the same server (e.g. switching between primary and local URLs),
     * provided [toServerUrl] does not already have its own snapshot.
     */
    @Synchronized
    fun migrateBackupsIfMissing(fromServerUrl: String, toServerUrl: String) {
        val targetKey = prefKey(toServerUrl) ?: return
        if (getBackups(toServerUrl).isNotEmpty()) return
        val sourceBackups = getBackups(fromServerUrl)
        if (sourceBackups.isEmpty()) return
        persistList(targetKey, sourceBackups)
    }

    /**
     * Replaces the stored backup for [serverUrl] with all unlocked saved-search albums currently
     * returned by the server. Should ONLY be called when the server returned at least one
     * unlocked album so that a server wipe/update (`0` unlocked albums) preserves the last snapshot.
     */
    @Synchronized
    fun replaceUnlockedSnapshot(
        serverUrl: String,
        albums: List<ApiAlbum>,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val key = prefKey(serverUrl) ?: return
        val unlocked = albums.filter { !it.locked && !it.searchQuery.isNullOrEmpty() && it.name.isNotBlank() }
        if (unlocked.isEmpty()) return

        val deduplicated = mutableListOf<BackedUpAlbum>()
        val seenKeys = HashSet<String>()
        for (album in unlocked) {
            val trimmedName = album.name.trim()
            @Suppress("UNCHECKED_CAST")
            val cleanedQuery = cleanJsonValue(album.searchQuery!!) as? Map<String, Any?> ?: continue
            if (cleanedQuery.isEmpty()) continue
            val dedupeKey = "$trimmedName|${canonicalQueryJson(cleanedQuery)}"
            if (seenKeys.add(dedupeKey)) {
                deduplicated.add(
                    BackedUpAlbum(
                        name = trimmedName,
                        searchQuery = cleanedQuery,
                        savedAtMs = nowMs,
                        lastKnownId = album.id.takeIf { it > 0 }
                    )
                )
            }
        }

        if (deduplicated.isNotEmpty()) {
            persistList(key, deduplicated)
            // Remove stale alias keys (e.g. old http:// when now on https://)
            for (aliasKey in findMatchingKeys(serverUrl)) {
                if (aliasKey != key) {
                    prefs.edit { remove(aliasKey) }
                }
            }
        }
    }

    @Synchronized
    fun upsertAlbum(
        serverUrl: String,
        name: String,
        searchQuery: Map<String, Any?>,
        albumId: Int? = null,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val key = prefKey(serverUrl) ?: return
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || searchQuery.isEmpty()) return
        @Suppress("UNCHECKED_CAST")
        val cleanedQuery = cleanJsonValue(searchQuery) as? Map<String, Any?> ?: return
        if (cleanedQuery.isEmpty()) return

        val targetQueryCanonical = canonicalQueryJson(cleanedQuery)
        val existing = getBackups(serverUrl).filterNot { item ->
            (albumId != null && albumId > 0 && item.lastKnownId == albumId) ||
                (item.name == trimmedName && canonicalQueryJson(item.searchQuery) == targetQueryCanonical)
        }
        val updated = existing + BackedUpAlbum(
            name = trimmedName,
            searchQuery = cleanedQuery,
            savedAtMs = nowMs,
            lastKnownId = albumId?.takeIf { it > 0 }
        )
        persistList(key, updated)
    }

    /**
     * Removes an album from the local backup after intentional in-app deletion.
     */
    @Synchronized
    fun removeAlbum(serverUrl: String, album: ApiAlbum) {
        val key = prefKey(serverUrl) ?: return
        val matchingKeys = findMatchingKeys(serverUrl)
        if (matchingKeys.isEmpty()) return

        val trimmedName = album.name.trim()
        val targetQueryCanonical = album.searchQuery?.let { canonicalQueryJson(it) }

        for (targetKey in matchingKeys) {
            val existing = readBackupsFromKey(targetKey)
            if (existing.isEmpty()) continue
            val remaining = existing.filterNot { item ->
                val idMatches = album.id > 0 && item.lastKnownId == album.id
                val nameMatches = item.name == trimmedName
                val queryMatches = targetQueryCanonical == null ||
                    canonicalQueryJson(item.searchQuery) == targetQueryCanonical
                idMatches || (nameMatches && queryMatches)
            }

            if (remaining.isEmpty()) {
                prefs.edit { remove(targetKey) }
            } else if (remaining.size != existing.size) {
                persistList(key, remaining)
                if (targetKey != key) {
                    prefs.edit { remove(targetKey) }
                }
            }
        }
    }

    @Synchronized
    fun clearBackups(serverUrl: String) {
        val matchingKeys = findMatchingKeys(serverUrl)
        if (matchingKeys.isEmpty()) return
        prefs.edit {
            for (k in matchingKeys) {
                remove(k)
            }
        }
    }

    @Synchronized
    fun clearAll() {
        prefs.edit { clear() }
    }

    private fun persistList(prefKey: String, items: List<BackedUpAlbum>) {
        val rawList = items.map { item ->
            buildMap<String, Any?> {
                put("name", item.name)
                put("searchQuery", cleanJsonValue(item.searchQuery))
                put("savedAtMs", item.savedAtMs)
                if (item.lastKnownId != null) {
                    put("lastKnownId", item.lastKnownId)
                }
            }
        }
        val json = listAdapter.toJson(rawList)
        prefs.edit { putString(prefKey, json) }
    }

    private fun canonicalQueryJson(query: Map<String, Any?>): String {
        @Suppress("UNCHECKED_CAST")
        val canonical = canonicalizeJsonValue(query) as? Map<String, Any?> ?: emptyMap()
        return mapAdapter.toJson(canonical)
    }

    private fun cleanJsonValue(value: Any?): Any? {
        return when (value) {
            is Double -> if (value % 1.0 == 0.0) value.toLong() else value
            is Map<*, *> -> {
                val result = linkedMapOf<String, Any?>()
                for ((k, v) in value) {
                    if (k != null) {
                        result[k.toString()] = cleanJsonValue(v)
                    }
                }
                result
            }
            is List<*> -> value.map { cleanJsonValue(it) }
            else -> value
        }
    }

    private fun canonicalizeJsonValue(value: Any?): Any? {
        return when (value) {
            is Double -> if (value % 1.0 == 0.0) value.toLong() else value
            is Int -> value.toLong()
            is Map<*, *> -> {
                val sorted = TreeMap<String, Any?>()
                for ((k, v) in value) {
                    if (k != null) {
                        sorted[k.toString()] = canonicalizeJsonValue(v)
                    }
                }
                sorted
            }
            is List<*> -> value.map { canonicalizeJsonValue(it) }
            else -> value
        }
    }
}
