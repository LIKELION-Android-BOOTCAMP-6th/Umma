package com.app.umma.data.source.remote

import com.app.umma.data.model.learningstate.DashSummaryDto
import com.app.umma.data.model.learningstate.FlashcardSummaryDto
import com.app.umma.data.model.learningstate.LangStateDto
import com.app.umma.data.model.learningstate.SessionSummaryDto
import com.app.umma.data.model.learningstate.UserLangPrefDto

/**
 * Firebase(Firestore) 에서 LearningState 를 읽고 쓰는 데이터소스.
 *
 * SSOT: DASH-001_Dashboard_Entry.md (AC 1, 6)
 * AC 1: Dashboard 진입 시 DashSummary fetch가 수행된다.
 * AC 6: Firebase background sync가 수행된다.
 * LS-008: local-first 변경분은 pending marker 기준으로 Firestore에 write-back 된다.
 *
 * 왜 별도 인터페이스인가:
 *  - FirestoreRepository<T, ID> 는 단일 문서 CRUD 베이스라 LearningState 의
 *    sub-collection 묶음 구조 (userPref / langStates / dashSummaries / ...) 와
 *    안 맞음.
 *  - LearningStateRepoImpl 이 Firestore 의 구체 클래스를 직접 의존하지 않게
 *    한 겹 추상화.
 *
 * fetch는 remote → local 복구, sync는 local pending → remote write-back 경계를 담당한다.
 */
interface LearningStateRemoteDataSource {
    /**
     * userUid 기준 최신 LearningState 를 fetch.
     *
     * - 네트워크 실패 / 인증 만료 / 문서 부재 → throw
     * - 부분 데이터(예: dashSummaries 만 있고 sessionSummaries 없음) 도 그대로 반환.
     *   merge 정책은 호출자가 결정.
     */
    suspend fun fetch(userUid: String): LearningStateRemote

    /**
     * local-first로 저장된 LearningState 변경분을 Firestore에 반영한다.
     *
     * 이 호출은 사용자 완료 기준이 아니라 background write-back 경계다.
     * 실패하면 호출자는 local snapshot을 유지하고 pending sync 표시를 남겨 다음 sync에서 재시도한다.
     */
    suspend fun sync(userUid: String, update: LearningStateRemoteUpdate): Result<Unit>
}

/**
 * Firebase 응답을 묶은 DTO.
 *
 * 이미 LearningStateDtos.kt 의 DTO 들을 그대로 재사용해서
 * toDomain() 한 줄로 Domain 변환 가능.
 */
data class LearningStateRemote(
    val userPref: UserLangPrefDto?,
    val langStates: List<LangStateDto>,
    val dashSummaries: List<DashSummaryDto>,
    val sessionSummaries: List<SessionSummaryDto>,
    val flashcardSummaries: List<FlashcardSummaryDto>
)

/**
 * Firestore에 써야 하는 local 변경 묶음.
 *
 * null/empty 값은 "이번 sync에서 변경 없음"을 의미한다.
 * 전체 GlobalLangState를 무조건 쓰지 않고 pending key에 걸린 항목만 보내기 위한 DTO 묶음이다.
 */
data class LearningStateRemoteUpdate(
    val userPref: UserLangPrefDto? = null,
    val langStates: List<LangStateDto> = emptyList(),
    val dashSummaries: List<DashSummaryDto> = emptyList(),
    val sessionSummaries: List<SessionSummaryDto> = emptyList(),
    val flashcardSummaries: List<FlashcardSummaryDto> = emptyList()
) {
    val isEmpty: Boolean
        get() = userPref == null &&
            langStates.isEmpty() &&
            dashSummaries.isEmpty() &&
            sessionSummaries.isEmpty() &&
            flashcardSummaries.isEmpty()
}
