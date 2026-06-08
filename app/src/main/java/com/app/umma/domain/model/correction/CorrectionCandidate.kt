package com.app.umma.domain.model.correction

import com.app.umma.domain.model.learningstate.LangCode

/**
 * Correction 결과 생성을 위해 내부적으로 추출한 후보 문장 단위.
 *
 * 이 모델은 화면에 직접 노출되지 않고, 이후 교정 결과 생성 UseCase의 입력으로만 사용한다.
 */
data class CorrectionCandidate(
    // 후보 식별자. 선택/추적용으로 사용한다.
    val id: String,
    // 후보가 속한 현재 선택 언어.
    val lang: LangCode,
    // 원본 turn 식별자. 현재는 turn id 가 없을 수 있어 nullable 로 둔다.
    val sourceTurnId: String? = null,
    // 원본 user turn의 순서.
    val sourceTurnIndex: Int,
    // 교정 대상이 되는 원본 문장.
    val sourceText: String,
    // 의미 파악을 돕는 짧은 assistant 문맥.
    val assistantContext: String? = null
)
