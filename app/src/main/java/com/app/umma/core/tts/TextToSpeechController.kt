package com.app.umma.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.app.umma.domain.model.learningstate.LangCode
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextToSpeechController @Inject constructor(
    private val context: Context
) {
    // Android TTS 인스턴스 (초기화 전 null)
    private var tts: TextToSpeech? = null

    // TTS 엔진 초기화 완료 여부 - false 동안 speak 호출 무시
    private var isReady = false

    init {
        // TTS 엔진은 비동기로 초기화, 준비가 끝나면 콜백(람다)으로 알려줌
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                Log.d("ummaDev", "TextToSpeechController - 초기화 완료")
            } else {
                // 기기에 TTS 엔진이 없거나 실패한 경우
                Log.d("ummaDev", "TextToSpeechController - 초기화 실패: $status")
            }
        }
    }

    /**
     * 카드 언어에 맞게 TTS 언어 변경
     * 성공: true, 언어 미지원 또는 데이터 없음: false
     */
    fun setLanguage(langCode: LangCode): Boolean {
        val locale = when (langCode) {
            LangCode.KO -> Locale.KOREAN
            LangCode.EN -> Locale.ENGLISH
            LangCode.JA -> Locale.JAPANESE
            else -> return false
        }
        // tts가 null이면 초기화 전. 바로 false 반환
        val result = tts?.setLanguage(locale) ?: return false
        return when (result) {
            // 실패: 언어 데이터가 없거나 또는 지원하지 않는 언어일 때
            TextToSpeech.LANG_MISSING_DATA,
            TextToSpeech.LANG_NOT_SUPPORTED -> false

            else -> true
        }
    }

    /**
     * text 를 소리내어 읽음
     * 이미 재생 중이면 QUEUE_FLUSH로 기존 재생 중단 후 새 텍스트 재생
     */
    fun speak(text: String) {
        if (!isReady) {
            Log.d("ummaDev", "TextToSpeechController - 초기화 전 speak 무시")
            return
        }
        // QUEUE_FLUSH: 재생 중이던 것 바로 중단, 새 텍스트 재생
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    /** 현재 재생 중단 */
    fun stop() {
        tts?.stop()
    }

    /**
     * ViewModel이 사라질 때 호출 -> 시스템 자원 해제
     */
    fun shutdown() {
        tts?.shutdown()
        tts = null
        isReady = false
    }
}