import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

# I will replace all '        exitSelectMode()\n    }' back to '        exitSelectMode()'
content = content.replace("        exitSelectMode()\n    }", "        exitSelectMode()")

# Now I'll only add it where we actually need it:
#        _albumContentState.value = null
#        exitSelectMode()
#    private val _cacheSize = MutableStateFlow("0 B")
content = content.replace(
"""        _albumContentState.value = null
        exitSelectMode()
    private val _cacheSize""",
"""        _albumContentState.value = null
        exitSelectMode()
    }

    private val _cacheSize""")

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
