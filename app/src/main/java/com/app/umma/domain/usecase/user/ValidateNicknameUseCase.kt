package com.app.umma.domain.usecase.user

import javax.inject.Inject

/**
 * 닉네임 입력값 검사 (공백 불가, 2~10자)
 * true 통과
 */
class ValidateNicknameUseCase @Inject constructor() {
    operator fun invoke(nickname: String): Boolean {
        return nickname.isNotBlank() && nickname.length in 2..10
    }
}