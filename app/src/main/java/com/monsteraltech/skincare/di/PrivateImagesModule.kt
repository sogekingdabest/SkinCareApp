package com.monsteraltech.skincare.di

import android.content.Context
import com.monsteraltech.skincare.data.image.FilePrivateImageStore
import com.monsteraltech.skincare.data.image.PrivateImageStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrivateImagesModule {
    @Provides
    @Singleton
    fun providePrivateImageStore(@ApplicationContext context: Context): PrivateImageStore =
        FilePrivateImageStore(context.noBackupFilesDir, context.filesDir, context.cacheDir)
}
