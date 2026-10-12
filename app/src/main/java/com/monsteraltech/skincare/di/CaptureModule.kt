package com.monsteraltech.skincare.di

import com.monsteraltech.skincare.camera.CapturedPhotoDecoder
import com.monsteraltech.skincare.camera.FileCapturedPhotoDecoder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {
    @Binds abstract fun bindPhotoDecoder(decoder: FileCapturedPhotoDecoder): CapturedPhotoDecoder
}
