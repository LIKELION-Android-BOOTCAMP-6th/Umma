package com.example.umma.di

import android.content.Context
import androidx.room.Room
import com.example.umma.data.source.local.CorrectionFlashcardDao
import com.example.umma.data.source.local.CorrectionFlashcardDatabase
import com.example.umma.data.source.local.SessionMemoryDatabase
import com.example.umma.data.source.local.SessionMetadataDao
import com.example.umma.data.source.local.SessionTurnDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSessionMemoryDatabase(
        @ApplicationContext context: Context
    ): SessionMemoryDatabase {
        return Room.databaseBuilder(
            context,
            SessionMemoryDatabase::class.java,
            "umma_session_memory_db"
        ).fallbackToDestructiveMigration(false).build()
    }

    @Provides
    fun provideSessionTurnDao(database: SessionMemoryDatabase): SessionTurnDao {
        return database.sessionTurnDao()
    }

    @Provides
    fun provideSessionMetadataDao(database: SessionMemoryDatabase): SessionMetadataDao {
        return database.sessionMetadataDao()
    }

    @Provides
    @Singleton
    fun provideCorrectionFlashcardDatabase(
        @ApplicationContext context: Context
    ): CorrectionFlashcardDatabase {
        return Room.databaseBuilder(
            context,
            CorrectionFlashcardDatabase::class.java,
            "umma_correction_flashcard_db"
        ).fallbackToDestructiveMigration(false).build()
    }

    @Provides
    fun provideCorrectionFlashcardDao(
        database: CorrectionFlashcardDatabase
    ): CorrectionFlashcardDao {
        return database.correctionFlashcardDao()
    }
}
