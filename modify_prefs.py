import re

with open('app/src/main/java/com/example/data/PreferencesManager.kt', 'r') as f:
    content = f.read()

new_prefs = """    fun getFolderSortBy(path: String = "global"): String = prefs.getString("folder_sort_by_$path", "name") ?: "name"
    fun setFolderSortBy(path: String = "global", value: String) = prefs.edit().putString("folder_sort_by_$path", value).apply()

    fun getFolderSortDirection(path: String = "global"): String = prefs.getString("folder_sort_direction_$path", "asc") ?: "asc"
    fun setFolderSortDirection(path: String = "global", value: String) = prefs.edit().putString("folder_sort_direction_$path", value).apply()

    fun getMediaSortBy(path: String = "global"): String = prefs.getString("media_sort_by_$path", "date") ?: "date"
    fun setMediaSortBy(path: String = "global", value: String) = prefs.edit().putString("media_sort_by_$path", value).apply()

    fun getMediaSortDirection(path: String = "global"): String = prefs.getString("media_sort_direction_$path", "asc") ?: "asc"
    fun setMediaSortDirection(path: String = "global", value: String) = prefs.edit().putString("media_sort_direction_$path", value).apply()

    var sortBy: String"""

content = content.replace("    var sortBy: String", new_prefs)

with open('app/src/main/java/com/example/data/PreferencesManager.kt', 'w') as f:
    f.write(content)
