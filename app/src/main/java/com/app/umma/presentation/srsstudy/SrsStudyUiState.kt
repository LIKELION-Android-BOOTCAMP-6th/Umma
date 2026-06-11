package com.app.umma.presentation.srsstudy

import com.app.umma.domain.model.flashcard.Flashcard
import com.app.umma.domain.model.learningstate.LangCode

data class SrsStudyUiState(
    val isLoading: Boolean = true,
    // 언어 정보 불러오기 실패 시 true, "다시 시도" 버튼 표시
    val hasInitError: Boolean = false,
    // GlobalLangState 현재 학습 언어
    val selectedLearningLanguage: LangCode? = null,

    // 오늘 복습할 카드 목록
    val cards: List<Flashcard> = emptyList(),
    // 현재 화면의 카드 번호(시작: 0번)
    val currentCardIndex: Int = 0,
    // false = 앞면(모국어 번역, 내가 말했던 틀린 답), true = 뒷면(듣기 버튼, 정답, Grammar Note)
    val isCardFlipped: Boolean = false,
    // 모든 카드 끝냈을 시 true -> 완료 화면
    val isDone: Boolean = false,
    // Room 저장 요청을 보낸 후 응답 오기 전 까지 true
    // true -> 평가 버튼 비활성화, 중복 시도 방지
    val isSaving: Boolean = false,
    // 저장 실패 시 true -> 화면에 안내 표시
    val hasSaveError: Boolean = false,
    // 발음 재생 중일 때 true -> 버튼 색상 변경
    val isSpeaking: Boolean = false,
    // 완료 화면에 표시할 학습 카드 수
    // 덱이 처음 로드된 시점의 카드 수로 고정 (Again은 세션 내 카드를 늘리지 않음)
    val studiedCardCount: Int = 0,
    // 평가 버튼에 표시할 다음 복습 간격 문자열
    // 카드가 바뀔 때마다 ViewModel이 실제 스케줄 계산 결과로 표기
    // Again 은 현재 세션에 재노출하지 않고 1분 뒤 재학습 대상이 되므로 "재학습" 고정, 나머지는 동적 계산값
    val againLabel: String = "재학습",
    val hardLabel: String = "1일",
    val goodLabel: String = "2일",
    val easyLabel: String = "4일",
) {
    /** 지금 보고 있는 카드, 없으면 null */
    val currentCard: Flashcard?
        get() = cards.getOrNull(currentCardIndex)

    val totalCards: Int
        get() = cards.size

    val remainingCards: Int
        get() = (totalCards - currentCardIndex).coerceAtLeast(0)
}