import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

# I see it's missing the closing brace for the function before it.
# Wait, look at:
#        _albumContentState.value = null
#        exitSelectMode()
#    private val _cacheSize = MutableStateFlow("0 B")

# It should be:
#        _albumContentState.value = null
#        exitSelectMode()
#    }
#
#    private val _cacheSize = MutableStateFlow("0 B")

content = content.replace("        exitSelectMode()\n    private val _cacheSize", "        exitSelectMode()\n    }\n\n    private val _cacheSize")

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
