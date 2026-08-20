package app.codeg.android.feature.tokenusage

import app.codeg.android.core.model.TokenUsagePoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object TokenUsageFormat {
    fun compact(value: Long): String = when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }

    fun percentChange(current: Long, previous: Long?): Double? {
        if (previous == null || previous <= 0L) return null
        return (current - previous).toDouble() / previous.toDouble() * 100.0
    }

    fun isoDate(point: TokenUsagePoint, zone: ZoneId): String {
        val key = point.bucketKey.trim()
        if (key.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return key
        val instant = parseInstant(point.start) ?: parseInstant(point.end)
        return instant?.atZone(zone)?.toLocalDate()?.toString()
            ?: key.takeIf { it.isNotBlank() }
            ?: ""
    }

    fun localDate(point: TokenUsagePoint, zone: ZoneId): LocalDate? =
        runCatching { LocalDate.parse(isoDate(point, zone)) }.getOrNull()

    fun dayBounds(point: TokenUsagePoint, zone: ZoneId = ZoneOffset.UTC): Pair<String, String>? {
        if (point.start.isNotBlank() && point.end.isNotBlank()) return point.start to point.end
        val date = localDate(point, zone) ?: return null
        val start = date.atStartOfDay(zone).toInstant().toString()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toString()
        return start to end
    }

    fun bucketLabel(point: TokenUsagePoint, bucket: String, zone: ZoneId, locale: Locale): String {
        val date = localDate(point, zone)
        if (date != null) {
            val fmt = when (bucket) {
                "month" -> DateTimeFormatter.ofPattern("MMM yyyy", locale)
                "week" -> DateTimeFormatter.ofPattern("MMM d", locale)
                else -> DateTimeFormatter.ofPattern("EEE MMM d", locale)
            }
            return date.format(fmt)
        }
        return point.bucketKey.ifBlank { point.start }
    }

    private fun parseInstant(raw: String): Instant? {
        if (raw.isBlank()) return null
        return runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { Instant.parse(raw + "Z") }.getOrNull()
            ?: runCatching { LocalDate.parse(raw.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant() }.getOrNull()
    }
}
