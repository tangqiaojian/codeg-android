package app.codeg.android.feature.live

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskStatusWidgetProviderTest {
    @Test
    fun `listens for Xiaomi widget refresh action`() {
        assertEquals("miui.appwidget.action.APPWIDGET_UPDATE", TaskStatusWidgetProvider.MIUI_UPDATE)
    }
}
