import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

ui_state = """
sealed interface PersonsUiState {
    object Loading : PersonsUiState
    data class Success(val persons: List<com.example.data.ApiPerson>) : PersonsUiState
    data class Error(val message: String) : PersonsUiState
}
"""

if "sealed interface PersonsUiState" not in content:
    idx = content.find("sealed interface AlbumsUiState")
    if idx != -1:
        content = content[:idx] + ui_state + "\n" + content[idx:]

state_vars = """
    private val _personsState = MutableStateFlow<PersonsUiState>(PersonsUiState.Loading)
    val personsState: StateFlow<PersonsUiState> = _personsState.asStateFlow()

    private val _selectedPerson = MutableStateFlow<com.example.data.ApiPerson?>(null)
    val selectedPerson: StateFlow<com.example.data.ApiPerson?> = _selectedPerson.asStateFlow()

    private val _personContentState = MutableStateFlow<GalleryUiState?>(null)
    val personContentState: StateFlow<GalleryUiState?> = _personContentState.asStateFlow()
"""

if "val personsState" not in content:
    idx = content.find("val albumsState: StateFlow<AlbumsUiState>")
    if idx != -1:
        end_of_line = content.find("\n", idx)
        content = content[:end_of_line+1] + state_vars + "\n" + content[end_of_line+1:]

fetch_method = """
    // --- Persons Functionality ---
    fun loadPersons() {
        val server = prefs.serverUrl ?: run {
            _personsState.value = PersonsUiState.Error("No server configured.")
            return
        }
        val apiPrefix = prefs.apiPrefix
        _personsState.value = PersonsUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val persons = api.getPersons(server, cookies, apiPrefix)
                _personsState.value = PersonsUiState.Success(persons)
            } catch (e: Exception) {
                _personsState.value = PersonsUiState.Error(e.localizedMessage ?: "Failed to fetch persons")
            }
        }
    }
    
    fun selectPerson(person: com.example.data.ApiPerson) {
        _selectedPerson.value = person
        loadPersonContent(person)
    }

    fun clearSelectedPerson() {
        _selectedPerson.value = null
        _personContentState.value = null
    }

    private fun loadPersonContent(person: com.example.data.ApiPerson) {
        val server = prefs.serverUrl ?: return
        val apiPrefix = prefs.apiPrefix
        _personContentState.value = GalleryUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val searchQuery = mapOf("type" to 105, "text" to person.name, "matchType" to 1)
                val moshi = com.squareup.moshi.Moshi.Builder().build()
                val type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val adapter = moshi.adapter<Map<String, Any>>(type)
                val searchQueryJson = adapter.toJson(searchQuery)
                val dummyDir = api.search(server, searchQueryJson, cookies, apiPrefix)
                _personContentState.value = GalleryUiState.Success(dummyDir)
            } catch (e: Exception) {
                _personContentState.value = GalleryUiState.Error(e.localizedMessage ?: "Failed to fetch person content")
            }
        }
    }
"""

if "fun loadPersons()" not in content:
    idx = content.find("fun loadAlbums()")
    if idx != -1:
        content = content[:idx] + fetch_method + "\n" + content[idx:]

# Also ActiveTab needs PERSONS
if "PERSONS," not in content:
    idx = content.find("ALBUMS,")
    if idx != -1:
        content = content[:idx+7] + "\n    PERSONS," + content[idx+7:]

# And load persons on start if not already done, wait, active tab change is the best place
if "ActiveTab.PERSONS -> loadPersons()" not in content:
    idx = content.find("ActiveTab.ALBUMS -> loadAlbums()")
    if idx != -1:
        content = content.replace("ActiveTab.ALBUMS -> loadAlbums()", "ActiveTab.ALBUMS -> loadAlbums()\n            ActiveTab.PERSONS -> loadPersons()")

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
