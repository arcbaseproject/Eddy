package app.eddy.browser

import app.eddy.browser.downloads.DownloadNames
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
}
