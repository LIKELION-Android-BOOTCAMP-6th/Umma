package com.app.umma.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        // ViewModel이 Dispatchers.Main을 직접 쓰는 테스트에서, 실제 메인 스레드 대신
        // 제어 가능한 test dispatcher를 주입해 비동기 상태 전이를 안정적으로 검증한다.
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        // 테스트가 끝나면 Main dispatcher를 원복해서 다른 테스트에 영향이 남지 않게 한다.
        Dispatchers.resetMain()
    }
}
