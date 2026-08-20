package app.codeg.android.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskStatusWidgetProviderTest {
    @Test
    fun `listens for Xiaomi widget refresh action`() {
        assertEquals("miui.appwidget.action.APPWIDGET_UPDATE", TaskStatusWidgetProvider.MIUI_UPDATE)
    }

    @Test
    fun `picker label contains Codeg so Android-widget search can match`() {
        val values = widgetStrings("values")
        val zh = widgetStrings("values-zh-rCN")
        assertTrue(values.contains("<string name=\"app_name\">Codeg</string>"))
        assertTrue(zh.contains("<string name=\"app_name\">Codeg</string>"))
        assertTrue(values.contains("name=\"live_status_widget_picker_name\">Codeg"))
        assertTrue(zh.contains("name=\"live_status_widget_picker_name\">Codeg"))
    }

    private fun widgetStrings(folder: String): String {
        val candidates = listOf(
            File("src/main/res/$folder/strings.xml"),
            File("app/src/main/res/$folder/strings.xml"),
        )
        val file = candidates.first { it.exists() }
        return file.readText()
    }
}
