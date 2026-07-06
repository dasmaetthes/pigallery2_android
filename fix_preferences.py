import re

with open('app/src/main/java/com/example/data/PreferencesManager.kt', 'r') as f:
    content = f.read()

const_old = """        private const val KEY_SERVER_URL = "server_url\""""
const_new = """        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_ALLOW_INSECURE_SSL = "allow_insecure_ssl\""""

if const_old in content:
    content = content.replace(const_old, const_new)
    print("Replaced const successfully")

getter_old = """    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()"""

getter_new = """    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var allowInsecureSsl: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_INSECURE_SSL, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_INSECURE_SSL, value).apply()"""

if getter_old in content:
    content = content.replace(getter_old, getter_new)
    print("Replaced getter successfully")

with open('app/src/main/java/com/example/data/PreferencesManager.kt', 'w') as f:
    f.write(content)
