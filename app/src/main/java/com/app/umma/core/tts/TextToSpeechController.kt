package com.app.umma.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.app.umma.domain.model.learningstate.LangCode
import java.util.Locale
import javax.inject.Inject

class TextToSpeechController @Inject constructor(
    context: Context
) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var utteranceSequence = 0L
    private var currentUtteranceId: String? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                Log.d("ummaDev", "TextToSpeechController - 초기화 완료")
            } else {
                Log.d("ummaDev", "TextToSpeechController - 초기화 실패: $status")
            }
        }
    }

    fun setLanguage(langCode: LangCode): Boolean {
        val locale = when (langCode) {
            LangCode.KO -> Locale.KOREAN
            LangCode.EN -> Locale.ENGLISH
            LangCode.JA -> Locale.JAPANESE
            LangCode.DE -> Locale.GERMAN
            else -> return false
        }
        val result = tts?.setLanguage(locale) ?: return false
        return when (result) {
            TextToSpeech.LANG_MISSING_DATA,
            TextToSpeech.LANG_NOT_SUPPORTED -> false

            else -> true
        }
    }

    fun speak(
        text: String,
        onComplete: () -> Unit = {},
        onInterrupted: () -> Unit = {},
        onFailed: () -> Unit = {},
    ): Boolean {
        if (!isReady) {
            Log.d("ummaDev", "TextToSpeechController - 초기화 전 speak 무시")
            return false
        }
        val engine = tts ?: return false
        val utteranceId = "umma-utterance-${++utteranceSequence}"
        currentUtteranceId = utteranceId
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId != currentUtteranceId) return
                onComplete()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId != currentUtteranceId) return
                onFailed()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != currentUtteranceId) return
                onFailed()
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId != currentUtteranceId) return
                onInterrupted()
            }
        })
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.d("ummaDev", "TextToSpeechController - speak 실패")
            currentUtteranceId = null
            return false
        }
        return true
    }

    fun stop() {
        currentUtteranceId = null
        tts?.stop()
    }

    fun shutdown() {
        currentUtteranceId = null
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
