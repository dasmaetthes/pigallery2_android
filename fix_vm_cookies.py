import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

bad_load = """    fun loadPersons() {
        val server = prefs.serverUrl ?: run {
            _personsState.value = PersonsUiState.Error("No server configured.")
            return
        }
        val apiPrefix = prefs.apiPrefix
        _personsState.value = PersonsUiState.Loading"""

good_load = """    fun loadPersons() {
        val server = prefs.serverUrl ?: run {
            _personsState.value = PersonsUiState.Error("No server configured.")
            return
        }
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix
        _personsState.value = PersonsUiState.Loading"""

bad_content = """    private fun loadPersonContent(person: com.example.data.ApiPerson) {
        val server = prefs.serverUrl ?: return
        val apiPrefix = prefs.apiPrefix
        _personContentState.value = GalleryUiState.Loading"""

good_content = """    private fun loadPersonContent(person: com.example.data.ApiPerson) {
        val server = prefs.serverUrl ?: return
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix
        _personContentState.value = GalleryUiState.Loading"""

content = content.replace(bad_load, good_load)
content = content.replace(bad_content, good_content)

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
