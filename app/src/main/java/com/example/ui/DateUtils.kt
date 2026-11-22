package com.example.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    fun formatMediaDate(
        timestamp: Long?,
        offset: String?,
        formatString: String = "dd.MM.yyyy HH:mm:ss",
        unknownLabel: String = "Unknown"
    ): String {
        if (timestamp == null || timestamp <= 0L) return unknownLabel
        val ms = if (timestamp < 10000000000L) timestamp * 1000L else timestamp
        return try {
            val sdf = SimpleDateFormat(formatString, Locale.getDefault())
            
            // If offset is provided (e.g., "+02:00"), use it. Otherwise fallback to UTC to prevent shifting.
            if (!offset.isNullOrEmpty()) {
                sdf.timeZone = TimeZone.getTimeZone("GMT$offset")
            } else {
                sdf.timeZone = TimeZone.getTimeZone("UTC")
            }
            
            sdf.format(Date(ms))
        } catch (e: Exception) {
            unknownLabel
        }
    }

    fun getLocalTimeMs(timestamp: Long?, offset: String?): Long {
        if (timestamp == null || timestamp <= 0L) return 0L
        val ms = if (timestamp < 10000000000L) timestamp * 1000L else timestamp
        if (offset.isNullOrEmpty()) return ms

        try {
            if (offset.length >= 5 && (offset.startsWith("+") || offset.startsWith("-"))) {
                val parts = offset.substring(1).split(":")
                val hours = Math.abs(parts[0].toInt())
                val minutes = if (parts.size > 1) parts[1].toInt() else 0
                val totalOffsetMinutes = hours * 60 + minutes
                val sign = if (offset.startsWith("-")) -1 else 1
                return ms + (sign * totalOffsetMinutes * 60 * 1000L)
            }
        } catch (e: Exception) {
            // Ignore format errors and fallback to base ms
        }
        return ms
    }
}
