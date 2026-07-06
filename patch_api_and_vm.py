import re

# 1. Patch PiGalleryApi.kt
with open('/app/applet/app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    api_content = f.read()

old_api_sig = "fun getAutocompleteSuggestions(serverUrl: String, text: String, cookies: String, apiPrefix: String): List<String> {"
new_api_sig = "fun getAutocompleteSuggestions(serverUrl: String, text: String, cookies: String, apiPrefix: String, type: Int = 100): List<String> {"
api_content = api_content.replace(old_api_sig, new_api_sig)

old_api_url = 'val endpoint = "$sanitizedUrl$apiPrefix/autocomplete/$encodedText?type=100"'
new_api_url = 'val endpoint = "$sanitizedUrl$apiPrefix/autocomplete/$encodedText?type=$type"'
api_content = api_content.replace(old_api_url, new_api_url)

with open('/app/applet/app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(api_content)


# 2. Patch GalleryViewModel.kt
with open('/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    vm_content = f.read()

old_fetch = """    fun fetchSearchSuggestions(text: String) {
        val lastToken = text.trimEnd().split(Regex("\\\\s+")).lastOrNull() ?: ""
        if (lastToken.isBlank()) {
            searchSuggestions.value = emptyList()
            return
        }
        
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val suggestions = api.getAutocompleteSuggestions(prefs.serverUrl, lastToken, prefs.cookies, prefs.apiPrefix)
                searchSuggestions.value = suggestions
            } catch (e: Exception) {
                // Ignore
            }
        }
    }"""

new_fetch = """    fun fetchSearchSuggestions(text: String) {
        val lastToken = text.trimEnd().split(Regex("\\\\s+")).lastOrNull() ?: ""
        if (lastToken.isBlank()) {
            searchSuggestions.value = emptyList()
            return
        }
        
        var searchType = 100 // ANY_TEXT
        var searchValue = lastToken
        val colonIndex = lastToken.indexOf(':')

        if (colonIndex > 0) {
            val prefix = lastToken.substring(0, colonIndex).lowercase()
            searchValue = lastToken.substring(colonIndex + 1)
            
            searchType = when (prefix) {
                "tag", "keyword" -> 104
                "person" -> 105
                "position", "place" -> 106
                "caption" -> 101
                "filename", "file-name" -> 103
                "directory", "folder" -> 102
                else -> 100
            }
        }
        
        if (searchValue.isBlank()) {
            searchSuggestions.value = emptyList()
            return
        }

        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val suggestions = api.getAutocompleteSuggestions(prefs.serverUrl, searchValue, prefs.cookies, prefs.apiPrefix, searchType)
                searchSuggestions.value = suggestions
            } catch (e: Exception) {
                // Ignore
            }
        }
    }"""

vm_content = vm_content.replace(old_fetch, new_fetch)

with open('/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(vm_content)
