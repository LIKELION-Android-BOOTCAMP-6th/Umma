package com.example.umma.data.source.remote

import com.example.umma.data.model.learningstate.DashSummaryDto
import com.example.umma.data.model.learningstate.FlashcardSummaryDto
import com.example.umma.data.model.learningstate.LangStateDto
import com.example.umma.data.model.learningstate.SessionSummaryDto
import com.example.umma.data.model.learningstate.UserLangPrefDto

/**
 * Firebase(Firestore) 에서 LearningState 를 fetch 하는 데이터소스.
 *
 * SSOT: DASH-001_Dashboard_Entry.md (AC 1, 6)
 * AC 1: Dashboard 진입 시 DashSummary fetch가 수행된다.
 * AC 6: Firebase background sync가 수행된다.
 *
 * 왜 별도 인터페이스인가:
 *  - FirestoreRepository<T, ID> 는 단일 문서 CRUD 베이스라 LearningState 의
 *    sub-collection 묶음 구조 (userPref / langStates / dashSummaries / ...) 와
 *    안 맞음.
 *  - LearningStateRepoImpl 이 Firestore 의 구체 클래스를 직접 의존하지 않게
 *    한 겹 추상화.
 *
 * 본 구현(LearningStateRemoteDataSourceImpl) 은 Firestore 스키마 확정 후 작성.
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