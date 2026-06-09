package com.app.umma.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.app.umma.data.source.local.ChatUsageDao
import com.app.umma.data.source.local.ChatUsageDatabase
import com.app.umma.data.source.local.CorrectionFlashcardDao
import com.app.umma.data.source.local.CorrectionFlashcardDatabase
import com.app.umma.data.source.local.CorrectionSuggestionCacheDao
import com.app.umma.data.source.local.CorrectionSuggestionCacheDatabase
import com.app.umma.data.source.local.SessionMemoryDatabase
import com.app.umma.data.source.local.SessionMetadataDao
import com.app.umma.data.source.local.SessionTurnDao
import com.app.umma.data.source.local.StatisticsHistoryDao
import com.app.umma.data.source.local.StatisticsHistoryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_1_2_CORRECTION_FLASHCARD = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE correction_flashcards ADD COLUMN lastReviewRating TEXT"
        )
        db.execSQL(
            "ALTER TABLE correction_flashcards ADD COLUMN lastReviewedAt INTEGER"
        )
    }
}

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
        )
            .addMigrations(MIGRATION_1_2_CORRECTION_FLASHCARD)
            .fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    fun provideCorrectionFlashcardDao(
        database: CorrectionFlashcardDatabase
    ): CorrectionFlashcardDao {
        return database.correctionFlashcardDao()
    }

    @Provides
    @Singleton
    fun provideCorrectionSuggestionCacheDatabase(
        @ApplicationContext context: Context
    ): CorrectionSuggestionCacheDatabase {
        return Room.databaseBuilder(
            context,
            CorrectionSuggestionCacheDatabase::class.java,
            "umma_correction_suggestion_cache_db"
        ).fallbackToDestructiveMigration(false).build()
    }

    @Provides
    fun provideCorrectionSuggestionCacheDao(
        database: CorrectionSuggestionCacheDatabase
    ): CorrectionSuggestionCacheDao {
        return database.correctionSuggestionCacheDao()
    }

    @Provides
    @Singleton
    fun provideStatisticsHistoryDatabase(
        @ApplicationContext context: Context
    ): StatisticsHistoryDatabase {
        return Room.databaseBuilder(
            context,
            StatisticsHistoryDatabase::class.java,
            "umma_statistics_history_db"
        ).fallbackToDestructiveMigration(false).build()
    }

    @Provides
    fun provideStatisticsHistoryDao(
        database: StatisticsHistoryDatabase
    ): StatisticsHistoryDao {
        return database.statisticsHistoryDao()
    }

    @Provides
    @Singleton
    fun provideChatUsageDatabase(
        @ApplicationContext context: Context
    ): ChatUsageDatabase {
        // Realtime usage는 비용 분석용 운영 데이터라 schema 변화 가능성이 높다.
        // 다른 Room DB와 분리해 usage migration이 SessionMemory/Statistics 저장소에 영향을 주지 않게 한다.
        return Room.databaseBuilder(
            context,
            ChatUsageDatabase::class.java,
            "umma_chat_usage_db"
        ).fallbackToDestructiveMigration(false).build()
    }

    @Provides
    fun provideChatUsageDao(
        database: ChatUsageDatabase
    ): ChatUsageDao {
        return database.chatUsageDao()
    }
}
