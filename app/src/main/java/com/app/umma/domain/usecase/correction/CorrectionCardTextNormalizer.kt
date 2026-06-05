package com.app.umma.domain.usecase.correction

/**
 * 교정 Flashcard 텍스트 비교를 위한 정규화 유틸입니다 (COR-TUNE-007).
 *
 * 소문자화 + 내부 공백 collapse + 양끝 구두점/공백 제거를 적용한다.
 * 대소문자·문장부호·공백만 다른 텍스트를 동일하게 취급해
 * [PrepareSaveRequestUseCase]의 저품질 필터와 배치 내 중복 제거에서 사용한다.
 */
internal object CorrectionCardTextNormalizer {

    /**
     * 비교용 정규화: 대소문자·문장부호·공백 차이를 흡수한다.
     *
     * 적용 순서: 소문자화 → 내부 공백 collapse → 양끝 공백/구두점 제거.
     * 원본 텍스트를 변형하지 않으며, 오직 비교용 키 생성에만 사용한다.
     */
    fun normalize(text: String): String =
        text.lowercase()
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .trimEnd { it.isWhitespace() || it in TRIM_PUNCTUATION }
            .trimStart { it.isWhitespace() || it in TRIM_PUNCTUATION }

    /** 양끝에서 제거할 구두점. 문장 내부 구두점은 건드리지 않는다. */
    private val TRIM_PUNCTUATION = setOf('.', ',', '!', '?', '。', '！', '？', '、', '…')

    /** 내부 공백 collapse 용 정규식. */
    private val WHITESPACE_REGEX = Regex("\\s+")
}
