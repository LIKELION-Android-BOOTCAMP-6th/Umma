package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.correction.CorrectionSuggestion
import java.util.Locale
import javax.inject.Inject

/**
 * 교정/저장 경계에서 공통으로 쓰는 최소 안전 정책이다.
 *
 * 이 정책은 "명백히 유해한" 문장만 차단하고, 애매한 일반 학습 문장은 기본 허용한다.
 * 더 넓은 안전 판단은 프롬프트 지시와 모델 응답 후처리가 보조한다.
 */
class CorrectionSafetyPolicy @Inject constructor() {

    fun isBlocked(candidate: CorrectionCandidate): Boolean {
        return isBlockedText(
            candidate.sourceText,
            candidate.assistantContext.orEmpty()
        )
    }

    fun isBlocked(suggestion: CorrectionSuggestion): Boolean {
        return isBlockedText(
            suggestion.beforeText,
            suggestion.nativeText,
            suggestion.afterText,
            suggestion.explanation
        )
    }

    private fun isBlockedText(vararg parts: String): Boolean {
        val text = parts
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .lowercase(Locale.ROOT)

        if (text.isBlank()) return false

        return selfHarmPatterns.any { it.containsMatchIn(text) } ||
            childSexualPatterns.any { it.containsMatchIn(text) } ||
            hateViolencePatterns.any { it.containsMatchIn(text) } ||
            crimeFraudPatterns.any { it.containsMatchIn(text) } ||
            explicitSexualPatterns.any { it.containsMatchIn(text) } ||
            dangerousAdvicePatterns.any { it.containsMatchIn(text) }
    }

    private companion object {
        val selfHarmPatterns = listOf(
            Regex("""\b(kill myself|suicide|self harm|cut myself|overdose|hang myself)\b"""),
            Regex("""자살|자해|죽고\s?싶|목매|과다복용""")
        )

        val childSexualPatterns = listOf(
            Regex("""\b(child porn|underage sex|minor sexual|sexualized child)\b"""),
            Regex("""아동\s*성|미성년자\s*성|아청물""")
        )

        val hateViolencePatterns = listOf(
            Regex("""\b(kill all|lynch|ethnic cleansing|hate crime)\b"""),
            Regex("""혐오|집단\s*학살|죽여버리""")
        )

        val crimeFraudPatterns = listOf(
            Regex("""\b(make a bomb|build a bomb|how to steal|rob a bank|phishing|credit card fraud|scam people)\b"""),
            Regex("""폭탄\s*(만들|제조)|사기|피싱|훔치|강도""")
        )

        val explicitSexualPatterns = listOf(
            Regex("""\b(blowjob|rape porn|hardcore sex|incest porn)\b"""),
            Regex("""강간|노골적\s*성행위|성적\s*학대""")
        )

        val dangerousAdvicePatterns = listOf(
            Regex("""\b(prescribe me|diagnose me|how much .* to die|illegal tax evasion|guaranteed investment advice)\b"""),
            Regex("""처방해|진단해|죽으려면|탈세|확실한\s*투자\s*조언""")
        )
    }
}
