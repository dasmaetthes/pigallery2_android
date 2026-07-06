import re

with open('app/src/main/java/com/example/data/PreferencesManager.kt', 'r') as f:
    content = f.read()

new_prefs = """    fun getFolderSortBy(path: String = "global", fallback: String = "name"): String {
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

    var sortDirection: String"""

old_prefs = r"    fun getFolderSortBy.*?\n    var sortDirection: String"
content = re.sub(old_prefs, new_prefs, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/PreferencesManager.kt', 'w') as f:
    f.write(content)
