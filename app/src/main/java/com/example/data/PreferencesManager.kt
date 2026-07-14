package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pigallery_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_ALLOW_INSECURE_SSL = "allow_insecure_ssl"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_COOKIES = "cookies"
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
        private const val KEY_SORT_BY = "sort_by"
        private const val KEY_SORT_DIRECTION = "sort_direction"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PEOPLE_FALLBACK_TO_KEYWORDS = "people_fallback_to_keywords"
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var allowInsecureSsl: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_INSECURE_SSL, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_INSECURE_SSL, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var cookies: String
        get() = prefs.getString(KEY_COOKIES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COOKIES, value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var apiPrefix: String
        get() = prefs.getString(KEY_API_PREFIX, "/api") ?: "/api"
        set(value) = prefs.edit().putString(KEY_API_PREFIX, value).apply()

    var rediscoverDays: Int
        get() = prefs.getInt(KEY_REDISCOVER_DAYS, 1)
        set(value) = prefs.edit().putInt(KEY_REDISCOVER_DAYS, value).apply()

    var slideshowDuration: Int
        get() = prefs.getInt(KEY_SLIDESHOW_DURATION, 3)
        set(value) = prefs.edit().putInt(KEY_SLIDESHOW_DURATION, value).apply()

    var thumbnailPathSuffix: String
        get() = prefs.getString(KEY_THUMBNAIL_PATH_SUFFIX, "320") ?: "320"
        set(value) = prefs.edit().putString(KEY_THUMBNAIL_PATH_SUFFIX, value).apply()

    var preloadPathSuffix: String
        get() = prefs.getString(KEY_PRELOAD_PATH_SUFFIX, "720") ?: "720"
        set(value) = prefs.edit().putString(KEY_PRELOAD_PATH_SUFFIX, value).apply()

    var videoPathSuffix: String
        get() = prefs.getString(KEY_VIDEO_PATH_SUFFIX, "") ?: ""
        set(value) = prefs.edit().putString(KEY_VIDEO_PATH_SUFFIX, value).apply()

    var showDirectoryItemCount: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DIR_ITEM_COUNT, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_DIR_ITEM_COUNT, value).apply()

    var itemsPerRow: Int
        get() = prefs.getInt(KEY_ITEMS_PER_ROW, 3)
        set(value) = prefs.edit().putInt(KEY_ITEMS_PER_ROW, value).apply()

    var itemsPerRowPortrait: Int
        get() = prefs.getInt(KEY_ITEMS_PER_ROW_PORTRAIT, 3)
        set(value) = prefs.edit().putInt(KEY_ITEMS_PER_ROW_PORTRAIT, value).apply()

    var itemsPerRowLandscape: Int
        get() = prefs.getInt(KEY_ITEMS_PER_ROW_LANDSCAPE, 6)
        set(value) = prefs.edit().putInt(KEY_ITEMS_PER_ROW_LANDSCAPE, value).apply()

    var cornerRadius: Int
        get() = prefs.getInt(KEY_CORNER_RADIUS, 5)
        set(value) = prefs.edit().putInt(KEY_CORNER_RADIUS, value).apply()

    var spacing: Int
        get() = prefs.getInt(KEY_SPACING, 5)
        set(value) = prefs.edit().putInt(KEY_SPACING, value).apply()

    var aspectRatio: Float
        get() = prefs.getFloat(KEY_ASPECT_RATIO, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_ASPECT_RATIO, value).apply()

    fun getFolderSortBy(path: String = "global", fallback: String = "name"): String {
        return prefs.getString("folder_sort_by_$path", null) ?: fallback
    }
    fun setFolderSortBy(path: String = "global", value: String) {
        prefs.edit().putString("folder_sort_by_$path", value).apply()
    }

    fun getFolderSortDirection(path: String = "global", fallback: String = "asc"): String {
        return prefs.getString("folder_sort_direction_$path", null) ?: fallback
    }
    fun setFolderSortDirection(path: String = "global", value: String) {
        prefs.edit().putString("folder_sort_direction_$path", value).apply()
    }

    fun getMediaSortBy(path: String = "global", fallback: String = "date"): String {
        return prefs.getString("media_sort_by_$path", null) ?: fallback
    }
    fun setMediaSortBy(path: String = "global", value: String) {
        prefs.edit().putString("media_sort_by_$path", value).apply()
    }

    fun getMediaSortDirection(path: String = "global", fallback: String = "asc"): String {
        return prefs.getString("media_sort_direction_$path", null) ?: fallback
    }
    fun setMediaSortDirection(path: String = "global", value: String) {
        prefs.edit().putString("media_sort_direction_$path", value).apply()
    }

    var sortDirection: String
        get() = prefs.getString(KEY_SORT_DIRECTION, "asc") ?: "asc"
        set(value) = prefs.edit().putString(KEY_SORT_DIRECTION, value).apply()

    var themeColor: String
        get() = prefs.getString(KEY_THEME_COLOR, "Purple") ?: "Purple"
        set(value) = prefs.edit().putString(KEY_THEME_COLOR, value).apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "Auto") ?: "Auto"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var peopleFallbackToKeywords: Boolean
        get() = prefs.getBoolean(KEY_PEOPLE_FALLBACK_TO_KEYWORDS, false)
        set(value) = prefs.edit().putBoolean(KEY_PEOPLE_FALLBACK_TO_KEYWORDS, value).apply()

    fun getFavoritePersons(): Set<String> {
        return prefs.getStringSet("favorite_persons", emptySet()) ?: emptySet()
    }

    fun setFavoritePerson(name: String, isFavourite: Boolean) {
        val current = getFavoritePersons().toMutableSet()
        if (isFavourite) {
            current.add(name)
        } else {
            current.remove(name)
        }
        prefs.edit().putStringSet("favorite_persons", current).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
