package app.eddy.browser

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.eddy.browser.bookmarks.BookmarkManager
import app.eddy.browser.data.database.EddyDatabase
import app.eddy.browser.downloads.DownloadNames
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportAndDownloadTest {
    @Test fun mixedRootEntriesSurviveReimportAndExport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, EddyDatabase::class.java).build()
        try {
            val manager = BookmarkManager(database, this)
            val html = """
                <DL><DT><H3>Zebra</H3><DL>
                <DT><A HREF="https://example.org/1">First</A>
                <DT><A HREF="https://example.org/2">Second</A></DL>
                <DT><A HREF="https://example.org/root">Middle</A>
                <DT><H3>Alpha</H3><DL></DL></DL>
            """.trimIndent()
            repeat(2) {
                assertEquals(3, manager.importHtml(if (it == 0) html else manager.exportHtml()))
                assertEquals(listOf("First", "Second", "Middle"), manager.bookmarks.first().map { it.title })
                assertEquals(listOf("Zebra", "Alpha"), manager.folders.first().map { it.name })
                val exported = manager.exportHtml()
                val offsets = listOf("Zebra", "First", "Second", "Middle", "Alpha").map { exported.indexOf(">$it<") }
                assertTrue(offsets.first() >= 0 && offsets.zipWithNext().all { (a, b) -> a < b })
            }
        } finally {
            database.close()
        }
    }

    @Test fun nestedFoldersSurviveReimportAndExport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, EddyDatabase::class.java).build()
        try {
            val manager = BookmarkManager(database, this)
            val html = """
                <DL><DT><H3>Work</H3><DL>
                <DT><H3>Docs</H3><DL>
                <DT><A HREF="https://example.org/deep">Deep</A></DL>
                <DT><A HREF="https://example.org/work">InWork</A></DL>
                <DT><H3>Docs</H3><DL></DL></DL>
            """.trimIndent()
            repeat(2) {
                manager.importHtml(if (it == 0) html else manager.exportHtml())
                val folders = manager.folders.first()
                // Two different "Docs": one inside Work, one at the top. A re-import merges instead of adding more.
                assertEquals(3, folders.size)
                val work = folders.single { f -> f.name == "Work" }
                val inner = folders.single { f -> f.name == "Docs" && f.parentId == work.id }
                val marks = manager.bookmarks.first().associateBy { b -> b.title }
                assertEquals(inner.id, marks.getValue("Deep").folderId)
                assertEquals(work.id, marks.getValue("InWork").folderId)
            }
        } finally {
            database.close()
        }
    }

    @Test fun unnamedPhpResponseUsesServedType() {
        val url = "https://example.org/download.php?id=123"
        assertTrue(DownloadNames.forDownload(url, "", "application/pdf").endsWith(".pdf"))
        assertTrue(!DownloadNames.forDownload(url, "", "application/octet-stream").endsWith(".php"))
    }
}
