package com.example.data.search

class SearchQueryParser {

    companion object {
        fun tokenize(queryText: String): List<String> {
            return Regex("""(?:[^\s"()]|"[^"]*(?:"|$)|\([^)]*(?:\)|$))+""").findAll(queryText.trimEnd()).map { it.value }.toList()
        }
        fun parse(queryText: String, implicitAND: Boolean = true): SearchQueryDTO {
            var text = queryText
                .replace(Regex("\\s\\s+"), " ")
                .replace(Regex(":\\s+"), ":")
                .trim()

            if (text.isEmpty()) {
                return TextSearch(SearchQueryTypes.ANY_TEXT, "")
            }

            if (text.startsWith("(") && text.endsWith(")")) {
                text = text.substring(1, text.length - 1)
            }

            fun firstSpace(start: Int = 0): Int {
                val bracketIn = mutableListOf<Int>()
                var quotationMark = false
                for (i in start until text.length) {
                    if (text[i] == '"') {
                        quotationMark = !quotationMark
                        continue
                    }
                    if (text[i] == '(') {
                        bracketIn.add(i)
                        continue
                    }
                    if (text[i] == ')') {
                        if (bracketIn.isNotEmpty()) bracketIn.removeAt(bracketIn.size - 1)
                        continue
                    }
                    if (!quotationMark && bracketIn.isEmpty() && text[i] == ' ') {
                        return i
                    }
                }
                return text.length - 1
            }

            val tokenEnd = firstSpace()
            if (tokenEnd != text.length - 1) {
                if (text.substring(tokenEnd).startsWith(" and ", ignoreCase = true)) {
                    val rest = parse(text.substring(tokenEnd + 5), implicitAND)
                    val left = parse(text.substring(0, tokenEnd), implicitAND)
                    return AndSearchQuery(listOf(left) + if (rest is SearchListQuery && rest.type == SearchQueryTypes.AND) rest.list else listOf(rest))
                } else if (text.substring(tokenEnd).startsWith(" or ", ignoreCase = true) || text.substring(tokenEnd).startsWith(" | ")) {
                    val splitLen = if (text.substring(tokenEnd).startsWith(" or ", ignoreCase = true)) 4 else 3
                    val rest = parse(text.substring(tokenEnd + splitLen), implicitAND)
                    val left = parse(text.substring(0, tokenEnd), implicitAND)
                    return OrSearchQuery(listOf(left) + if (rest is SearchListQuery && rest.type == SearchQueryTypes.OR) rest.list else listOf(rest))
                } else {
                    val t = if (implicitAND) SearchQueryTypes.AND else SearchQueryTypes.UNKNOWN_RELATION
                    val rest = parse(text.substring(tokenEnd).trim(), implicitAND)
                    val left = parse(text.substring(0, tokenEnd), implicitAND)
                    val list = listOf(left) + if (rest is SearchListQuery && rest.type == t) rest.list else listOf(rest)
                    return AndSearchQuery(list)
                }
            }

            // Single token parsing
            val rangeSearch = parseRangeQuery(text)
            if (rangeSearch != null) return rangeSearch

            for (type in SearchQueryTypes.TEXT_SEARCH_TYPES) {
                if (type == SearchQueryTypes.ANY_TEXT) continue
                
                val keyword = when(type) {
                    SearchQueryTypes.KEYWORD -> "keyword"
                    SearchQueryTypes.PERSON -> "person"
                    SearchQueryTypes.POSITION -> "position"
                    SearchQueryTypes.CAPTION -> "caption"
                    SearchQueryTypes.FILE_NAME -> "file-name"
                    SearchQueryTypes.DIRECTORY -> "directory"
                    else -> ""
                }
                
                val aliases = mutableListOf("$keyword:")
                if (type == SearchQueryTypes.KEYWORD) aliases.add("tag:")
                if (type == SearchQueryTypes.POSITION) aliases.add("place:")
                if (type == SearchQueryTypes.FILE_NAME) aliases.add("filename:")
                if (type == SearchQueryTypes.DIRECTORY) aliases.add("folder:")

                for (alias in aliases) {
                    val isNegated = text.startsWith("-$alias") || text.startsWith("!$alias")
                    val prefixLen = if (isNegated) alias.length + 1 else alias.length
                    
                    if ((!isNegated && text.startsWith(alias, ignoreCase = true)) || 
                        (isNegated && text.substring(1).startsWith(alias, ignoreCase = true))) {
                        
                        val afterKey = text.substring(prefixLen)
                        if (afterKey.isNotEmpty() && afterKey.startsWith("\"") && afterKey.endsWith("\"")) {
                            return TextSearch(type, afterKey.substring(1, afterKey.length - 1), TextSearchQueryMatchTypes.EXACT_MATCH, isNegated)
                        } else if (afterKey.isNotEmpty() && afterKey.startsWith("(") && afterKey.endsWith(")")) {
                            return TextSearch(type, afterKey.substring(1, afterKey.length - 1), TextSearchQueryMatchTypes.LIKE, isNegated)
                        } else {
                            return TextSearch(type, afterKey, TextSearchQueryMatchTypes.LIKE, isNegated)
                        }
                    }
                }
            }

            val isNegated = text.startsWith("-") && text.length > 1
            val value = if (isNegated) text.substring(1) else text
            
            if (value.startsWith("\"") && value.endsWith("\"") && value.length > 1) {
                return TextSearch(SearchQueryTypes.ANY_TEXT, value.substring(1, value.length - 1), TextSearchQueryMatchTypes.EXACT_MATCH, isNegated)
            }
            
            return TextSearch(SearchQueryTypes.ANY_TEXT, value, TextSearchQueryMatchTypes.LIKE, isNegated)
        }

        private fun parseRangeQuery(text: String): RangeSearch? {
            // Simplified range query parsing (date, rating, resolution, person_count)
            // Example: rating:4..6, rating:4, rating>3
            val keywords = listOf(
                Pair(SearchQueryTypes.DATE, "date"),
                Pair(SearchQueryTypes.RATING, "rating"),
                Pair(SearchQueryTypes.RESOLUTION, "resolution"),
                Pair(SearchQueryTypes.PERSON_COUNT, "person-count")
            )

            for ((type, keyword) in keywords) {
                val regex = Regex("^$keyword(!?[:=]|!?[<>]=?)(\\d+)(?:\\.\\.(\\d+))?\$", RegexOption.IGNORE_CASE)
                val match = regex.find(text)
                if (match != null) {
                    val relation = match.groupValues[1]
                    val rawA = match.groupValues[2]
                    val rawB = if (match.groupValues.size > 3 && match.groupValues[3].isNotEmpty()) match.groupValues[3] else null

                    val a = rawA.toLongOrNull() ?: 0L
                    val b = rawB?.toLongOrNull()

                    var negate = false
                    var rel = relation
                    if (rel.startsWith("!")) {
                        negate = true
                        rel = rel.substring(1)
                    }

                    when (rel) {
                        ":", "=" -> {
                            return if (b == null) RangeSearch(type, min = a, max = a, negate = negate)
                            else RangeSearch(type, min = a, max = b, negate = negate)
                        }
                        ">=" -> return RangeSearch(type, min = a, negate = negate)
                        ">" -> return RangeSearch(type, min = a + 1, negate = negate)
                        "<=" -> return RangeSearch(type, max = a, negate = negate)
                        "<" -> return RangeSearch(type, max = a - 1, negate = negate)
                    }
                }
            }
            return null
        }
    }
}
