package app.eddy.browser

import app.eddy.browser.data.models.CookieMode
import app.eddy.browser.data.models.ProtectionLevel
import app.eddy.browser.data.models.Settings
import app.eddy.browser.settings.isNewerVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionLevelTest {
    @Test fun levelFollowsTheSwitches() {
        assertEquals(ProtectionLevel.BALANCED, ProtectionLevel.of(Settings()))
        assertEquals(ProtectionLevel.STRICT, ProtectionLevel.of(ProtectionLevel.STRICT.applyTo(Settings())))
        val custom = Settings(cookieMode = CookieMode.ALLOW_ALL)
        assertEquals(ProtectionLevel.CUSTOM, ProtectionLevel.of(custom))
        assertEquals(ProtectionLevel.BALANCED, ProtectionLevel.of(ProtectionLevel.BALANCED.applyTo(custom)))
    }

    @Test fun comparesReleaseVersions() {
        assertTrue(isNewerVersion("0.6.40-beta", "0.6.39-beta"))
        assertTrue(isNewerVersion("0.7", "0.6.39-beta"))
        assertTrue(isNewerVersion("0.6.100", "0.6.99"))
        assertFalse(isNewerVersion("0.6.39-beta", "0.6.39-beta"))
        assertFalse(isNewerVersion("0.6.38", "0.6.39-beta"))
    }
}
