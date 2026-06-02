package com.app.umma.domain.usecase.user

import com.app.umma.domain.model.learningstate.LangCode
import java.util.Locale
import javax.inject.Inject

/**
 * 기기 시스템 언어 반환
 * 미지원 언어는 LangCode.EN 으로 반환
 */
class GetSystemLanguageUseCase @Inject constructor() {
    operator fun invoke(): LangCode {
        val systemLanguageCode = Locale.getDefault().language
        return LangCode.fromCode(systemLanguageCode) ?: LangCode.EN
    }
}