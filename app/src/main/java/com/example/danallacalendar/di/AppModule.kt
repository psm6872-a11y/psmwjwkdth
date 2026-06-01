package com.example.danallacalendar.di

import android.content.Context
import com.example.danallacalendar.data.CalendarDatabase
import com.example.danallacalendar.data.EventDao
import com.example.danallacalendar.data.local.UserPreferences
import com.example.danallacalendar.data.repository.CalendarRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.firestoreSettings
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val firestore = Firebase.firestore
        val settings = firestoreSettings {
            isPersistenceEnabled = true
        }
        firestore.firestoreSettings = settings
        return firestore
    }

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }

    @Provides
    @Singleton
    fun provideCalendarDatabase(@ApplicationContext context: Context): CalendarDatabase {
        return CalendarDatabase.getDatabase(context, CoroutineScope(SupervisorJob()))
    }

    @Provides
    @Singleton
    fun provideEventDao(database: CalendarDatabase): EventDao {
        return database.eventDao()
    }

    @Provides
    @Singleton
    fun provideCalendarRepository(
        firestore: FirebaseFirestore,
        userPreferences: UserPreferences,
        eventDao: EventDao
    ): CalendarRepository {
        return CalendarRepository(firestore, userPreferences, eventDao)
    }

    @Provides
    @Singleton
    fun provideBackupRepository(
        firestore: FirebaseFirestore
    ): com.example.danallacalendar.backup.BackupRepository {
        return com.example.danallacalendar.backup.BackupRepository(firestore)
    }
}
