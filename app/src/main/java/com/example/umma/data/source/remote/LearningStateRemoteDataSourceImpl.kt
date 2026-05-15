package com.example.umma.data.source.remote

import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TODO: Firestore 스키마 확정 후 구현.
 *
 * 예시 스키마 가정:
 *  users/{uid}/learningState/userPref       (단일 문서)
 *  users/{uid}/learningState/langStates     (컬렉션, doc id = langCode)
 *  users/{uid}/learningState/dashSummaries  (컬렉션, doc id = langCode)
 *  ...
 *
 * 실제 스키마에 맞춰 fetch 로직 작성.
 */
@Singleton
class LearningStateRemoteDataSourceImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : LearningStateRemoteDataSource {

    override suspend fun fetch(userUid: String): LearningStateRemote {
        TODO("Firestore 스키마 확정 후 구현 — DASH-001 후속")
    }
}