package com.example.umma.domain.repository

import kotlinx.coroutines.flow.Flow


/**
 * 모든 Firestore 리포지토리가 공통으로 구현해야 하는 기본 CRUD 인터페이스입니다.
 * 성공과 실패는 Kotlin 내장 [Result]를 통해 반환됩니다.
 *
 * @param T 도메인 계층에서 사용하는 비즈니스 모델 (Model)
 * @param ID Firestore 문서의 식별자 타입 (주로 String)
 */
interface FirestoreRepository<T, ID> {

    suspend fun getDocument(id: ID): Result<T>

    fun getAllDocumentsStream(): Flow<Result<List<T>>>

    fun getDocumentAsStream(id: ID): Flow<Result<T>>

    suspend fun saveDocument(item: T): Result<Unit>

    suspend fun deletedDocument(id: ID): Result<Unit>
}