package es.monsteraltech.skincare_tfm.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionDataTest {

    @Test
    fun `SessionData should be created with default values`() {
        // When
        val sessionData = SessionData()
        
        // Then
        assertEquals("", sessionData.userId)
        assertNull(sessionData.email)
        assertNull(sessionData.displayName)
        assertEquals(0L, sessionData.tokenExpiry)
        assertTrue(sessionData.lastRefresh > 0)
        assertEquals("", sessionData.authProvider)
    }

    @Test
    fun `SessionData should be created with provided values`() {
        // Given
        val userId = "test_user_123"
        val email = "test@example.com"
        val displayName = "Test User"
        val tokenExpiry = System.currentTimeMillis() + 3600000 // 1 hora en el futuro
        val lastRefresh = System.currentTimeMillis()
        val authProvider = "google.com"
        
        // When
        val sessionData = SessionData(
            userId = userId,
            email = email,
            displayName = displayName,
            tokenExpiry = tokenExpiry,
            lastRefresh = lastRefresh,
            authProvider = authProvider
        )
        
        // Then
        assertEquals(userId, sessionData.userId)
        assertEquals(email, sessionData.email)
        assertEquals(displayName, sessionData.displayName)
        assertEquals(tokenExpiry, sessionData.tokenExpiry)
        assertEquals(lastRefresh, sessionData.lastRefresh)
        assertEquals(authProvider, sessionData.authProvider)
    }

    @Test
    fun `toJson should serialize SessionData correctly`() {
        // Given
        val sessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            displayName = "Test User",
            tokenExpiry = 1234567890L,
            lastRefresh = 1234567800L,
            authProvider = "firebase"
        )
        
        // When
        val json = sessionData.toJson()
        
        // Then
        assertNotNull(json)
        assertTrue(json.contains("user123"))
        assertTrue(json.contains("user@test.com"))
        assertTrue(json.contains("Test User"))
        assertTrue(json.contains("1234567890"))
        assertTrue(json.contains("firebase"))
    }

    @Test
    fun `fromJson should deserialize valid JSON correctly`() {
        // Given
        val json = """
            {
                "userId": "user123",
                "email": "user@test.com",
                "displayName": "Test User",
                "tokenExpiry": 1234567890,
                "lastRefresh": 1234567800,
                "authProvider": "firebase"
            }
        """.trimIndent()
        
        // When
        val sessionData = SessionData.fromJson(json)
        
        // Then
        assertNotNull(sessionData)
        assertEquals("user123", sessionData.userId)
        assertEquals("user@test.com", sessionData.email)
        assertEquals("Test User", sessionData.displayName)
        assertEquals(1234567890L, sessionData.tokenExpiry)
        assertEquals(1234567800L, sessionData.lastRefresh)
        assertEquals("firebase", sessionData.authProvider)
    }

    @Test
    fun `fromJson should return null for invalid JSON`() {
        // Given
        val invalidJson = "{ invalid json }"
        
        // When
        val sessionData = SessionData.fromJson(invalidJson)
        
        // Then
        assertNull(sessionData)
    }

    @Test
    fun `fromJson should return null for empty string`() {
        // Given
        val emptyJson = ""
        
        // When
        val sessionData = SessionData.fromJson(emptyJson)
        
        // Then
        assertNull(sessionData)
    }

    @Test
    fun `isValid should return true for valid SessionData`() {
        // Given
        val validSessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            displayName = "Test User",
            tokenExpiry = System.currentTimeMillis() + 3600000, // 1 hora en el futuro
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val isValid = validSessionData.isValid()
        
        // Then
        assertTrue(isValid)
    }

    @Test
    fun `isValid should return false for empty userId`() {
        // Given
        val invalidSessionData = SessionData(
            userId = "",
            email = "user@test.com",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val isValid = invalidSessionData.isValid()
        
        // Then
        assertFalse(isValid)
    }

    @Test
    fun `isValid should return false for empty authProvider`() {
        // Given
        val invalidSessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = ""
        )
        
        // When
        val isValid = invalidSessionData.isValid()
        
        // Then
        assertFalse(isValid)
    }

    @Test
    fun `isValid should return false for invalid tokenExpiry`() {
        // Given
        val invalidSessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            tokenExpiry = 0L,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val isValid = invalidSessionData.isValid()
        
        // Then
        assertFalse(isValid)
    }

    @Test
    fun `isValid should return false when lastRefresh is after tokenExpiry`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val invalidSessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            tokenExpiry = currentTime,
            lastRefresh = currentTime + 1000, // 1 segundo después
            authProvider = "firebase"
        )
        
        // When
        val isValid = invalidSessionData.isValid()
        
        // Then
        assertFalse(isValid)
    }

    @Test
    fun `isExpired should return true for expired token`() {
        // Given
        val expiredSessionData = SessionData(
            userId = "user123",
            tokenExpiry = System.currentTimeMillis() - 1000, // 1 segundo en el pasado
            lastRefresh = System.currentTimeMillis() - 2000,
            authProvider = "firebase"
        )
        
        // When
        val isExpired = expiredSessionData.isExpired()
        
        // Then
        assertTrue(isExpired)
    }

    @Test
    fun `isExpired should return false for valid token`() {
        // Given
        val validSessionData = SessionData(
            userId = "user123",
            tokenExpiry = System.currentTimeMillis() + 3600000, // 1 hora en el futuro
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val isExpired = validSessionData.isExpired()
        
        // Then
        assertFalse(isExpired)
    }

    @Test
    fun `willExpireSoon should return true for token expiring within 5 minutes`() {
        // Given
        val soonToExpireSessionData = SessionData(
            userId = "user123",
            tokenExpiry = System.currentTimeMillis() + 240000, // 4 minutos en el futuro
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val willExpireSoon = soonToExpireSessionData.willExpireSoon()
        
        // Then
        assertTrue(willExpireSoon)
    }

    @Test
    fun `willExpireSoon should return false for token with more than 5 minutes left`() {
        // Given
        val validSessionData = SessionData(
            userId = "user123",
            tokenExpiry = System.currentTimeMillis() + 600000, // 10 minutos en el futuro
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val willExpireSoon = validSessionData.willExpireSoon()
        
        // Then
        assertFalse(willExpireSoon)
    }

    @Test
    fun `withRefreshedToken should create new instance with updated expiry and refresh time`() {
        // Given
        val originalSessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            tokenExpiry = System.currentTimeMillis() + 1000,
            lastRefresh = System.currentTimeMillis() - 1000,
            authProvider = "firebase"
        )
        val newTokenExpiry = System.currentTimeMillis() + 3600000
        
        // When
        val refreshedSessionData = originalSessionData.withRefreshedToken(newTokenExpiry)
        
        // Then
        assertEquals(originalSessionData.userId, refreshedSessionData.userId)
        assertEquals(originalSessionData.email, refreshedSessionData.email)
        assertEquals(originalSessionData.authProvider, refreshedSessionData.authProvider)
        assertEquals(newTokenExpiry, refreshedSessionData.tokenExpiry)
        assertTrue(refreshedSessionData.lastRefresh > originalSessionData.lastRefresh)
    }

    @Test
    fun `getSummary should return safe summary without sensitive data`() {
        // Given
        val sessionData = SessionData(
            userId = "user123456789",
            email = "sensitive@email.com",
            displayName = "Test User",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        
        // When
        val summary = sessionData.getSummary()
        
        // Then
        assertTrue(summary.contains("user1234"))
        assertTrue(summary.contains("sen***"))
        assertTrue(summary.contains("firebase"))
        assertFalse(summary.contains("user123456789"))
        assertFalse(summary.contains("sensitive@email.com"))
    }

    @Test
    fun `JSON serialization and deserialization should be symmetric`() {
        // Given
        val originalSessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            displayName = "Test User",
            tokenExpiry = 1234567890L,
            lastRefresh = 1234567800L,
            authProvider = "firebase"
        )
        
        // When
        val json = originalSessionData.toJson()
        val deserializedSessionData = SessionData.fromJson(json)
        
        // Then
        assertNotNull(deserializedSessionData)
        assertEquals(originalSessionData, deserializedSessionData)
    }
}