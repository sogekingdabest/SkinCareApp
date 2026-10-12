package com.monsteraltech.skincare.account

interface CurrentAccountProfileSource {
    suspend fun getCurrentUserProfile(): UserInfo?
}
