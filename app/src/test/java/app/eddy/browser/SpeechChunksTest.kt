package app.eddy.browser

import app.eddy.browser.browser.SpeechChunks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunksTest {
    @Test fun keepsShortTextInOnePiece() {
        assertEquals(listOf("Hello there. How are you?"), SpeechChunks.split("Hello there. How are you?"))
    }

    @Test fun dropsBlankInput() {
        assertTrue(SpeechChunks.split("   \n  ").isEmpty())
    }

    @Test fun splitsOnSentenceEndsAndStaysUnderTheEngineLimit() {
        val sentence = "word ".repeat(100).trim() + ". "
        val chunks = SpeechChunks.split(sentence.repeat(20))
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it.length <= SpeechChunks.MAX_CHUNK) }
    }

    @Test fun splitsTextWithNoSentenceEndAtAll() {
        val chunks = SpeechChunks.split("a".repeat(SpeechChunks.MAX_CHUNK * 3))
        assertEquals(3, chunks.size)
        chunks.forEach { assertTrue(it.length <= SpeechChunks.MAX_CHUNK) }
    }

    @Test fun losesNoCharactersOtherThanTheSeparators() {
        val text = "One. Two. Three."
        assertEquals(text.filter { !it.isWhitespace() }, SpeechChunks.split(text).joinToString("").filter { !it.isWhitespace() })
    }

    @Test fun capsRunawayPages() {
        val chunks = SpeechChunks.split(("x".repeat(SpeechChunks.MAX_CHUNK) + ". ").repeat(SpeechChunks.MAX_CHUNKS + 20))
        assertEquals(SpeechChunks.MAX_CHUNKS, chunks.size)
    }
}
