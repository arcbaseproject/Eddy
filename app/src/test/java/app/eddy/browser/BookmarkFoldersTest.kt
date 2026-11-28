package app.eddy.browser

import app.eddy.browser.data.database.BookmarkFolder
import app.eddy.browser.data.database.path
import app.eddy.browser.data.database.withDescendants
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkFoldersTest {
    private val folders = listOf(
        BookmarkFolder(id = 1, name = "Work"),
        BookmarkFolder(id = 2, name = "Docs", parentId = 1),
        BookmarkFolder(id = 3, name = "Old", parentId = 2),
        BookmarkFolder(id = 4, name = "Home"),
    )

    @Test fun findsEveryNestedFolder() {
        assertEquals(setOf(1L, 2L, 3L), folders.withDescendants(1))
        assertEquals(setOf(4L), folders.withDescendants(4))
    }

    @Test fun namesThePathFromTheTop() {
        assertEquals("Work / Docs / Old", folders.path(3))
        assertEquals("Home", folders.path(4))
    }

    @Test fun survivesACycle() {
        val loop = listOf(BookmarkFolder(id = 1, name = "A", parentId = 2), BookmarkFolder(id = 2, name = "B", parentId = 1))
        assertEquals(setOf(1L, 2L), loop.withDescendants(1))
        assertEquals("A / B", loop.path(2))
    }
}
