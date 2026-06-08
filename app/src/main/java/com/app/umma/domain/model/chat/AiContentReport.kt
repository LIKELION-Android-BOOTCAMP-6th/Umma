package com.app.umma.domain.model.chat

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker

/**
 * 운영용 AI 생성 콘텐츠 신고 모델입니다.
 *
 * 개발용 prompt review는 프롬프트 튜닝/QA를 위한 세션 단위 도구이고,
 * 이 모델은 Google Play 정책 대응을 위해 실제 사용자가 부적절한 AI 응답을 신고하는 저장 계약입니다.
 * 그래서 SessionMemory나 devtools 저장소와 섞지 않고 별도 domain 모델로 둡니다.
 */
data class AiContentReport(
    // 같은 AI turn을 중복 제출해도 원격 문서를 하나로 식별하기 위한 report id.
    val reportId: String,
    // 신고한 Firebase 사용자 uid. 운영 검토와 abuse 방지를 위해 필수다.
    val userId: String,
    // 신고 대상 AI 응답이 발생한 Chat app session id.
    val sessionId: String,
    // 신고 대상 AI final transcript turn id.
    val reportedTurnId: String,
    // 신고된 AI final transcript 원문.
    val reportedAiText: String,
    // 신고된 AI 응답 직전 사용자 final transcript. 맥락이 없으면 null로 둔다.
    val previousUserText: String?,
    // 운영자가 문제 응답을 판단할 수 있는 최소 맥락. 전체 세션 대신 최근 6턴만 저장한다.
    val contextTurns: List<AiContentReportContextTurn>,
    // 사용자가 기준으로 삼는 언어. 신고 시점의 학습 환경 재현에 필요하다.
    val primaryLang: LangCode,
    // 현재 Chat 세션의 학습 대상 언어.
    val selectedLang: LangCode,
    // 사용자가 선택한 필수 신고 사유.
    val reasonCategory: AiContentReportReasonCategory,
    // 사용자가 추가로 적은 선택 메모. 비어 있으면 null로 저장한다.
    val detailNote: String?,
    // 신고 접수 시각. Firestore 서버 시간이 아니라 앱 이벤트 추적용 클라이언트 시각이다.
    val reportedAt: Long,
    // 운영 큐에서의 처리 상태. 최초 접수는 항상 New다.
    val status: AiContentReportStatus = AiContentReportStatus.New,
    // 앱 버전. 같은 문제를 특정 배포 버전과 묶어 분석하기 위한 값이다.
    val appVersion: String,
    // AI model 식별자. Realtime 모델 교체 시 문제 응답을 구분하기 위한 값이다.
    val modelVersion: String,
    // Chat system prompt 구조 버전.
    val promptVersion: String,
    // 같은 promptVersion 안의 미세 조정 revision.
    val promptRevision: String
)

/**
 * 신고 시점 주변 final transcript snapshot입니다.
 *
 * 화면 자막과 같은 final-only 정책을 따르며, partial transcript는 흔들릴 수 있어 저장하지 않습니다.
 */
data class AiContentReportContextTurn(
    // SessionMemory와 연결 가능한 final turn id.
    val turnId: String,
    // 해당 turn이 속한 Chat app session id.
    val sessionId: String,
    // USER/AI 역할. 운영 검토자가 대화 흐름을 읽기 위해 필요하다.
    val role: TurnSpeaker,
    // final transcript 원문.
    val text: String,
    // final transcript 생성 시각.
    val createdAt: Long
)

/**
 * 사용자가 선택하는 운영 신고 사유입니다.
 *
 * Google Play 정책 대응에 필요한 범주만 두고, 상세 분류는 운영 검토 단계에서 확장합니다.
 */
enum class AiContentReportReasonCategory {
    HarmfulDangerous,
    HateHarassment,
    SexualInappropriate,
    IllegalFraud,
    SelfHarm,
    Other
}

/**
 * 운영 검토 상태입니다.
 *
 * 초기 구현은 앱에서 신고 접수만 담당하므로 생성 가능한 상태를 New 하나로 제한합니다.
 * Reviewed/Actioned/Dismissed 같은 운영 처리 상태는 관리자 도구가 생길 때 별도 계약으로 확장합니다.
 */
enum class AiContentReportStatus {
    New
}
