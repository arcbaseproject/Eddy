package app.eddy.browser

import app.eddy.browser.browser.Translate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TranslateTest {
    @Test fun buildsProxyUrl() {
        assertEquals(
            "https://translate.google.com/translate?sl=auto&tl=de&hl=de&u=https%3A%2F%2Fexample.com%2Fa%3Fb%3D1%26c%3D2",
            Translate.url("https://example.com/a?b=1&c=2", "de"),
        )
    }

    @Test fun switchesLanguageOfTranslatedPage() {
        assertEquals(
            "https://example-com.translate.goog/a?_x_tr_sl=auto&_x_tr_tl=fr&_x_tr_hl=de",
            Translate.url("https://example-com.translate.goog/a?_x_tr_sl=auto&_x_tr_tl=de&_x_tr_hl=de", "fr"),
        )
    }

    @Test fun detectsTranslatedPages() {
        assertTrue(Translate.isTranslated("https://github-com.translate.goog/x"))
        assertFalse(Translate.isTranslated("https://translate.goog.evil.com/x"))
        assertFalse(Translate.isTranslated("https://example.com/?u=a.translate.goog"))
    }

    @Test fun deviceLanguageComesFirst() {
        assertEquals("ja", Translate.languages(Locale.JAPAN).first())
        assertEquals("zh-TW", Translate.deviceLanguage(Locale.TRADITIONAL_CHINESE))
        assertEquals("zh-CN", Translate.deviceLanguage(Locale.SIMPLIFIED_CHINESE))
        assertEquals(Translate.languages().size, Translate.languages().toSet().size)
    }
}
