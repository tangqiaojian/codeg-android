package app.codeg.android.feature.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiFocusParamTest {

    @Test
    fun `focus payload carries ticker, aod title, and updatable flag`() {
        val json = XiaomiFocusParam.encode(
            title = "Fix auth",
            content = "Grok · Running",
            ticker = "Grok · Running",
        )
        assertTrue(json.contains("\"param_v2\""))
        assertTrue(json.contains("\"ticker\":\"Grok · Running\""))
        assertTrue(json.contains("\"aodTitle\":\"Grok · Running\""))
        assertTrue(json.contains("\"updatable\":true"))
        assertTrue(json.contains("\"business\":\"codeg_live_task\""))
        assertFalse(json.contains("\"cancel\":true"))
    }

    @Test
    fun `island areas use HyperOS template objects, not raw title strings`() {
        val json = XiaomiFocusParam.encode(
            title = "Fix auth",
            content = "Grok · Running",
            ticker = "Grok · Running",
        )
        assertTrue(json.contains("\"baseInfo\""))
        assertTrue(json.contains("\"imageTextInfoLeft\""))
        assertTrue(json.contains("\"textInfo\""))
        assertTrue(json.contains("\"filterWhenNoPermission\":false"))
        assertFalse(json.contains("\"textTitle\":\"Fix auth\""))
        assertFalse(json.contains("\"textContent\":\"Grok · Running\""))
    }

    @Test
    fun `cancel payload tells HyperOS to dismiss the island`() {
        val json = XiaomiFocusParam.cancel()
        assertTrue(json.contains("\"cancel\":true"))
    }
}
