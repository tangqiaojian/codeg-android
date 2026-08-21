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
    private val onEngineFailed: () -> Unit = {},
) {
    private val hostContext = context
    private var recognizer: SpeechRecognizer? = null
    val inlineAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(hostContext)

    /** @return true if inline listening started. False means the caller should use the system UI. */
    fun start(): Boolean {
        if (!inlineAvailable) return false
        release(notify = false)
        val speech = runCatching { SpeechRecognizer.createSpeechRecognizer(hostContext) }.getOrNull()
        if (speech == null) return false
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListening(true)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                release(notify = true)
                when (error) {
                    SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Unit
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> onError()
                    else -> onEngineFailed()
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
        val started = runCatching {
            speech.startListening(recognitionIntent())
            onListening(true)
        }.isSuccess
        if (!started) {
            release(notify = true)
            return false
        }
        return true
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
