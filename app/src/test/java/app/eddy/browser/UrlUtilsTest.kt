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

    @Test fun resolvesDomainWithPort() {
        assertEquals("https://example.com:8443/path", UrlUtils.resolve("example.com:8443/path", engine))
        assertEquals("https://sub.example.com:443", UrlUtils.resolve("sub.example.com:443", engine))
        assertFalse(UrlUtils.isUrl("javascript:alert(1)"))
    }

    @Test fun hosts() {
        assertEquals("example.com", UrlUtils.displayHost("https://www.example.com:8080/x"))
        assertEquals("a.b.com", UrlUtils.host("https://user:pw@a.b.com/x"))
        assertTrue(UrlUtils.sameSite("cdn.example.com", "www.example.com"))
        assertFalse(UrlUtils.sameSite("cdn.tracker.net", "example.com"))
        assertFalse(UrlUtils.sameSite("ads.other.co.uk", "www.bbc.co.uk"))
        assertTrue(UrlUtils.sameSite("static.bbc.co.uk", "www.bbc.co.uk"))
    }

    @Test fun httpsUpgrade() {
        assertEquals("https://example.com/a?b=1", UrlUtils.toHttps("http://example.com/a?b=1"))
        assertEquals("https://example.com", UrlUtils.toHttps("https://example.com"))
        // Only the scheme changes: a host that merely starts with "http" must survive intact.
        assertEquals("https://httpbin.org/get", UrlUtils.toHttps("http://httpbin.org/get"))
        assertEquals("ftp://example.com", UrlUtils.toHttps("ftp://example.com"))
        assertEquals("http://example.com/a", UrlUtils.toHttp("https://example.com/a"))
        assertTrue(UrlUtils.isHttp("HTTP://example.com"))
        assertFalse(UrlUtils.isHttp("https://example.com"))
    }

    @Test fun httpsUpgradeEdgeCases() {
        assertEquals("https://example.com:8080/a", UrlUtils.toHttps("http://example.com:8080/a"))
        assertEquals("https://user:pw@example.com/a", UrlUtils.toHttps("http://user:pw@example.com/a"))
        assertEquals("neverssl.com", UrlUtils.host("http://neverssl.com"))
        // Local names have no certificate to upgrade to.
        assertTrue(UrlUtils.isLocalHost("http://localhost:3000/app"))
        assertTrue(UrlUtils.isLocalHost("http://192.168.1.10"))
        assertTrue(UrlUtils.isLocalHost("http://printer.local"))
        assertFalse(UrlUtils.isLocalHost("http://example.com"))
        assertFalse(UrlUtils.isLocalHost("http://localhost.example.com"))
    }
    @Test fun permissionsUseExactOrigins() {
        assertEquals("https://www.example.com", UrlUtils.origin("https://WWW.example.com:443/path"))
        assertEquals("https://example.com:8443", UrlUtils.origin("https://example.com:8443/"))
        assertEquals("http://example.com", UrlUtils.origin("http://example.com:80"))
        assertEquals("http://[::1]:8080", UrlUtils.origin("http://[::1]:8080/path"))
        assertEquals(null, UrlUtils.origin("file:///private"))
    }

}
