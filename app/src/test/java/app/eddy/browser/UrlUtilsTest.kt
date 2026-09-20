package app.eddy.browser

import app.eddy.browser.data.models.SearchEngines
import app.eddy.browser.util.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {
    private val engine = SearchEngines.builtIn("https://searx.be").first()

    @Test fun detectsUrls() {
        assertTrue(UrlUtils.isUrl("example.com"))
        assertTrue(UrlUtils.isUrl("https://example.com/a?b=c d".replace(" ", "%20")))
        assertTrue(UrlUtils.isUrl("localhost:8080/x"))
        assertTrue(UrlUtils.isUrl("192.168.1.1"))
        assertTrue(UrlUtils.isUrl("sub.example.co.uk/path"))
        assertFalse(UrlUtils.isUrl("hello world"))
        assertFalse(UrlUtils.isUrl("javascript:alert(1)"))
        assertFalse(UrlUtils.isUrl("note: buy milk"))
        assertFalse(UrlUtils.isUrl("kotlin"))
    }

    @Test fun resolves() {
        assertEquals("https://example.com", UrlUtils.resolve("example.com", engine))
        assertEquals("http://localhost:3000", UrlUtils.resolve("localhost:3000", engine))
        assertEquals("https://duckduckgo.com/?q=a+b", UrlUtils.resolve("a b", engine))
        assertEquals("https://duckduckgo.com/?q=javascript%3Aalert%281%29", UrlUtils.resolve("javascript:alert(1)", engine))
    }

    @Test fun hosts() {
        assertEquals("example.com", UrlUtils.displayHost("https://www.example.com:8080/x"))
        assertEquals("a.b.com", UrlUtils.host("https://user:pw@a.b.com/x"))
        assertTrue(UrlUtils.sameSite("cdn.example.com", "www.example.com"))
        assertFalse(UrlUtils.sameSite("cdn.tracker.net", "example.com"))
        assertFalse(UrlUtils.sameSite("ads.other.co.uk", "www.bbc.co.uk"))
        assertTrue(UrlUtils.sameSite("static.bbc.co.uk", "www.bbc.co.uk"))
    }
}
