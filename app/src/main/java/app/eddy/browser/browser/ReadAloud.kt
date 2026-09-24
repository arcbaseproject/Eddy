package app.eddy.browser.browser

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * Reads the visible page text with the system speech engine. The engine takes a limited amount of
 * text per call, so the page is split into chunks and queued; the last chunk finishing ends the
 * session. One engine instance is kept for the app and released with the ViewModel.
 */
class ReadAloud(private val context: Context, private val onStateChange: (Boolean) -> Unit) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    /** Speaks [text] from the start, replacing anything already queued. Empty text stops. */
    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return stop()
        pending = clean
        val engine = tts
        if (engine != null && ready) return flush(engine)
        if (engine != null) return
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            val e = tts
            if (ready && e != null) {
                e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String) = Unit
                    override fun onDone(id: String) { if (id == LAST) onStateChange(false) }
                    @Deprecated("Deprecated in Java") override fun onError(id: String) { onStateChange(false) }
                })
                flush(e)
            } else {
                pending = null
                onStateChange(false)
            }
        }
    }

    fun stop() {
        pending = null
        tts?.stop()
        onStateChange(false)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun flush(engine: TextToSpeech) {
        val text = pending ?: return
        pending = null
        val chunks = SpeechChunks.split(text)
        if (chunks.isEmpty()) return onStateChange(false)
        chunks.forEachIndexed { i, part ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(part, mode, null, if (i == chunks.lastIndex) LAST else "eddy-$i")
        }
        onStateChange(true)
    }

    private companion object {
        const val LAST = "eddy-last"
    }
}

/** Splitting is the only part worth testing on its own, so it lives outside the engine wrapper. */
object SpeechChunks {
    // TextToSpeech.getMaxSpeechInputLength() is 4000; stay well inside it.
    const val MAX_CHUNK = 1500
    // A very long page would otherwise queue for hours.
    const val MAX_CHUNKS = 400

    /** Splits on sentence ends so a chunk boundary does not cut a word, with a hard cap as a fallback. */
    fun split(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        text.split(Regex("(?<=[.!?\u3002\uFF01\uFF1F\n])\\s+")).forEach { sentence ->
            sentence.chunked(MAX_CHUNK).forEach { part ->
                if (current.isNotEmpty() && current.length + part.length + 1 > MAX_CHUNK) {
                    out.add(current.toString())
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(part)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out.filter { it.isNotBlank() }.take(MAX_CHUNKS)
    }
}
