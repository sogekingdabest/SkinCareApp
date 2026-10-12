package com.monsteraltech.skincare.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.monsteraltech.skincare.utils.ThemeManager
import com.monsteraltech.skincare.analysis.comparison.LocalObservationComparisonPreparer
import com.monsteraltech.skincare.analysis.comparison.LesionPairRegistrationEngine
import com.monsteraltech.skincare.analysis.comparison.ObservationComparisonPreparer
import com.monsteraltech.skincare.analysis.comparison.OpenCvLesionPairRegistrationEngine
import com.monsteraltech.skincare.body.mole.data.repository.LocalMoleObservationRepository
import com.monsteraltech.skincare.body.mole.data.repository.LocalMoleReadRepository
import com.monsteraltech.skincare.body.mole.domain.repository.MoleReadRepository
import com.monsteraltech.skincare.body.mole.data.local.ObservationMigration
import com.monsteraltech.skincare.body.mole.data.local.SkinCareDatabase
import com.monsteraltech.skincare.body.mole.domain.repository.MoleObservationRepository
import com.monsteraltech.skincare.data.SessionManager
import javax.inject.Singleton

/**
 * Providers raíz de SDK y almacenamiento. Los bindings de cada responsabilidad
 * se declaran en sus módulos de datos y las clases propias usan constructor injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager =
        SessionManager.getInstance(context)

    @Provides
    @Singleton
    fun provideThemeManager(): ThemeManager = ThemeManager.getInstance()

    @Provides
    @Singleton
    fun provideSkinCareDatabase(
        @ApplicationContext context: Context
    ): SkinCareDatabase = Room.databaseBuilder(
        context,
        SkinCareDatabase::class.java,
        "skincare_private.db"
    ).addMigrations(ObservationMigration.FROM_1_TO_2).build()

    @Provides
    @Singleton
    fun provideMoleObservationRepository(
        repository: LocalMoleObservationRepository
    ): MoleObservationRepository = repository

    @Provides
    fun provideMoleReadRepository(repository: LocalMoleReadRepository): MoleReadRepository = repository

    @Provides
    @Singleton
    fun provideObservationComparisonPreparer(
        preparer: LocalObservationComparisonPreparer
    ): ObservationComparisonPreparer = preparer

    @Provides
    @Singleton
    fun provideLesionPairRegistrationEngine(
        engine: OpenCvLesionPairRegistrationEngine
    ): LesionPairRegistrationEngine = engine

}
