package app.eddy.browser

import app.eddy.browser.passwords.PasswordManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordManagerTest {
    @Test fun normalizesOrigins() {
        assertEquals("https://example.com", PasswordManager.normalizeOrigin("example.com"))
        assertEquals("https://example.com", PasswordManager.normalizeOrigin("  https://Example.com/login?next=/a#x "))
        assertEquals("http://10.0.2.2:8765", PasswordManager.normalizeOrigin("http://10.0.2.2:8765/signup"))
        assertEquals("https://sub.example.co.uk:8443", PasswordManager.normalizeOrigin("sub.example.co.uk:8443/x"))
        assertNull(PasswordManager.normalizeOrigin("not a site"))
        assertNull(PasswordManager.normalizeOrigin("intranet"))
    }

    @Test fun generatesDistinctPasswordsOfTheRequestedLength() {
        val a = PasswordManager.generatePassword(24)
        assertEquals(24, a.length)
        assertNotEquals(a, PasswordManager.generatePassword(24))
    }
}

class CsvTest {
    @Test fun parsesQuotesCommasAndNewlines() {
        val rows = app.eddy.browser.passwords.Csv.parse("\uFEFFname,url,username,password,note\r\nex,https://ex.com,bob,\"p,\"\"w\"\"\nd\",\r\n\r\nlast,u,v,w,")
        assertEquals(listOf("name", "url", "username", "password", "note"), rows[0])
        assertEquals(listOf("ex", "https://ex.com", "bob", "p,\"w\"\nd", ""), rows[1])
        assertEquals(listOf("last", "u", "v", "w", ""), rows[2])
        assertEquals(3, rows.size)
    }

    @Test fun writesWhatItReads() {
        val cells = listOf("a", "b,c", "d\"e", "line\nbreak")
        val line = app.eddy.browser.passwords.Csv.row(cells)
        assertEquals(listOf(cells), app.eddy.browser.passwords.Csv.parse(line))
    }
}
