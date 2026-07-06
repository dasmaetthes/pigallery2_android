import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
"""        _albumContentState.value = null
        exitSelectMode()
    }

    private val _cacheSize = MutableStateFlow("0 B")""",
"""        _albumContentState.value = null
        exitSelectMode()
    }

    private val _cacheSize = MutableStateFlow("0 B")""") # wait, I added `}\n` before. Was it enough?
