package app.eddy.browser

import app.eddy.browser.browser.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class LogEntryTest {
    @Test fun parsesNewestFirstAndJoinsStackTraces() {
        val text = """
            --------- beginning of main
            10-01 10:51:53.624 I/eddy( 2149): started
            10-01 10:51:54.100 E/AndroidRuntime( 2149): FATAL EXCEPTION: main
            10-01 10:51:54.100 E/AndroidRuntime( 2149): 	at app.Foo.bar(Foo.kt:1)
        """.trimIndent()
        assertEquals(
            listOf(
                LogEntry("10:51:54", 'E', "AndroidRuntime", "FATAL EXCEPTION: main\n\tat app.Foo.bar(Foo.kt:1)"),
                LogEntry("10:51:53", 'I', "eddy", "started"),
            ),
            LogEntry.parse(text),
        )
    }
}
