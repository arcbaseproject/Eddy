package app.eddy.browser

import app.eddy.browser.settings.searchSettings
import app.eddy.browser.settings.settingsIndex
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIndexTest {
    @Test fun everyEntryMatchesARealRowTitle() {
        val source = File("src/main/java/app/eddy/browser/settings").listFiles { f -> f.extension == "kt" }!!
            .filter { it.name != "SettingsSearch.kt" }.joinToString("\n") { it.readText() }
        val missing = settingsIndex.map { it.title }.filter { "\"$it\"" !in source }
        assertTrue("Index titles with no matching row: $missing", missing.isEmpty())
    }

    @Test fun searchMatchesTitleHintAndPage() {
        assertEquals("Ad blocking", searchSettings("adblock").single().title)
        assertTrue(searchSettings("dark").any { it.title == "Theme" })
        assertTrue(searchSettings("privacy cookies").any { it.title == "Cookies" })
        assertTrue(searchSettings("   ").isEmpty())
    }
}
