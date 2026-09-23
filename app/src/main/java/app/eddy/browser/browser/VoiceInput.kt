package app.eddy.browser.browser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.theme.LocalReducedMotion
import app.eddy.browser.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shared between the mic button and the trace it feeds, so the held state can be shown away from the finger. */
@Stable
class VoiceState {
    var listening by mutableStateOf(false)
        internal set
    /** Smoothed microphone level, 0..1. */
    var level by mutableFloatStateOf(0f)
        internal set
}

@Composable
fun rememberVoiceState(): VoiceState = remember { VoiceState() }

/**
 * Dictation for the omnibox: one tap starts listening, a second tap ends it, and the recogniser also
 * ends the utterance by itself. The best transcript so far is pushed to [onText] as it arrives.
 *
 * It is a toggle rather than a press-and-hold because the omnibox resizes while transcripts stream in,
 * and a held button that moves under a still finger loses the gesture.
 *
 * Renders nothing when the device has no recognition service, so there is no dead button.
 */
@Composable
fun VoiceInputButton(onText: (String) -> Unit, state: VoiceState = rememberVoiceState(), modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!SpeechRecognizer.isRecognitionAvailable(context)) return

    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val locale = remember { java.util.Locale.getDefault().toLanguageTag() }
    // Set when the recogniser reports back, so the watchdog below can tell a finished session from a stuck one.
    val finished = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    val recognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                private fun best(results: Bundle?) =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

                override fun onPartialResults(partialResults: Bundle?) { best(partialResults)?.let(onText) }
                override fun onResults(results: Bundle?) { best(results)?.let(onText); stop(state); finished.set(true) }
                override fun onError(error: Int) { stop(state); finished.set(true) }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                // The reported range is roughly -2..10 dB; anything quieter reads as silence.
                override fun onRmsChanged(rmsdB: Float) { state.level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }
    DisposableEffect(recognizer) { onDispose { recognizer.destroy() } }

    val held = MaterialTheme.colorScheme.primary
    // The trace beside the omnibox carries the feedback; the button only needs to read as pressed.
    val fill by animateFloatAsState(if (state.listening) 1f else 0f, spring(dampingRatio = 0.7f), label = "voiceFill")

    Box(
        modifier
            .sizeIn(minWidth = Dimens.touchTarget, minHeight = Dimens.touchTarget)
            .drawBehind {
                if (fill <= 0f) return@drawBehind
                val d = size.minDimension * 0.86f * fill
                drawRoundRect(
                    held,
                    topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f),
                    size = Size(d, d),
                    cornerRadius = CornerRadius(d * 0.42f),
                )
            }
            .clickable(
                role = Role.Button,
                onClickLabel = if (state.listening) "Stop dictation" else "Start dictation",
            ) {
                when {
                    !granted -> askPermission.launch(Manifest.permission.RECORD_AUDIO)
                    // stopListening keeps whatever was said; cancel would throw it away.
                    state.listening -> { recognizer.stopListening(); stop(state); haptics.tick() }
                    else -> {
                        state.listening = true
                        state.level = 0f
                        finished.set(false)
                        haptics.confirm()
                        recognizer.startListening(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                // A web-search model is tuned for short queries and site names, which is what
                                // an address bar gets; free-form dictation mishears them as ordinary prose.
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
                                // The server-side recogniser is markedly better than the on-device fallback.
                                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName),
                        )
                        // A recogniser that never reports back would hold the microphone open, so drop
                        // the session if nothing has arrived long after the last word.
                        scope.launch {
                            delay(30_000)
                            if (!finished.get()) { recognizer.cancel(); stop(state) }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Mic,
            if (state.listening) "Listening, tap to stop" else "Dictate",
            tint = if (state.listening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun stop(state: VoiceState) {
    state.listening = false
    state.level = 0f
}

/**
 * The held state, shown where the finger is not: a running trace of what the microphone is hearing,
 * painted behind the text field. It claims no layout space of its own, because anything that grows
 * the omnibox would slide the button out from under the finger and cancel the hold.
 */
@Composable
fun Modifier.voiceTrace(state: VoiceState): Modifier {
    val reduced = LocalReducedMotion.current
    val ink = MaterialTheme.colorScheme.primary
    val history = remember { mutableStateListOf<Float>() }

    LaunchedEffect(state.listening, reduced) {
        if (!state.listening) { history.clear(); return@LaunchedEffect }
        if (reduced) return@LaunchedEffect
        while (true) {
            history.add(state.level)
            if (history.size > MAX_BARS) history.removeAt(0)
            delay(55)
        }
    }

    return drawBehind {
        if (!state.listening) return@drawBehind
        val barWidth = 3.dp.toPx()
        val step = barWidth + 4.dp.toPx()
        val slots = (size.width / step).toInt().coerceAtLeast(1)
        val floor = 2.dp.toPx()
        val span = size.height * 0.62f
        // Reduced motion gets one steady meter instead of a moving trace.
        val readings = if (reduced) List(slots) { state.level } else history.takeLast(slots)
        readings.forEachIndexed { i, value ->
            // Counted from the right, so the newest reading lands next to the button.
            val fromRight = readings.lastIndex - i
            val x = size.width - (fromRight + 1) * step
            val h = floor + value * (span - floor)
            val age = if (readings.size <= 1) 0f else fromRight.toFloat() / (readings.size - 1)
            drawRoundRect(
                ink,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f),
                alpha = (1f - age * 0.85f) * 0.55f,
            )
        }
    }
}

private const val MAX_BARS = 64
