with open('/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'val tokens = current.trimEnd().split(Regex("\\\\s+"))',
    'val tokens = com.example.data.search.SearchQueryParser.tokenize(current)'
)

content = content.replace(
    'val tokens = current.trimEnd().split(Regex("\\\\s+")).toMutableList()',
    'val tokens = com.example.data.search.SearchQueryParser.tokenize(current).toMutableList()'
)

content = content.replace(
    'val lastToken = text.trimEnd().split(Regex("\\\\s+")).lastOrNull() ?: ""',
    'val lastToken = com.example.data.search.SearchQueryParser.tokenize(text).lastOrNull() ?: ""'
)

with open('/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
