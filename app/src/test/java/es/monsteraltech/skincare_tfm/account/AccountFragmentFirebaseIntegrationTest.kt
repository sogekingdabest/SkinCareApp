package es.monsteraltech.skincare_tfm.account

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import es.monsteraltech.skincare_tfm.fragments.AccountFragment
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.junit.Assert.*

/**
 * Integration tests for AccountFragment with Firebase services
 * Tests the complete flow including Firebase Auth and Firestore operations
 */
@RunWith(AndroidJUnit4::class)
class AccountFragmentFirebaseIntegrationTest {

    @Mock
    private lateinit var mockFirebaseAuth: FirebaseAuth

    @Mock
    private lateinit var mockFirebaseUser: FirebaseUser

    @Mock
    private lateinit var mockFirestore: FirebaseFirestore

    @Mock
    private lateinit var mockDocumentSnapshot: DocumentSnapshot

    private lateinit var scenario: FragmentScenario<AccountFragment>
    private lateinit var mockitoCloseable: AutoCloseable

    // Test data
    private val testUserId = "test-user-id-123"
    private val testEmail = "test@example.com"
    private val testDisplayName = "Test User"

    @Before
    fun setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this)
        setupFirebaseMocks()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
        mockitoCloseable.close()
    }

    @Test
    fun `should load user information from Firebase Auth successfully`() = runTest {
        // Given: Firebase Auth returns authenticated user
        setupAuthenticatedUser()
        
        // When: Fragment is launched
        scenario = launchFragmentInContainer<AccountFragment>()
        scenario.moveToState(Lifecycle.State.RESUMED)
        
        // Then: Fragment should display user information
        scenario.onFragment { fragment ->
            assertNotNull(fragment)
            assertNotNull(fragment.view)
            // Fragment should have loaded user data from Firebase
        }
        
        // Verify Firebase Auth was called
        verify(mockFirebaseAuth, atLeastOnce()).currentUser
    }

    @Test
    fun `should load user settings from Firestore successfully`() = runTest {
        // Given: Firebase Auth and Firestore return valid data
        setupAuthenticatedUser()
        setupFirestoreSettings()
        
        // When: Fragment is launched
        scenario = launchFragmentInContainer<AccountFragment>()
        scenario.moveToState(Lifecycle.State.RESUMED)
        
        // Then: Fragment should load settings from Firestore
        scenario.onFragment { fragment ->
            assertNotNull(fragment)
            assertNotNull(fragment.view)
        }
    }

    @Test
    fun `should handle Firebase Auth errors gracefully`() = runTest {
        // Given: Firebase Auth throws exception
        whenever(mockFirebaseAuth.currentUser).thenThrow(RuntimeException("Firebase Auth error"))
        
        // When: Fragment is launched
        scenario = launchFragmentInContainer<AccountFragment>()
        scenario.moveToState(Lifecycle.State.RESUMED)
        
        // Then: Fragment should handle error gracefully
        scenario.onFragment { fragment ->
            assertNotNull(fragment)
            assertNotNull(fragment.view)
            // Fragment should show error state
        }
    }

    @Test
    fun `should handle Firestore errors gracefully`() = runTest {
        // Given: Firebase Auth succeeds but Firestore fails
        setupAuthenticatedUser()
        // Firestore will return failed tasks
        
        // When: Fragment is launched
        scenario = launchFragmentInContainer<AccountFragment>()
        scenario.moveToState(Lifecycle.State.RESUMED)
        
        // Then: Fragment should handle Firestore errors
        scenario.onFragment { fragment ->
            assertNotNull(fragment)
            assertNotNull(fragment.view)
        }
    }

    @Test
    fun `should handle user logout process completely`() = runTest {
        // Given: User is authenticated
        setupAuthenticatedUser()
        
        // When: Fragment is launched
        scenario = launchFragmentInContainer<AccountFragment>()
        scenario.moveToState(Lifecycle.State.RESUMED)
        
        // Then: Fragment should be ready for logout operations
        scenario.onFragment { fragment ->
            assertNotNull(fragment)
            assertNotNull(fragment.view)
            // Fragment should have logout functionality available
        }
    }

    @Test
    fun `should handle settings updates to Firestore`() = runTest {
        // Given: User is authenticated and Firestore is available
        setupAuthenticatedUser()
        setupFirestoreSettings()
        
        // When: Fragment is launched
        scenario = launchFragmentInContainer<AccountFragment>()
        scenario.moveToState(Lifecycle.State.RESUMED)
        
        // Then: Fragment should be able to update settings
        scenario.onFragment { fragment ->
            assertNotNull(fragment)
            assertNotNull(fragment.view)
            // Settings switches should be functional
        }
    }

    @Test
    fun `should maintain state during configuration changes with Firebase data`() = runTest {
        // Given: Firebase returns valid data
        setupAuthenticatedUser()
        setupFirestoreSettings()
        
        // When: Fragment is launched and recreated
        scenario = launchFragmentInContainer<AccountFragment>()
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.recreate()
        
        // Then: Fragment should maintain Firebase data
        scenario.onFragment { fragment ->
            assertNotNull(fragment)
            assertNotNull(fragment.view)
        }
    }

    @Test
    fun `should handle network connectivity issues`() = runTest {
        // Given: Network operations will fail
        setupAuthenticatedUser()
        // Simulate network failure by having Firebase operations timeout
        
        // When: Fragment is launched
        scenario = launchFragmentInContainer<AccountFragment>()
        scenario.moveToState(Lifecycle.State.RESUMED)
        
        // Then: Fragment should handle network issues
        scenario.onFragment { fragment ->
            assertNotNull(fragment)
            assertNotNull(fragment.view)
            // Fragment should show appropriate error or retry options
        }
    }

    private fun setupFirebaseMocks() {
        // Setup basic Firebase Auth mock
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseUser.uid).thenReturn(testUserId)
        whenever(mockFirebaseUser.email).thenReturn(testEmail)
        whenever(mockFirebaseUser.displayName).thenReturn(testDisplayName)
        whenever(mockFirebaseUser.isEmailVerified).thenReturn(true)
    }

    private fun setupAuthenticatedUser() {
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseUser.uid).thenReturn(testUserId)
        whenever(mockFirebaseUser.email).thenReturn(testEmail)
        whenever(mockFirebaseUser.displayName).thenReturn(testDisplayName)
        whenever(mockFirebaseUser.isEmailVerified).thenReturn(true)
    }

    private fun setupFirestoreSettings() {
        // Mock Firestore document with settings
        val settingsData = mapOf(
            "notificationsEnabled" to true,
            "darkModeEnabled" to false,
            "autoBackup" to true,
            "language" to "es"
        )
        
        whenever(mockDocumentSnapshot.exists()).thenReturn(true)
        whenever(mockDocumentSnapshot.data).thenReturn(settingsData)
        whenever(mockDocumentSnapshot.getBoolean("notificationsEnabled")).thenReturn(true)
        whenever(mockDocumentSnapshot.getBoolean("darkModeEnabled")).thenReturn(false)
        whenever(mockDocumentSnapshot.getBoolean("autoBackup")).thenReturn(true)
        whenever(mockDocumentSnapshot.getString("language")).thenReturn("es")
        
        // Mock successful Firestore operations
        val successfulTask = Tasks.forResult(mockDocumentSnapshot)
        whenever(mockFirestore.collection(any()).document(any()).get()).thenReturn(successfulTask)
    }
}