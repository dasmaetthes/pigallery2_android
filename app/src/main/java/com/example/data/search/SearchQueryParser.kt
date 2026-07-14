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

        private fun parseDateToMillis(dateStr: String, isEndOfRange: Boolean): Long? {
            val trimmed = dateStr.trim()
            // Try YYYY-MM-DD
            val ymdRegex = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
            val ymdMatch = ymdRegex.matchEntire(trimmed)
            if (ymdMatch != null) {
                val y = ymdMatch.groupValues[1].toInt()
                val m = ymdMatch.groupValues[2].toInt() - 1 // 0-based in Calendar
                val d = ymdMatch.groupValues[3].toInt()
                val cal = java.util.Calendar.getInstance()
                cal.clear()
                cal.set(java.util.Calendar.YEAR, y)
                cal.set(java.util.Calendar.MONTH, m)
                cal.set(java.util.Calendar.DAY_OF_MONTH, d)
                if (isEndOfRange) {
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                    cal.set(java.util.Calendar.MINUTE, 59)
                    cal.set(java.util.Calendar.SECOND, 59)
                    cal.set(java.util.Calendar.MILLISECOND, 999)
                } else {
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis
            }

            // Try YYYY-MM
            val ymRegex = Regex("^(\\d{4})-(\\d{2})$")
            val ymMatch = ymRegex.matchEntire(trimmed)
            if (ymMatch != null) {
                val y = ymMatch.groupValues[1].toInt()
                val m = ymMatch.groupValues[2].toInt() - 1
                val cal = java.util.Calendar.getInstance()
                cal.clear()
                cal.set(java.util.Calendar.YEAR, y)
                cal.set(java.util.Calendar.MONTH, m)
                if (isEndOfRange) {
                    val maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                    cal.set(java.util.Calendar.DAY_OF_MONTH, maxDay)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                    cal.set(java.util.Calendar.MINUTE, 59)
                    cal.set(java.util.Calendar.SECOND, 59)
                    cal.set(java.util.Calendar.MILLISECOND, 999)
                } else {
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis
            }

            // Try YYYY
            val yRegex = Regex("^(\\d{4})$")
            val yMatch = yRegex.matchEntire(trimmed)
            if (yMatch != null) {
                val y = yMatch.groupValues[1].toInt()
                val cal = java.util.Calendar.getInstance()
                cal.clear()
                cal.set(java.util.Calendar.YEAR, y)
                if (isEndOfRange) {
                    cal.set(java.util.Calendar.MONTH, java.util.Calendar.DECEMBER)
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 31)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                    cal.set(java.util.Calendar.MINUTE, 59)
                    cal.set(java.util.Calendar.SECOND, 59)
                    cal.set(java.util.Calendar.MILLISECOND, 999)
                } else {
                    cal.set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis
            }

            // Fallback to numeric
            return trimmed.toLongOrNull()
        }

        private fun parseRangeQuery(text: String): RangeSearch? {
            // First check if it is date querying with custom formats
            val dateRegex = Regex("^date(!?[:=]|!?[<>]=?)([^.]+)(?:\\.\\.([^.]+))?\$", RegexOption.IGNORE_CASE)
            val dateMatch = dateRegex.find(text)
            if (dateMatch != null) {
                val relation = dateMatch.groupValues[1]
                val rawA = dateMatch.groupValues[2]
                val rawB = if (dateMatch.groupValues.size > 3 && dateMatch.groupValues[3].isNotEmpty()) dateMatch.groupValues[3] else null

                var negate = false
                var rel = relation
                if (rel.startsWith("!")) {
                    negate = true
                    rel = rel.substring(1)
                }

                val a = parseDateToMillis(rawA, isEndOfRange = false)
                val b = rawB?.let { parseDateToMillis(it, isEndOfRange = true) }

                if (a != null) {
                    when (rel) {
                        ":", "=" -> {
                            val endVal = b ?: parseDateToMillis(rawA, isEndOfRange = true) ?: a
                            return RangeSearch(SearchQueryTypes.DATE, min = a, max = endVal, negate = negate)
                        }
                        ">=" -> return RangeSearch(SearchQueryTypes.DATE, min = a, negate = negate)
                        ">" -> return RangeSearch(SearchQueryTypes.DATE, min = a + 1, negate = negate)
                        "<=" -> {
                            val endVal = parseDateToMillis(rawA, isEndOfRange = true) ?: a
                            return RangeSearch(SearchQueryTypes.DATE, max = endVal, negate = negate)
                        }
                        "<" -> return RangeSearch(SearchQueryTypes.DATE, max = a - 1, negate = negate)
                    }
                }
            }

            // Simplified range query parsing (date fallback, rating, resolution, person_count)
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
