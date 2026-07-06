with open('/app/applet/app/src/main/java/com/example/data/search/SearchQueryParser.kt', 'r') as f:
    content = f.read()

content = content.replace("companion object {", """companion object {
        fun tokenize(queryText: String): List<String> {
            return Regex(\"\"\"(?:[^\\s\"()]|\"[^\"]*\"|\\([^)]*\\))+\"\"\").findAll(queryText.trimEnd()).map { it.value }.toList()
        }""")

with open('/app/applet/app/src/main/java/com/example/data/search/SearchQueryParser.kt', 'w') as f:
    f.write(content)
