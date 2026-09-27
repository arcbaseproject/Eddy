package app.eddy.browser

import app.eddy.browser.downloads.DownloadNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNamesTest {
    @Test fun flagsInstallersAndScripts() {
        listOf("app.apk", "Setup.EXE", "run.sh", "tool.msi", "payload.Jar", "thing.bat").forEach {
            assertTrue(it, DownloadNames.isExecutable(it))
        }
    }

    @Test fun leavesOrdinaryFilesAlone() {
        listOf("report.pdf", "photo.jpeg", "archive.zip", "notes.txt", "noextension", "trailing.").forEach {
            assertFalse(it, DownloadNames.isExecutable(it))
        }
    }

    @Test fun looksAtTheLastExtensionOnly() {
        assertTrue(DownloadNames.isExecutable("invoice.pdf.apk"))
        assertFalse(DownloadNames.isExecutable("apk.pdf"))
    }

    @Test fun plainFilenameIsTakenAsWritten() {
        assertEquals("50% off.pdf", DownloadNames.forResponse("attachment; filename=\"50% off.pdf\"", ""))
        assertEquals("a+b.txt", DownloadNames.forResponse("attachment; filename=\"a+b.txt\"", ""))
    }

    @Test fun extendedFilenameIsDecoded() {
        assertEquals("€ rates.pdf", DownloadNames.forResponse("attachment; filename*=UTF-8''%E2%82%AC%20rates.pdf", ""))
    }
}
