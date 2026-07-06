import re

with open('/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

old_fetch = """    fun fetchSearchSuggestions(text: String) {
        if (text.trim().isEmpty()) {
            searchSuggestions.value = emptyList()
            return
        }

        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val suggestions = api.getAutocompleteSuggestions(prefs.serverUrl, text, prefs.cookies, prefs.apiPrefix)
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

content = content.replace(old_fetch, new_fetch)

with open('/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
