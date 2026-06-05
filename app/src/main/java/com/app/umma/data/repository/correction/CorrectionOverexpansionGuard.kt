package com.app.umma.data.repository.correction

import android.util.Log
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.model.learningstate.CorrectionGrowthPolicy
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.NewExpressionLimitPolicy
import com.app.umma.domain.model.learningstate.SentenceExpansionPolicy
import javax.inject.Inject

/**
 * 교정 결과 과확장 런타임 가드 (COR-TUNE-006).
 *
 * AI가 [CorrectionGrowthPolicy]를 어겨 교정문을 과도하게 늘리거나 의미를 바꾼 경우,
 * 해당 suggestion을 결과 목록에서 drop하고 Logcat에 기록한다.
 *
 * 세 가지 검사를 순서대로 수행한다.
 *  1. meaningPreserved=false: learningSignal이 의미 변형을 보고하면 즉시 drop.
 *     meaningPreserved 누락(learningSignal=null)은 mapper가 이미 signal 전체를 null로 처리하므로
 *     여기서는 false로 확정된 경우만 다룬다 (COR-TUNE-002-FIX 유지).
 *  2. 길이비 상한 (주 신호): afterText/beforeText 길이 비율이 sentenceExpansion 정책 상한을 초과하면 drop.
 *     더 짧아진 교정(ratio ≤ 1.0)은 항상 통과 — 정상 교정이다.
 *  3. 새 표현 수 상한 (보조 신호): 공백 기준 언어에서 beforeText에 없는 신규 토큰 수가
 *     newExpressionLimit 상한을 초과하면 drop.
 *
 * 언어별 길이 측정:
 *  - 공백 없는 언어(JA 등): 공백 제거 후 코드포인트(글자) 수 — 단어 경계가 없으므로 글자로 센다.
 *  - 그 외(KO/EN/DE 등): 공백 split 단어 수, 최소 1로 보정한다.
 *
 * 이 가드가 실패해도 핵심 4필드 저장 흐름은 무손상이다.
 * 호출부([CorrectionRepositoryImpl])가 runCatching으로 감싸 가드 실패 시 원본 결과로 폴백한다.
 */
class CorrectionOverexpansionGuard @Inject constructor() {

    /**
     * suggestions 목록에서 과확장·의미 위반 항목을 drop해 반환한다.
     * 위반한 항목은 Logcat(warn)에 기록하고 결과 목록에서 제거한다.
     */
    fun filter(
        suggestions: List<CorrectionSuggestion>,
        input: GenerateSuggestionsInput
    ): List<CorrectionSuggestion> {
        val policy = input.profile.correctionPolicy
        val lang = input.langState.lang
        return suggestions.filter { suggestion -> !isViolation(suggestion, policy, lang) }
    }

    private fun isViolation(
        suggestion: CorrectionSuggestion,
        policy: CorrectionGrowthPolicy,
        lang: LangCode
    ): Boolean {
        val id = suggestion.id
        val beforeText = suggestion.beforeText
        val afterText = suggestion.afterText

        // 1. meaningPreserved=false → 의미가 바뀐 교정은 정책 위반으로 간주한다.
        if (suggestion.learningSignal?.meaningPreserved == false) {
            logDropped(id, "meaningPreserved=false")
            return true
        }

        val beforeLen = measureLength(beforeText, lang)
        val afterLen = measureLength(afterText, lang)

        // 더 짧거나 같아진 교정은 항상 통과한다 (정상 교정).
        if (afterLen <= beforeLen) return false

        // 2. 길이비 상한 검사 (주 신호)
        val ratio = afterLen.toDouble() / beforeLen
        val maxRatio = MAX_LENGTH_RATIO[policy.sentenceExpansion]
        if (maxRatio != null && ratio > maxRatio) {
            logDropped(id, "length ratio ${"%.2f".format(ratio)} > $maxRatio (${policy.sentenceExpansion})")
            return true
        }

        // 3. 새 표현 수 상한 (보조 신호 — 공백 기준 언어만 적용)
        if (lang !in SPACELESS_LANGS) {
            val newTokenCount = countNewTokens(beforeText, afterText)
            val maxNew = MAX_NEW_TOKENS[policy.newExpressionLimit]
            if (maxNew != null && newTokenCount > maxNew) {
                logDropped(id, "new tokens=$newTokenCount > $maxNew (${policy.newExpressionLimit})")
                return true
            }
        }

        return false
    }

    /**
     * 언어 특성을 반영한 텍스트 길이 측정.
     *  - 공백 없는 언어(JA 등): 공백 제거 후 코드포인트(글자) 수.
     *  - 그 외(KO/EN/DE 등): 공백 split 단어 수, 최소 1로 보정.
     */
    private fun measureLength(text: String, lang: LangCode): Int {
        return if (lang in SPACELESS_LANGS) {
            val stripped = text.replace(" ", "")
            stripped.codePointCount(0, stripped.length).coerceAtLeast(1)
        } else {
            text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size.coerceAtLeast(1)
        }
    }

    /**
     * beforeText에 없는 신규 소문자 토큰 수를 센다.
     * 공백 기준 언어 전용. 길이비가 주 신호이므로 임계값은 보수적(여유 있게) 설정한다.
     */
    private fun countNewTokens(beforeText: String, afterText: String): Int {
        val beforeTokens = beforeText.lowercase().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }.toSet()
        return afterText.lowercase().split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it !in beforeTokens }
            .size
    }

    /** 위반 drop을 Logcat에 남긴다. 순수 JVM 테스트에서도 안전하도록 runCatching으로 감싼다. */
    private fun logDropped(suggestionId: String, reason: String) {
        runCatching { Log.w(TAG, "overexpansion guard: dropped id=$suggestionId reason=$reason") }
    }

    private companion object {
        private const val TAG = "CorrectionOverexpansionGuard"

        /** 공백 없는 언어 집합. 이 언어는 글자 수로 측정하고 새 표현 수 검사는 생략한다. */
        private val SPACELESS_LANGS = setOf(LangCode.JA)

        /**
         * sentenceExpansion 정책별 길이비 상한 (주 신호).
         * 더 짧아진 교정(ratio ≤ 1.0)은 호출 전에 이미 통과 처리한다.
         * 초기 임계값은 보수적(여유 있게) 설정한다. COR-TUNE-007 실측 출력으로 보정 예정.
         */
        private val MAX_LENGTH_RATIO = mapOf(
            SentenceExpansionPolicy.NoExpansion to 1.5,
            SentenceExpansionPolicy.TinyPhraseOnly to 2.0,
            SentenceExpansionPolicy.OneShortSentence to 2.5,
            SentenceExpansionPolicy.AddSimpleReasonOrDetail to 3.0,
            SentenceExpansionPolicy.FlexibleNaturalDetail to 4.0
        )

        /**
         * newExpressionLimit 정책별 신규 토큰 수 상한 (보조 신호).
         * 문법 교정으로 인한 기능어(관사·전치사 등) 추가까지 여유 있게 허용한다.
         * 길이비가 주 신호이므로 임계값은 보수적(여유 있게) 설정한다.
         */
        private val MAX_NEW_TOKENS = mapOf(
            NewExpressionLimitPolicy.None to 3,
            NewExpressionLimitPolicy.OneTinyWord to 5,
            NewExpressionLimitPolicy.OneUsefulPhrase to 7,
            NewExpressionLimitPolicy.OneNaturalExpression to 9,
            NewExpressionLimitPolicy.OneNuanceChoice to 9
        )
    }
}
