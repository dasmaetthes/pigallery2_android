with open('/app/applet/app/src/main/java/com/example/data/search/SearchQueryParser.kt', 'r') as f:
    content = f.read()

old_code = """class SearchQueryParser {
    companion object {
        fun parse"""

new_code = """class SearchQueryParser {
    companion object {
        fun tokenize(queryText: String): List<String> {
            return Regex(\"\"\"(?:[^\\s\"()]|\"[^\"]*\"|\\([^)]*\\))+\"\"\").findAll(queryText.trimEnd()).map { it.value }.toList()
        }

        fun parse"""

content = content.replace(old_code, new_code)

with open('/app/applet/app/src/main/java/com/example/data/search/SearchQueryParser.kt', 'w') as f:
    f.write(content)
