package app.eddy.browser

import app.eddy.browser.privacy.ContentBlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentBlockerTest {
    @Test fun parsesHostsPlainAndAdblockLines() {
        assertEquals("ads.example.com", ContentBlocker.parseLine("0.0.0.0 ads.example.com"))
        assertEquals("ads.example.com", ContentBlocker.parseLine("127.0.0.1\tAds.Example.com # comment"))
        assertEquals("tracker.net", ContentBlocker.parseLine("tracker.net"))
        assertEquals("ads.example.org", ContentBlocker.parseLine("||ads.example.org^"))
        assertEquals("ads.example.org", ContentBlocker.parseLine("||ads.example.org^\$third-party"))
    }

    @Test fun ignoresNonDomainRules() {
        assertNull(ContentBlocker.parseLine("# comment"))
        assertNull(ContentBlocker.parseLine("! adblock comment"))
        assertNull(ContentBlocker.parseLine("@@||allowed.com^"))
        assertNull(ContentBlocker.parseLine("||example.com/banner/*"))
        assertNull(ContentBlocker.parseLine("example.com##.ad"))
        assertNull(ContentBlocker.parseLine("0.0.0.0 0.0.0.0"))
        assertNull(ContentBlocker.parseLine("localhost"))
    }

    @Test fun firstPartyRequestsAreNeverThirdParty() {
        assertFalse(ContentBlocker.isThirdParty("cdn.example.com", "www.example.com"))
        assertTrue(ContentBlocker.isThirdParty("ads.tracker.net", "www.example.com"))
        assertFalse(ContentBlocker.isThirdParty("ads.tracker.net", ""))
    }
}
