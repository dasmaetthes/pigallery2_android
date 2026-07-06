with open('/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

old_code = """        if (searchValue.isBlank()) {
            searchSuggestions.value = emptyList()
            return
        }

        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val suggestions = api.getAutocompleteSuggestions(prefs.serverUrl, searchValue, prefs.cookies, prefs.apiPrefix, searchType)"""

new_code = """        var cleanValue = searchValue
        if (cleanValue.startsWith("\"")) cleanValue = cleanValue.substring(1)
        if (cleanValue.endsWith("\"")) cleanValue = cleanValue.substring(0, cleanValue.length - 1)
        if (cleanValue.startsWith("(")) cleanValue = cleanValue.substring(1)
        if (cleanValue.endsWith(")")) cleanValue = cleanValue.substring(0, cleanValue.length - 1)

        if (cleanValue.isBlank()) {
            searchSuggestions.value = emptyList()
            return
        }

        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val suggestions = api.getAutocompleteSuggestions(prefs.serverUrl, cleanValue, prefs.cookies, prefs.apiPrefix, searchType)"""

content = content.replace(old_code, new_code)

with open('/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
