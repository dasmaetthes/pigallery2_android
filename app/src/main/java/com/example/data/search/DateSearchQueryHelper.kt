package com.example.data.search

import com.example.data.ApiMedia
import com.example.data.PiGalleryApi
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Pure helper for constructing PiGallery2 Search API DATE queries (SearchQueryTypes.DATE = 10)
 * for Year, Month, and Date-Root discovery in "View by Date".
 *
 * Uses UTC calendar boundaries padded by ±14 hours on server queries so photos taken in any
 * EXIF timezone offset (UTC-12..UTC+14) around midnight on month/year borders are never missed;
 * callers then filter precisely using DateUtils.getLocalTimeMs().
 */
object DateSearchQueryHelper {

    const val TIMEZONE_PADDING_MS: Long = 14L * 60L * 60L * 1000L
    const val MIN_VALID_DATE_MS: Long = 1L
    const val MIN_DISCOVERY_YEAR: Int = 1970

    fun yearUtcBounds(year: Int): Pair<Long, Long> {
        val start = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val end = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        return Pair(start, end)
    }

    fun monthUtcBounds(year: Int, month: Int): Pair<Long, Long> {
        val safeMonth = month.coerceIn(1, 12)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, safeMonth - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.apply {
            set(Calendar.DAY_OF_MONTH, maxDay)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val end = cal.timeInMillis
        return Pair(start, end)
    }

    fun paddedBounds(bounds: Pair<Long, Long>): Pair<Long, Long> {
        val minPadded = (bounds.first - TIMEZONE_PADDING_MS).coerceAtLeast(MIN_VALID_DATE_MS)
        val maxPadded = bounds.second + TIMEZONE_PADDING_MS
        return Pair(minPadded, maxPadded)
    }

    fun buildDateRangeQuery(
        minMs: Long?,
        maxMs: Long?,
        rootPath: String = "",
        negate: Boolean = false
    ): Map<String, Any?> {
        val dateQuery = RangeSearch(
            type = SearchQueryTypes.DATE,
            min = minMs,
            max = maxMs,
            negate = negate
        )
        val cleanRoot = PiGalleryApi.cleanServerPath(rootPath)
        return if (cleanRoot.isNotEmpty()) {
            AndSearchQuery(
                listOf(
                    dateQuery,
                    TextSearch(
                        type = SearchQueryTypes.DIRECTORY,
                        value = cleanRoot,
                        matchType = TextSearchQueryMatchTypes.LIKE
                    )
                )
            ).toJson()
        } else {
            dateQuery.toJson()
        }
    }

    fun buildYearQuery(year: Int, rootPath: String = ""): Map<String, Any?> {
        val (minMs, maxMs) = paddedBounds(yearUtcBounds(year))
        return buildDateRangeQuery(minMs = minMs, maxMs = maxMs, rootPath = rootPath)
    }

    fun buildMonthQuery(year: Int, month: Int, rootPath: String = ""): Map<String, Any?> {
        val (minMs, maxMs) = paddedBounds(monthUtcBounds(year, month))
        return buildDateRangeQuery(minMs = minMs, maxMs = maxMs, rootPath = rootPath)
    }

    fun buildAllDatedQuery(rootPath: String = ""): Map<String, Any?> {
        return buildDateRangeQuery(minMs = MIN_VALID_DATE_MS, maxMs = null, rootPath = rootPath)
    }

    /**
     * Probes whether any dated media exists strictly before [yearExclusive] (with +14h padding so
     * positive timezone offsets near Dec 31 of yearExclusive-1 are safely captured).
     */
    fun buildBeforeYearQuery(yearExclusive: Int, rootPath: String = ""): Map<String, Any?> {
        val (yearStartMs, _) = yearUtcBounds(yearExclusive)
        val maxMs = (yearStartMs - 1L) + TIMEZONE_PADDING_MS
        return buildDateRangeQuery(minMs = MIN_VALID_DATE_MS, maxMs = maxMs, rootPath = rootPath)
    }

    fun buildDatePath(year: Int, month: Int? = null): String {
        return if (month != null && month in 1..12) {
            "year:$year/month:$month"
        } else {
            "year:$year"
        }
    }

    fun parseYearFromPath(path: String): Int? {
        if (!path.startsWith("year:")) return null
        return path.substringAfter("year:").substringBefore("/").toIntOrNull()?.takeIf { it > 0 }
    }

    fun parseMonthFromPath(path: String): Int? {
        if (!path.contains("month:")) return null
        return path.substringAfter("month:").substringBefore("/").toIntOrNull()?.takeIf { it in 1..12 }
    }

    fun buildSearchTextForDate(year: Int, month: Int? = null): String {
        return if (month != null && month in 1..12) {
            String.format(Locale.US, "date:%04d-%02d", year, month)
        } else {
            String.format(Locale.US, "date:%04d", year)
        }
    }

    fun matchesRootPath(media: ApiMedia, cleanRoot: String): Boolean {
        if (cleanRoot.isEmpty()) return true
        val parent = PiGalleryApi.cleanServerPath(media.parentPath)
        return parent == cleanRoot || parent.startsWith("$cleanRoot/")
    }
}
