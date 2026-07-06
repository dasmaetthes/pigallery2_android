import re

with open('/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

# Fix quoting in suggestion
old_set = """    fun setSearchQueryFromSuggestion(suggestion: String) {
        val current = searchQuery.value
        val lastSpaceIndex = current.lastIndexOf(' ')
        val newQuery = if (lastSpaceIndex != -1) {
            current.substring(0, lastSpaceIndex + 1) + suggestion
        } else {
            suggestion
        }
        searchQuery.value = newQuery + " "
        searchSuggestions.value = emptyList()
    }"""

new_set = """    fun setSearchQueryFromSuggestion(suggestion: String) {
        val current = searchQuery.value
        
        var formattedSuggestion = suggestion
        val colonIndex = suggestion.indexOf(':')
        if (colonIndex > 0 && suggestion.contains(" ")) {
            val prefix = suggestion.substring(0, colonIndex + 1)
            val value = suggestion.substring(colonIndex + 1)
            formattedSuggestion = if (!value.startsWith("\\\"")) {
                "$prefix\\\"$value\\\""
            } else {
                suggestion
            }
        } else if (suggestion.contains(" ") && !suggestion.startsWith("\\\"")) {
            formattedSuggestion = "\\\"$suggestion\\\""
        }

        // Find the boundary of the last token to replace it
        val tokens = current.trimEnd().split(Regex("\\\\s+"))
        val lastToken = tokens.lastOrNull() ?: ""
        
        val newQuery = if (lastToken.isNotEmpty()) {
            val lastTokenIndex = current.lastIndexOf(lastToken)
            if (lastTokenIndex != -1) {
                current.substring(0, lastTokenIndex) + formattedSuggestion
            } else {
                formattedSuggestion
            }
        } else {
            current + formattedSuggestion
        }
        
        searchQuery.value = newQuery + " "
        searchSuggestions.value = emptyList()
    }"""
content = content.replace(old_set, new_set)


old_fetch = """    fun fetchSearchSuggestions(text: String) {
        val lastToken = text.trimEnd().split(Regex("\\\\s+")).lastOrNull() ?: ""
        if (lastToken.isBlank()) {
            searchSuggestions.value = emptyList()
            return
        }"""

new_fetch = """    fun fetchSearchSuggestions(text: String) {
        val lastToken = text.trimEnd().split(Regex("\\\\s+")).lastOrNull() ?: ""
        if (lastToken.isBlank() || lastToken.equals("and", ignoreCase = true) || lastToken.equals("or", ignoreCase = true)) {
            searchSuggestions.value = emptyList()
            return
        }"""
content = content.replace(old_fetch, new_fetch)

with open('/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
