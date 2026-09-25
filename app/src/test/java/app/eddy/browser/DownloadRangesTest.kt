package app.eddy.browser

import app.eddy.browser.downloads.DownloadRanges
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadRangesTest {
    @Test fun acceptsMatchingResumeRange() { assertEquals(100L, DownloadRanges.total("bytes 40-99/100", 40, 60)) }
    @Test fun acceptsUnknownTotal() { assertEquals(-1L, DownloadRanges.total("bytes 40-99/*", 40, 60)) }
    @Test fun refusesCorruptPartialResponses() {
        listOf(null, "bytes 0-59/100", "bytes 40-100/100", "bytes 99-40/100", "bytes 40-99/1000", "bytes 40-9223372036854775807/*").forEach { header ->
            val length = if (header == "bytes 40-99/1000") 20L else 60L
            assertThrows(IOException::class.java) { DownloadRanges.total(header, 40, length) }
        }
    }
}
