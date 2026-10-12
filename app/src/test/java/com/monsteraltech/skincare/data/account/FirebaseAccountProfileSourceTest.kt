package com.monsteraltech.skincare.data.account

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserInfo
import io.mockk.*
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FirebaseAccountProfileSourceTest {
    private val auth = mockk<FirebaseAuth>()
    private val source = FirebaseAccountProfileSource(auth)

    @Test fun `no Firebase user yields no account profile`() = runTest {
        every { auth.currentUser } returns null
        assertNull(source.getCurrentUserProfile())
    }

    @Test fun `SDK profile fields become portable account values`() = runTest {
        val google = mockk<UserInfo> { every { providerId } returns "google.com" }
        val password = mockk<UserInfo> { every { providerId } returns "password" }
        val user = mockk<FirebaseUser> {
            every { uid } returns "fixture"
            every { displayName } returns "Nombre"
            every { email } returns "fixture@example.test"
            every { photoUrl } returns null
            every { isEmailVerified } returns true
            every { metadata } returns mockk {
                every { creationTimestamp } returns 1000L
                every { lastSignInTimestamp } returns 2000L
            }
            every { providerData } returns listOf(google, password)
        }
        every { auth.currentUser } returns user
        val profile = checkNotNull(source.getCurrentUserProfile())
        assertEquals("fixture", profile.uid)
        assertEquals("Nombre", profile.displayName)
        assertEquals("fixture@example.test", profile.email)
        assertEquals(Date(1000L), profile.createdAt)
        assertEquals(Date(2000L), profile.lastSignIn)
        assertEquals(listOf("google.com", "password"), profile.providerIds)
        assertTrue(profile.emailVerified)
        assertTrue(profile.isGoogleUser)
        assertFalse(profile.isLocalProfile)
    }
}
