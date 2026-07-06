package com.example.data.search

enum class SearchQueryTypes(val value: Int) {
    AND(1),
    OR(2),
    SOME_OF(3),
    UNKNOWN_RELATION(99999),
    
    // Range types
    DATE(10),
    RATING(12),
    RESOLUTION(14),
    PERSON_COUNT(16),
    
    DISTANCE(50),
    ORIENTATION(51),
    DATE_PATTERN(60),
    
    // Text search types
    ANY_TEXT(100),
    CAPTION(101),
    DIRECTORY(102),
    FILE_NAME(103),
    KEYWORD(104),
    PERSON(105),
    POSITION(106);

    companion object {
        fun fromValue(value: Int): SearchQueryTypes = entries.find { it.value == value } ?: UNKNOWN_RELATION
        val TEXT_SEARCH_TYPES = listOf(ANY_TEXT, CAPTION, DIRECTORY, FILE_NAME, KEYWORD, PERSON, POSITION)
    }
}

enum class TextSearchQueryMatchTypes(val value: Int) {
    EXACT_MATCH(1),
    LIKE(2)
}

open class SearchQueryDTO(val type: SearchQueryTypes) {
    open fun toJson(): Map<String, Any?> = mutableMapOf("type" to type.value)
}

open class NegatableSearchQuery(type: SearchQueryTypes, val negate: Boolean = false) : SearchQueryDTO(type) {
    override fun toJson(): Map<String, Any?> {
        val map = super.toJson().toMutableMap()
        if (negate) map["negate"] = true
        return map
    }
}

open class SearchListQuery(type: SearchQueryTypes, val list: List<SearchQueryDTO>) : SearchQueryDTO(type) {
    override fun toJson(): Map<String, Any?> {
        val map = super.toJson().toMutableMap()
        map["list"] = list.map { it.toJson() }
        return map
    }
}

class AndSearchQuery(list: List<SearchQueryDTO>) : SearchListQuery(SearchQueryTypes.AND, list)
class OrSearchQuery(list: List<SearchQueryDTO>) : SearchListQuery(SearchQueryTypes.OR, list)
class SomeOfSearchQuery(list: List<SearchQueryDTO>, val min: Int? = null) : SearchListQuery(SearchQueryTypes.SOME_OF, list) {
    override fun toJson(): Map<String, Any?> {
        val map = super.toJson().toMutableMap()
        if (min != null) map["min"] = min
        return map
    }
}

class TextSearch(
    type: SearchQueryTypes,
    val value: String,
    val matchType: TextSearchQueryMatchTypes = TextSearchQueryMatchTypes.LIKE,
    negate: Boolean = false
) : NegatableSearchQuery(type, negate) {
    override fun toJson(): Map<String, Any?> {
        val map = super.toJson().toMutableMap()
        map["value"] = value
        map["matchType"] = matchType.value
        return map
    }
}

class RangeSearch(
    type: SearchQueryTypes,
    val min: Long? = null,
    val max: Long? = null,
    negate: Boolean = false
) : NegatableSearchQuery(type, negate) {
    override fun toJson(): Map<String, Any?> {
        val map = super.toJson().toMutableMap()
        if (min != null) map["min"] = min
        if (max != null) map["max"] = max
        return map
    }
}
