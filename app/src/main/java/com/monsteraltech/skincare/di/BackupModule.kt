package com.monsteraltech.skincare.di

import com.monsteraltech.skincare.backup.BackupSettingsStore
import com.monsteraltech.skincare.backup.LocalBackupSettingsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds abstract fun bindSettings(store: LocalBackupSettingsStore): BackupSettingsStore
}
