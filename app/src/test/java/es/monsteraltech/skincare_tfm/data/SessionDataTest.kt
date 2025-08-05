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
        val sessionData = SessionData()
        assertEquals("", sessionData.userId)
        assertNull(sessionData.email)
        assertNull(sessionData.displayName)
        assertEquals(0L, sessionData.tokenExpiry)
        assertTrue(sessionData.lastRefresh > 0)
        assertEquals("", sessionData.authProvider)
    }
    @Test
    fun `SessionData should be created with provided values`() {
        val userId = "test_user_123"
        val email = "test@example.com"
        val displayName = "Test User"
        val tokenExpiry = System.currentTimeMillis() + 3600000
        val lastRefresh = System.currentTimeMillis()
        val authProvider = "google.com"
        val sessionData = SessionData(
            userId = userId,
            email = email,
            displayName = displayName,
            tokenExpiry = tokenExpiry,
            lastRefresh = lastRefresh,
            authProvider = authProvider
        )
        assertEquals(userId, sessionData.userId)
        assertEquals(email, sessionData.email)
        assertEquals(displayName, sessionData.displayName)
        assertEquals(tokenExpiry, sessionData.tokenExpiry)
        assertEquals(lastRefresh, sessionData.lastRefresh)
        assertEquals(authProvider, sessionData.authProvider)
    }
    @Test
    fun `toJson should serialize SessionData correctly`() {
        val sessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            displayName = "Test User",
            tokenExpiry = 1234567890L,
            lastRefresh = 1234567800L,
            authProvider = "firebase"
        )
        val json = sessionData.toJson()
        assertNotNull(json)
        assertTrue(json.contains("user123"))
        assertTrue(json.contains("user@test.com"))
        assertTrue(json.contains("Test User"))
        assertTrue(json.contains("1234567890"))
        assertTrue(json.contains("firebase"))
    }
    @Test
    fun `fromJson should deserialize valid JSON correctly`() {
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
        val sessionData = SessionData.fromJson(json)
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
        val invalidJson = "{ invalid json }"
        val sessionData = SessionData.fromJson(invalidJson)
        assertNull(sessionData)
    }
    @Test
    fun `fromJson should return null for empty string`() {
        val emptyJson = ""
        val sessionData = SessionData.fromJson(emptyJson)
        assertNull(sessionData)
    }
    @Test
    fun `isValid should return true for valid SessionData`() {
        val validSessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            displayName = "Test User",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        val isValid = validSessionData.isValid()
        assertTrue(isValid)
    }
    @Test
    fun `isValid should return false for empty userId`() {
        val invalidSessionData = SessionData(
            userId = "",
            email = "user@test.com",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        val isValid = invalidSessionData.isValid()
        assertFalse(isValid)
    }
    @Test
    fun `isValid should return false for empty authProvider`() {
        val invalidSessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = ""
        )
        val isValid = invalidSessionData.isValid()
        assertFalse(isValid)
    }
    @Test
    fun `isValid should return false for invalid tokenExpiry`() {
        val invalidSessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            tokenExpiry = 0L,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        val isValid = invalidSessionData.isValid()
        assertFalse(isValid)
    }
    @Test
    fun `isValid should return false when lastRefresh is after tokenExpiry`() {
        val currentTime = System.currentTimeMillis()
        val invalidSessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            tokenExpiry = currentTime,
            lastRefresh = currentTime + 1000,
            authProvider = "firebase"
        )
        val isValid = invalidSessionData.isValid()
        assertFalse(isValid)
    }
    @Test
    fun `isExpired should return true for expired token`() {
        val expiredSessionData = SessionData(
            userId = "user123",
            tokenExpiry = System.currentTimeMillis() - 1000,
            lastRefresh = System.currentTimeMillis() - 2000,
            authProvider = "firebase"
        )
        val isExpired = expiredSessionData.isExpired()
        assertTrue(isExpired)
    }
    @Test
    fun `isExpired should return false for valid token`() {
        val validSessionData = SessionData(
            userId = "user123",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        val isExpired = validSessionData.isExpired()
        assertFalse(isExpired)
    }
    @Test
    fun `willExpireSoon should return true for token expiring within 5 minutes`() {
        val soonToExpireSessionData = SessionData(
            userId = "user123",
            tokenExpiry = System.currentTimeMillis() + 240000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        val willExpireSoon = soonToExpireSessionData.willExpireSoon()
        assertTrue(willExpireSoon)
    }
    @Test
    fun `willExpireSoon should return false for token with more than 5 minutes left`() {
        val validSessionData = SessionData(
            userId = "user123",
            tokenExpiry = System.currentTimeMillis() + 600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        val willExpireSoon = validSessionData.willExpireSoon()
        assertFalse(willExpireSoon)
    }
    @Test
    fun `withRefreshedToken should create new instance with updated expiry and refresh time`() {
        val originalSessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            tokenExpiry = System.currentTimeMillis() + 1000,
            lastRefresh = System.currentTimeMillis() - 1000,
            authProvider = "firebase"
        )
        val newTokenExpiry = System.currentTimeMillis() + 3600000
        val refreshedSessionData = originalSessionData.withRefreshedToken(newTokenExpiry)
        assertEquals(originalSessionData.userId, refreshedSessionData.userId)
        assertEquals(originalSessionData.email, refreshedSessionData.email)
        assertEquals(originalSessionData.authProvider, refreshedSessionData.authProvider)
        assertEquals(newTokenExpiry, refreshedSessionData.tokenExpiry)
        assertTrue(refreshedSessionData.lastRefresh > originalSessionData.lastRefresh)
    }
    @Test
    fun `getSummary should return safe summary without sensitive data`() {
        val sessionData = SessionData(
            userId = "user123456789",
            email = "sensitive@email.com",
            displayName = "Test User",
            tokenExpiry = System.currentTimeMillis() + 3600000,
            lastRefresh = System.currentTimeMillis(),
            authProvider = "firebase"
        )
        val summary = sessionData.getSummary()
        assertTrue(summary.contains("user1234"))
        assertTrue(summary.contains("sen***"))
        assertTrue(summary.contains("firebase"))
        assertFalse(summary.contains("user123456789"))
        assertFalse(summary.contains("sensitive@email.com"))
    }
    @Test
    fun `JSON serialization and deserialization should be symmetric`() {
        val originalSessionData = SessionData(
            userId = "user123",
            email = "user@test.com",
            displayName = "Test User",
            tokenExpiry = 1234567890L,
            lastRefresh = 1234567800L,
            authProvider = "firebase"
        )
        val json = originalSessionData.toJson()
        val deserializedSessionData = SessionData.fromJson(json)
        assertNotNull(deserializedSessionData)
        assertEquals(originalSessionData, deserializedSessionData)
    }
}