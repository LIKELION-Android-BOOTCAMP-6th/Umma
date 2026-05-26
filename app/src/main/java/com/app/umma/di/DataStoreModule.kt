package com.app.umma.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 학습 상태 전용 Preferences DataStore를 제공한다.
 *
 * LS-007의 초기 저장 계약은 앱 세션에서 실제로 값을 보관해야 하므로
 * Repository가 의존할 저장소 기반을 별도로 만든다.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideLearningStateDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        // 초기 저장값과 이후 local preload가 같은 파일을 바라보도록 고정한다.
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("learning_state.preferences_pb") }
        )
    }
}
