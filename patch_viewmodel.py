import re

with open('/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

# 1. Update fetchSearchSuggestions
old_fetch = """    fun fetchSearchSuggestions(text: String) {
        if (text.isBlank()) {
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
        val lastToken = text.split(Regex("\\\\s+")).lastOrNull() ?: ""
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

# 2. Update setSearchQueryFromSuggestion and add appendPrefixToSearch / removeTokenFromSearch
old_set_query = """    fun setSearchQueryFromSuggestion(query: String) {
        searchQuery.value = query
        showSearchSuggestions.value = false
        loadCurrentDirectory()
    }"""

new_set_query = """    fun setSearchQueryFromSuggestion(suggestion: String) {
        val current = searchQuery.value
        val lastSpaceIndex = current.lastIndexOf(' ')
        val newQuery = if (lastSpaceIndex != -1) {
            current.substring(0, lastSpaceIndex + 1) + suggestion
        } else {
            suggestion
        }
        searchQuery.value = newQuery + " "
        searchSuggestions.value = emptyList()
    }

    fun appendPrefixToSearch(prefix: String) {
        val current = searchQuery.value
        val lastSpaceIndex = current.lastIndexOf(' ')
        val newQuery = if (lastSpaceIndex != -1) {
            current.substring(0, lastSpaceIndex + 1) + prefix
        } else {
            prefix
        }
        searchQuery.value = newQuery
        fetchSearchSuggestions(newQuery)
    }

    fun removeTokenFromSearch(tokenToRemove: String) {
        val current = searchQuery.value
        val tokens = current.trimEnd().split(Regex("\\\\s+")).toMutableList()
        val index = tokens.lastIndexOf(tokenToRemove)
        if (index != -1) {
            tokens.removeAt(index)
        }
        val newQuery = tokens.joinToString(" ") + if (tokens.isNotEmpty()) " " else ""
        searchQuery.value = newQuery
        fetchSearchSuggestions(newQuery)
    }"""
content = content.replace(old_set_query, new_set_query)

with open('/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)

