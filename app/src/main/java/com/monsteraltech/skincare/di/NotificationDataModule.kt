package com.monsteraltech.skincare.di

import com.monsteraltech.skincare.notifications.AndroidNotificationScheduleService
import com.monsteraltech.skincare.notifications.NotificationScheduleService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationDataModule {
    @Binds abstract fun bindScheduler(service: AndroidNotificationScheduleService): NotificationScheduleService
}
