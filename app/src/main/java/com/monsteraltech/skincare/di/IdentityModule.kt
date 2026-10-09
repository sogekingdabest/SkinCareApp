package com.monsteraltech.skincare.di

import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.data.identity.DeviceActiveProfile
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IdentityModule {
    @Binds
    @Singleton
    abstract fun bindActiveProfile(implementation: DeviceActiveProfile): ActiveProfile
}
