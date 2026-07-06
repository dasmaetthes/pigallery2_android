with open('/app/applet/app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

old_code = """    val tokens = searchQuery.trimEnd().split(Regex("\\\\s+")).filter { it.isNotBlank() }"""
new_code = """    val tokens = com.example.data.search.SearchQueryParser.tokenize(searchQuery)"""

content = content.replace(old_code, new_code)

with open('/app/applet/app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
