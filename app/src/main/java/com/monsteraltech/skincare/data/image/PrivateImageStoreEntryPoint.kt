package com.monsteraltech.skincare.data.image

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Puente para adaptadores y utilidades de vistas aún construidos por el framework. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrivateImageStoreEntryPoint {
    fun imageStore(): PrivateImageStore
}

fun privateImageStore(context: Context): PrivateImageStore =
    EntryPointAccessors.fromApplication(context.applicationContext, PrivateImageStoreEntryPoint::class.java)
        .imageStore()
