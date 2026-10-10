package com.monsteraltech.skincare.di

import com.monsteraltech.skincare.analysis.AnalysisEngines
import com.monsteraltech.skincare.analysis.AsyncImageProcessor
import com.monsteraltech.skincare.analysis.ImageAnalysisRunner
import com.monsteraltech.skincare.analysis.NativeAnalysisEngines
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class AnalysisModule {
    @Binds abstract fun bindRunner(processor: AsyncImageProcessor): ImageAnalysisRunner
    @Binds abstract fun bindEngines(engines: NativeAnalysisEngines): AnalysisEngines
}
