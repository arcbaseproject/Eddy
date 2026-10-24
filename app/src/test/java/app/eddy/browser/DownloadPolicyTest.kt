package app.eddy.browser

import app.eddy.browser.downloads.DownloadPolicy
import app.eddy.browser.util.readTextCapped
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadPolicyTest {
    @Test fun referrerFollowsStrictOriginWhenCrossOrigin() {
        val page = "https://source.example/account?token=test#frag"
        assertEquals("https://source.example/account?token=test", DownloadPolicy.referrerFor(page, "https://source.example/file.zip"))
        assertEquals("https://source.example/", DownloadPolicy.referrerFor(page, "https://cdn.other.example/file.zip"))
        assertNull(DownloadPolicy.referrerFor(page, "http://source.example/file.zip"))
        assertNull(DownloadPolicy.referrerFor("about:blank", "https://a.example/"))
        assertEquals("https://source.example/", DownloadPolicy.referrerFor(page, "https://source.example:8443/f"))
    }

    @Test fun percentDecodingKeepsBinaryBytes() {
        assertArrayEquals(byteArrayOf(0x00, 0xFF.toByte(), 0x80.toByte()), DownloadPolicy.percentDecode("%00%FF%80"))
        assertArrayEquals("a b€".toByteArray(), DownloadPolicy.percentDecode("a%20b€"))
        assertArrayEquals("100%".toByteArray(), DownloadPolicy.percentDecode("100%"))
    }

    @Test fun cappedReadRefusesOversizedInput() {
        assertEquals("hello", "hello".byteInputStream().readTextCapped(5))
        assertNull("hello!".byteInputStream().readTextCapped(5))
    }
}
