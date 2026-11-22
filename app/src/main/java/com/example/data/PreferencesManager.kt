package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pigallery_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_LOCAL_SERVER_URL = "local_server_url"
        private const val KEY_ALLOW_INSECURE_SSL = "allow_insecure_ssl"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_CSRF_TOKEN = "csrf_token"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_ALBUM_WRITE_MIN_ROLE = "album_write_min_role"
        private const val KEY_ALBUM_FEATURE_ENABLED = "album_feature_enabled"
        private const val KEY_USER_CAN_CREATE_ALBUMS = "user_can_create_albums"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_API_PREFIX = "api_prefix"
        private const val KEY_REDISCOVER_DAYS = "rediscover_days"
        private const val KEY_SLIDESHOW_DURATION = "slideshow_duration"
        
        // New configs
        private const val KEY_THUMBNAIL_PATH_SUFFIX = "thumbnail_path_suffix"
        private const val KEY_PRELOAD_PATH_SUFFIX = "preload_path_suffix"
        private const val KEY_VIDEO_PATH_SUFFIX = "video_path_suffix"
        private const val KEY_SHOW_DIR_ITEM_COUNT = "show_dir_item_count"
        private const val KEY_ITEMS_PER_ROW = "items_per_row"
        private const val KEY_ITEMS_PER_ROW_PORTRAIT = "items_per_row_portrait"
        private const val KEY_ITEMS_PER_ROW_LANDSCAPE = "items_per_row_landscape"
        private const val KEY_CORNER_RADIUS = "corner_radius"
        private const val KEY_SPACING = "spacing"
        private const val KEY_ASPECT_RATIO = "aspect_ratio"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PEOPLE_FALLBACK_TO_KEYWORDS = "people_fallback_to_keywords"
        private const val KEY_DEFAULT_ROOT_PATH = "default_root_path"
        private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_MAX_BRIGHTNESS_ENABLED = "max_brightness_enabled"
        private const val KEY_GALLERY_VIEW_MODE = "gallery_view_mode"
        private const val KEY_DISMISS_GESTURE_ENABLED = "dismiss_gesture_enabled"
        private const val KEY_SHOW_METADATA_GESTURE_ENABLED = "show_metadata_gesture_enabled"
        private const val KEY_BIOMETRIC_LOCK_ENABLED = "biometric_lock_enabled"
        private const val KEY_PROTECTED_FOLDERS = "protected_folders"
        private const val KEY_HIDE_PROTECTED_ITEMS = "hide_protected_items"
    }

    var galleryViewMode: String
        get() = prefs.getString(KEY_GALLERY_VIEW_MODE, "FOLDER") ?: "FOLDER"
        set(value) = prefs.edit { putString(KEY_GALLERY_VIEW_MODE, value) }

    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_CHECK_UPDATES, value) }

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_UPDATE_CHECK, value) }

    var maxBrightnessEnabled: Boolean
        get() = prefs.getBoolean(KEY_MAX_BRIGHTNESS_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_MAX_BRIGHTNESS_ENABLED, value) }

    var defaultRootPath: String
        get() = prefs.getString(KEY_DEFAULT_ROOT_PATH, "") ?: ""
        set(value) = prefs.edit { putString(KEY_DEFAULT_ROOT_PATH, value) }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit { putString(KEY_SERVER_URL, value) }

    var localServerUrl: String
        get() = prefs.getString(KEY_LOCAL_SERVER_URL, "") ?: ""
        set(value) = prefs.edit { putString(KEY_LOCAL_SERVER_URL, value) }

    var allowInsecureSsl: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_INSECURE_SSL, false)
        set(value) = prefs.edit { putBoolean(KEY_ALLOW_INSECURE_SSL, value) }

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit { putString(KEY_USERNAME, value) }

    private fun getSecureString(key: String): String {
        val raw = prefs.getString(key, null) ?: return ""
        val (plain, needsMigration) = com.example.util.SecurePrefsCipher.decrypt(raw)
        if (needsMigration && plain.isNotEmpty()) {
            val encrypted = com.example.util.SecurePrefsCipher.encrypt(plain)
            if (encrypted.isNotEmpty()) {
                prefs.edit { putString(key, encrypted) }
            }
        }
        return plain
    }

    private fun setSecureString(key: String, value: String) {
        prefs.edit {
            if (value.isEmpty()) {
                remove(key)
            } else {
                putString(key, com.example.util.SecurePrefsCipher.encrypt(value))
            }
        }
    }

    var password: String
        get() = getSecureString(KEY_PASSWORD)
        set(value) = setSecureString(KEY_PASSWORD, value)

    var cookies: String
        get() = getSecureString(KEY_COOKIES)
        set(value) = setSecureString(KEY_COOKIES, value)

    var csrfToken: String
        get() = getSecureString(KEY_CSRF_TOKEN)
        set(value) = setSecureString(KEY_CSRF_TOKEN, value)

    var userRole: Int
        get() = prefs.getInt(KEY_USER_ROLE, -1)
        set(value) = prefs.edit { putInt(KEY_USER_ROLE, value) }

    var albumWriteMinRole: Int
        get() = prefs.getInt(KEY_ALBUM_WRITE_MIN_ROLE, UserRoles.ADMIN)
        set(value) = prefs.edit { putInt(KEY_ALBUM_WRITE_MIN_ROLE, value) }

    var albumFeatureEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALBUM_FEATURE_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_ALBUM_FEATURE_ENABLED, value) }

    /**
     * Tri-state per-user album creation override from server user payload:
     * -1 = not specified (use albumWriteMinRole), 1 = explicitly allowed, 0 = explicitly denied.
     */
    var userCanCreateAlbumsOverride: Int
        get() = prefs.getInt(KEY_USER_CAN_CREATE_ALBUMS, -1)
        set(value) = prefs.edit { putInt(KEY_USER_CAN_CREATE_ALBUMS, value) }

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit { putBoolean(KEY_IS_LOGGED_IN, value) }

    var apiPrefix: String
        get() = prefs.getString(KEY_API_PREFIX, "/pgapi") ?: "/pgapi"
        set(value) = prefs.edit { putString(KEY_API_PREFIX, value) }

    var rediscoverDays: Int
        get() = prefs.getInt(KEY_REDISCOVER_DAYS, 1)
        set(value) = prefs.edit { putInt(KEY_REDISCOVER_DAYS, value) }

    var slideshowDuration: Int
        get() = prefs.getInt(KEY_SLIDESHOW_DURATION, 3)
        set(value) = prefs.edit { putInt(KEY_SLIDESHOW_DURATION, value) }

    var thumbnailPathSuffix: String
        get() = prefs.getString(KEY_THUMBNAIL_PATH_SUFFIX, "320") ?: "320"
        set(value) = prefs.edit { putString(KEY_THUMBNAIL_PATH_SUFFIX, value) }

    var preloadPathSuffix: String
        get() = prefs.getString(KEY_PRELOAD_PATH_SUFFIX, "720") ?: "720"
        set(value) = prefs.edit { putString(KEY_PRELOAD_PATH_SUFFIX, value) }

    var videoPathSuffix: String
        get() = prefs.getString(KEY_VIDEO_PATH_SUFFIX, "") ?: ""
        set(value) = prefs.edit { putString(KEY_VIDEO_PATH_SUFFIX, value) }

    var showDirectoryItemCount: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DIR_ITEM_COUNT, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_DIR_ITEM_COUNT, value) }

    var itemsPerRow: Int
        get() = prefs.getInt(KEY_ITEMS_PER_ROW, 3)
        set(value) = prefs.edit { putInt(KEY_ITEMS_PER_ROW, value) }

    var itemsPerRowPortrait: Int
        get() = prefs.getInt(KEY_ITEMS_PER_ROW_PORTRAIT, 3)
        set(value) = prefs.edit { putInt(KEY_ITEMS_PER_ROW_PORTRAIT, value) }

    var itemsPerRowLandscape: Int
        get() = prefs.getInt(KEY_ITEMS_PER_ROW_LANDSCAPE, 6)
        set(value) = prefs.edit { putInt(KEY_ITEMS_PER_ROW_LANDSCAPE, value) }

    var cornerRadius: Int
        get() = prefs.getInt(KEY_CORNER_RADIUS, 5)
        set(value) = prefs.edit { putInt(KEY_CORNER_RADIUS, value) }

    var spacing: Int
        get() = prefs.getInt(KEY_SPACING, 5)
        set(value) = prefs.edit { putInt(KEY_SPACING, value) }

    var aspectRatio: Float
        get() = prefs.getFloat(KEY_ASPECT_RATIO, 1.0f)
        set(value) = prefs.edit { putFloat(KEY_ASPECT_RATIO, value) }

    fun getFolderSortBy(path: String = "global", fallback: String = "name"): String {
        return prefs.getString("folder_sort_by_$path", null) ?: fallback
    }
    fun setFolderSortBy(path: String = "global", value: String) {
        prefs.edit { putString("folder_sort_by_$path", value) }
    }

    fun getFolderSortDirection(path: String = "global", fallback: String = "asc"): String {
        return prefs.getString("folder_sort_direction_$path", null) ?: fallback
    }
    fun setFolderSortDirection(path: String = "global", value: String) {
        prefs.edit { putString("folder_sort_direction_$path", value) }
    }

    fun hasFolderSort(path: String): Boolean {
        return prefs.contains("folder_sort_by_$path")
    }

    fun removeFolderSort(path: String) {
        prefs.edit {
            remove("folder_sort_by_$path")
            remove("folder_sort_direction_$path")
        }
    }

    fun getMediaSortBy(path: String = "global", fallback: String = "date"): String {
        return prefs.getString("media_sort_by_$path", null) ?: fallback
    }
    fun setMediaSortBy(path: String = "global", value: String) {
        prefs.edit { putString("media_sort_by_$path", value) }
    }

    fun getMediaSortDirection(path: String = "global", fallback: String = "asc"): String {
        return prefs.getString("media_sort_direction_$path", null) ?: fallback
    }
    fun setMediaSortDirection(path: String = "global", value: String) {
        prefs.edit { putString("media_sort_direction_$path", value) }
    }

    fun hasMediaSort(path: String): Boolean {
        return prefs.contains("media_sort_by_$path")
    }

    fun removeMediaSort(path: String) {
        prefs.edit {
            remove("media_sort_by_$path")
            remove("media_sort_direction_$path")
        }
    }

    // Dedicated Album Sorting (completely independent from folder sort)
    fun getAlbumSortBy(fallback: String = "name"): String {
        return prefs.getString("album_sort_by", null) ?: fallback
    }
    fun setAlbumSortBy(value: String) {
        prefs.edit { putString("album_sort_by", value) }
    }

    fun getAlbumSortDirection(fallback: String = "asc"): String {
        return prefs.getString("album_sort_direction", null) ?: fallback
    }
    fun setAlbumSortDirection(value: String) {
        prefs.edit { putString("album_sort_direction", value) }
    }

    // Dedicated Person Sorting (completely independent from folder sort)
    fun getPersonSortBy(fallback: String = "name"): String {
        return prefs.getString("person_sort_by", null) ?: fallback
    }
    fun setPersonSortBy(value: String) {
        prefs.edit { putString("person_sort_by", value) }
    }

    fun getPersonSortDirection(fallback: String = "asc"): String {
        return prefs.getString("person_sort_direction", null) ?: fallback
    }
    fun setPersonSortDirection(value: String) {
        prefs.edit { putString("person_sort_direction", value) }
    }

    var themeColor: String
        get() = prefs.getString(KEY_THEME_COLOR, "Purple") ?: "Purple"
        set(value) = prefs.edit { putString(KEY_THEME_COLOR, value) }

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "Auto") ?: "Auto"
        set(value) = prefs.edit { putString(KEY_THEME_MODE, value) }

    var peopleFallbackToKeywords: Boolean
        get() = prefs.getBoolean(KEY_PEOPLE_FALLBACK_TO_KEYWORDS, false)
        set(value) = prefs.edit { putBoolean(KEY_PEOPLE_FALLBACK_TO_KEYWORDS, value) }

    fun getFavoritePersons(): Set<String> {
        val set = prefs.getStringSet("favorite_persons", emptySet()) ?: emptySet()
        return HashSet(set)
    }

    fun setFavoritePerson(name: String, isFavourite: Boolean) {
        val current = getFavoritePersons().toMutableSet()
        if (isFavourite) {
            current.add(name)
        } else {
            current.remove(name)
        }
        prefs.edit { putStringSet("favorite_persons", current) }
    }

    var dismissGestureEnabled: Boolean
        get() = prefs.getBoolean(KEY_DISMISS_GESTURE_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_DISMISS_GESTURE_ENABLED, value) }

    var showMetadataGestureEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHOW_METADATA_GESTURE_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_METADATA_GESTURE_ENABLED, value) }

    var biometricLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_LOCK_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_BIOMETRIC_LOCK_ENABLED, value) }

    var hideProtectedItems: Boolean
        get() = prefs.getBoolean(KEY_HIDE_PROTECTED_ITEMS, false)
        set(value) = prefs.edit { putBoolean(KEY_HIDE_PROTECTED_ITEMS, value) }

    fun getProtectedFolders(): Set<String> {
        val set = prefs.getStringSet(KEY_PROTECTED_FOLDERS, emptySet()) ?: emptySet()
        return HashSet(set)
    }

    private fun cleanFolderPath(path: String): String {
        return path.replace('\\', '/').trim().trim('/').let {
            if (it == "." || it == "./") "" else it.removePrefix("./").trim('/')
        }
    }

    fun toggleProtectedFolder(path: String) {
        val clean = cleanFolderPath(path)
        if (clean.isEmpty()) return
        val current = getProtectedFolders().toMutableSet()
        val existing = current.firstOrNull { cleanFolderPath(it).equals(clean, ignoreCase = true) }
        if (existing != null) {
            current.remove(existing)
        } else {
            current.add(clean)
        }
        prefs.edit { putStringSet(KEY_PROTECTED_FOLDERS, current) }
    }

    fun isFolderDirectlyProtected(path: String): Boolean {
        val clean = cleanFolderPath(path)
        if (clean.isEmpty()) return false
        return getProtectedFolders().any {
            cleanFolderPath(it).equals(clean, ignoreCase = true)
        }
    }

    fun isFolderProtected(path: String): Boolean {
        val clean = cleanFolderPath(path)
        if (clean.isEmpty()) return false

        val protectedSet = getProtectedFolders()
        if (protectedSet.isEmpty()) return false

        return protectedSet.any { protectedPath ->
            val p = cleanFolderPath(protectedPath)
            if (p.isEmpty()) return@any false

            // 1. Direct folder match: this exact folder is locked
            if (clean.equals(p, ignoreCase = true)) {
                return@any true
            }

            // 2. Subfolder inheritance: any subfolder nested inside the locked folder is protected
            if (clean.startsWith("$p/", ignoreCase = true)) {
                return@any true
            }

            // 3. Nested relative match: if p was stored as a relative path segment, e.g. "Secret" and clean is "Photos/Secret/2024"
            if (clean.endsWith("/$p", ignoreCase = true) || clean.contains("/$p/", ignoreCase = true)) {
                return@any true
            }

            false
        }
    }

    fun clearSession() {
        prefs.edit {
            remove(KEY_IS_LOGGED_IN)
            remove(KEY_COOKIES)
            remove(KEY_CSRF_TOKEN)
            remove(KEY_PASSWORD)
            remove(KEY_USER_ROLE)
            remove(KEY_ALBUM_WRITE_MIN_ROLE)
            remove(KEY_ALBUM_FEATURE_ENABLED)
            remove(KEY_USER_CAN_CREATE_ALBUMS)
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }
}
