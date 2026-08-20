package app.codeg.android.feature.tokenusage

import app.codeg.android.core.model.TokenUsagePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class TokenUsageFormatTest {

    @Test
    fun `compacts token counts for a dense daily list`() {
        assertEquals("12", TokenUsageFormat.compact(12))
        assertEquals("1.2K", TokenUsageFormat.compact(1_234))
        assertEquals("1.5M", TokenUsageFormat.compact(1_500_000))
    }

    @Test
    fun `iso date prefers bucket key then start instant`() {
        val keyed = TokenUsagePoint(bucketKey = "2026-08-19", start = "2026-08-19T16:00:00Z")
        assertEquals("2026-08-19", TokenUsageFormat.isoDate(keyed, ZoneOffset.UTC))
        val fromStart = TokenUsagePoint(start = "2026-08-18T16:00:00Z")
        assertEquals("2026-08-19", TokenUsageFormat.isoDate(fromStart, ZoneOffset.ofHours(8)))
    }

    @Test
    fun `percent change is null when there is no previous baseline`() {
        assertEquals(50.0, TokenUsageFormat.percentChange(150, 100)!!, 0.01)
        assertNull(TokenUsageFormat.percentChange(10, 0))
        assertNull(TokenUsageFormat.percentChange(10, null))
    }
}
