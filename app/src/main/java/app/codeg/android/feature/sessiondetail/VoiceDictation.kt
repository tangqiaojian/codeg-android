package app.codeg.android.feature.sessiondetail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceDictation(
    context: Context,
    private val onSpoken: (text: String, isFinal: Boolean) -> Unit,
    private val onError: () -> Unit,
    private val onListening: (Boolean) -> Unit,
) {
    private val hostContext = context
    private var recognizer: SpeechRecognizer? = null
    val inlineAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(hostContext)

    fun start() {
        if (!inlineAvailable) {
            onError()
            return
        }
        release(notify = false)
        val speech = runCatching { SpeechRecognizer.createSpeechRecognizer(hostContext) }.getOrNull()
        if (speech == null) {
            onError()
            return
        }
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListening(true)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                release(notify = true)
                if (error != SpeechRecognizer.ERROR_CLIENT && error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    onError()
                }
            }
            override fun onResults(results: Bundle?) {
                release(notify = true)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onSpoken(text, true)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onSpoken(text, false)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        runCatching {
            speech.startListening(recognitionIntent())
            onListening(true)
        }.onFailure {
            release(notify = true)
            onError()
        }
    }

    /** End the utterance and wait for [onResults] so auto-send can still fire. */
    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    fun release(notify: Boolean = true) {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        if (notify) onListening(false)
    }

    companion object {
        fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }

        fun canLaunchRecognizer(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context) ||
                recognitionIntent().resolveActivity(context.packageManager) != null
    }
}
