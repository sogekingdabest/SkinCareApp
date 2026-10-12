package com.monsteraltech.skincare.testing

import com.monsteraltech.skincare.camera.CaptureDraftStore
import com.monsteraltech.skincare.data.auth.AuthRepository
import com.monsteraltech.skincare.data.identity.ActiveProfile
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Access to the target APK's real graph for instrumentation. Excluded from release. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TestingDependencies {
    fun auth(): AuthRepository
    fun profile(): ActiveProfile
    fun drafts(): CaptureDraftStore
}
