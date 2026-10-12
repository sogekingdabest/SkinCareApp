package com.monsteraltech.skincare.di

import com.monsteraltech.skincare.account.AccountProfileRepository
import com.monsteraltech.skincare.account.AccountSettingsRepository
import com.monsteraltech.skincare.account.CurrentAccountProfileSource
import com.monsteraltech.skincare.account.UserProfileManager
import com.monsteraltech.skincare.account.PasswordChangeRepository
import com.monsteraltech.skincare.data.auth.FirebasePasswordChangeRepository
import com.monsteraltech.skincare.data.auth.AuthRepository
import com.monsteraltech.skincare.login.PasswordResetRepository
import com.monsteraltech.skincare.data.account.FirebaseAccountProfileSource
import com.monsteraltech.skincare.data.account.LocalAccountSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountDataModule {
    @Binds abstract fun bindSettings(repository: LocalAccountSettingsRepository): AccountSettingsRepository
    @Binds abstract fun bindProfile(source: FirebaseAccountProfileSource): CurrentAccountProfileSource
    @Binds abstract fun bindAccount(repository: UserProfileManager): AccountProfileRepository
    @Binds abstract fun bindPassword(repository: FirebasePasswordChangeRepository): PasswordChangeRepository
    @Binds abstract fun bindPasswordReset(repository: AuthRepository): PasswordResetRepository
}
