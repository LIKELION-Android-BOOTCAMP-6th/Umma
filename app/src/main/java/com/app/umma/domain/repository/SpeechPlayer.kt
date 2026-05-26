package com.app.umma.domain.repository

import com.app.umma.domain.model.learningstate.LangCode

/**
 * 텍스트 음성 합성 및 재생(TTS)을 담당하는 인프라스트럭처 계약입니다.
 * 
 * 특정 기술(Android TTS, Google Cloud TTS 등)에 대한 UI 계층의 결합도를 낮추기 위해 정의되었습니다.
 * SRS 복습 시 뒷면의 정답 문장(외국어)을 소리로 들려줄 때 사용됩니다.
 */
interface SpeechPlayer {
    /**
     * 전달받은 텍스트를 지정된 언어의 억양과 목소리로 재생합니다.
     * 
     * @param text 음성으로 변환할 문자열
     * @param lang 인식할 언어 코드 (언어에 맞는 엔진 설정용)
     */
    fun play(text: String, lang: LangCode)

    /**
     * 현재 진행 중인 모든 음성 출력을 즉시 중단합니다.
     */
    fun stop()

    /**
     * TTS 엔진 자원을 시스템에 반환하고 리스너를 해제합니다.
     * 주로 화면(Activity/ViewModel)의 생명주기가 종료될 때 호출됩니다.
     */
    fun release()
}
