package com.example.umma.data.repository.fake

import com.example.umma.data.repository.CorrectionRepositoryImpl
import com.example.umma.data.repository.correction.CorrectionFlashcardStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CorrectionRepository 를 fake fixture 로 빠르게 전환하기 위한 구현체입니다.
 */
@Singleton
class FakeCorrectionRepository @Inject constructor(
    flashcardStore: CorrectionFlashcardStore
) : CorrectionRepositoryImpl(flashcardStore)
