package com.app.umma.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * 알림 설정 전용 DataStore 제공 모듈.
 */
@Module
@InstallIn(SingletonComponent::class)
object NotificationDataStoreModule {

    @Provides
    @Singleton
    @Named("notificationSettingsDataStore")
    fun provideNotificationSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("notification_settings.preferences_pb") }
        )
    }
}
