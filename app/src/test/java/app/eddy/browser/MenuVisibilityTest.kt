package app.eddy.browser

import app.eddy.browser.browser.toggleMenuItem
import app.eddy.browser.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuVisibilityTest {
    @Test fun allActionsStartVisibleAndCanBeRestoredIndividually() {
        val defaults = Settings().hiddenMenuItems
        assertEquals(emptySet<String>(), defaults)
        val hidden = defaults.toggleMenuItem("Reader").toggleMenuItem("Print")
        assertEquals(setOf("Reader", "Print"), hidden)
        assertEquals(setOf("Print"), hidden.toggleMenuItem("Reader"))
    }
}
