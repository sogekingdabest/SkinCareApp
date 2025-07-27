package es.monsteraltech.skincare_tfm.account

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AccountResult sealed class
 * Tests state handling, error type detection, and utility functions
 */
class AccountResultTest {

    @Test
    fun `success result should be created correctly`() {
        val data = "test data"
        val result = AccountResult.success(data)
        
        assertTrue(result.isSuccess())
        assertFalse(result.isError())
        assertFalse(result.isLoading())
        assertEquals(data, result.getDataOrNull())
        assertNull(result.getErrorOrNull())
    }

    @Test
    fun `error result should be created correctly`() {
        val exception = Exception("Test error")
        val message = "Test error message"
        val result = AccountResult.error<String>(exception, message)
        
        assertFalse(result.isSuccess())
        assertTrue(result.isError())
        assertFalse(result.isLoading())
        assertNull(result.getDataOrNull())
        assertNotNull(result.getErrorOrNull())
        
        val error = result.getErrorOrNull()!!
        assertEquals(exception, error.exception)
        assertEquals(message, error.message)
    }

    @Test
    fun `loading result should be created correctly`() {
        val message = "Loading..."
        val result = AccountResult.loading<String>(message)
        
        assertFalse(result.isSuccess())
        assertFalse(result.isError())
        assertTrue(result.isLoading())
        assertNull(result.getDataOrNull())
        assertNull(result.getErrorOrNull())
    }

    @Test
    fun `error type detection should work for network errors`() {
        val networkException = Exception("Network timeout error")
        val result = AccountResult.error<String>(networkException)
        
        val error = result.getErrorOrNull()!!
        assertEquals(AccountResult.ErrorType.NETWORK_ERROR, error.errorType)
    }

    @Test
    fun `error type detection should work for authentication errors`() {
        val authException = Exception("Authentication failed")
        val result = AccountResult.error<String>(authException)
        
        val error = result.getErrorOrNull()!!
        assertEquals(AccountResult.ErrorType.AUTHENTICATION_ERROR, error.errorType)
    }

    @Test
    fun `error type detection should work for firebase errors`() {
        val firebaseException = Exception("Firebase connection error")
        val result = AccountResult.error<String>(firebaseException)
        
        val error = result.getErrorOrNull()!!
        assertEquals(AccountResult.ErrorType.FIREBASE_ERROR, error.errorType)
    }

    @Test
    fun `error type detection should work for validation errors`() {
        val validationException = IllegalArgumentException("Invalid input")
        val result = AccountResult.error<String>(validationException)
        
        val error = result.getErrorOrNull()!!
        assertEquals(AccountResult.ErrorType.VALIDATION_ERROR, error.errorType)
    }

    @Test
    fun `error type detection should default to generic error`() {
        val genericException = Exception("Some random error")
        val result = AccountResult.error<String>(genericException)
        
        val error = result.getErrorOrNull()!!
        assertEquals(AccountResult.ErrorType.GENERIC_ERROR, error.errorType)
    }

    @Test
    fun `onSuccess should execute action for success result`() {
        var actionExecuted = false
        var receivedData: String? = null
        
        val data = "test data"
        val result = AccountResult.success(data)
        
        result.onSuccess { 
            actionExecuted = true
            receivedData = it
        }
        
        assertTrue(actionExecuted)
        assertEquals(data, receivedData)
    }

    @Test
    fun `onSuccess should not execute action for error result`() {
        var actionExecuted = false
        
        val result = AccountResult.error<String>(Exception("Error"))
        
        result.onSuccess { 
            actionExecuted = true
        }
        
        assertFalse(actionExecuted)
    }

    @Test
    fun `onError should execute action for error result`() {
        var actionExecuted = false
        var receivedError: AccountResult.Error<String>? = null
        
        val exception = Exception("Test error")
        val result = AccountResult.error<String>(exception)
        
        result.onError { 
            actionExecuted = true
            receivedError = it
        }
        
        assertTrue(actionExecuted)
        assertEquals(exception, receivedError?.exception)
    }

    @Test
    fun `onError should not execute action for success result`() {
        var actionExecuted = false
        
        val result = AccountResult.success("data")
        
        result.onError { 
            actionExecuted = true
        }
        
        assertFalse(actionExecuted)
    }

    @Test
    fun `onLoading should execute action for loading result`() {
        var actionExecuted = false
        var receivedLoading: AccountResult.Loading<String>? = null
        
        val message = "Loading..."
        val result = AccountResult.loading<String>(message)
        
        result.onLoading { 
            actionExecuted = true
            receivedLoading = it
        }
        
        assertTrue(actionExecuted)
        assertEquals(message, receivedLoading?.message)
    }

    @Test
    fun `onLoading should not execute action for success result`() {
        var actionExecuted = false
        
        val result = AccountResult.success("data")
        
        result.onLoading { 
            actionExecuted = true
        }
        
        assertFalse(actionExecuted)
    }

    @Test
    fun `map should transform success result`() {
        val originalData = "123"
        val result = AccountResult.success(originalData)
        
        val mappedResult = result.map { it.toInt() }
        
        assertTrue(mappedResult.isSuccess())
        assertEquals(123, mappedResult.getDataOrNull())
    }

    @Test
    fun `map should preserve error result`() {
        val exception = Exception("Test error")
        val result = AccountResult.error<String>(exception)
        
        val mappedResult = result.map { it.toInt() }
        
        assertTrue(mappedResult.isError())
        assertEquals(exception, mappedResult.getErrorOrNull()?.exception)
    }

    @Test
    fun `map should preserve loading result`() {
        val message = "Loading..."
        val result = AccountResult.loading<String>(message)
        
        val mappedResult = result.map { it.toInt() }
        
        assertTrue(mappedResult.isLoading())
    }

    @Test
    fun `flatMap should transform success result`() {
        val originalData = "123"
        val result = AccountResult.success(originalData)
        
        val mappedResult = result.flatMap { 
            AccountResult.success(it.toInt())
        }
        
        assertTrue(mappedResult.isSuccess())
        assertEquals(123, mappedResult.getDataOrNull())
    }

    @Test
    fun `flatMap should preserve error result`() {
        val exception = Exception("Test error")
        val result = AccountResult.error<String>(exception)
        
        val mappedResult = result.flatMap { 
            AccountResult.success(it.toInt())
        }
        
        assertTrue(mappedResult.isError())
        assertEquals(exception, mappedResult.getErrorOrNull()?.exception)
    }

    @Test
    fun `flatMap should allow error transformation`() {
        val originalData = "123"
        val result = AccountResult.success(originalData)
        
        val mappedResult = result.flatMap { 
            AccountResult.error<Int>(Exception("Transformation error"))
        }
        
        assertTrue(mappedResult.isError())
        assertEquals("Transformation error", mappedResult.getErrorOrNull()?.exception?.message)
    }

    @Test
    fun `default error messages should be user friendly`() {
        val networkError = AccountResult.error<String>(Exception("network timeout"))
        val authError = AccountResult.error<String>(Exception("auth failed"))
        val firebaseError = AccountResult.error<String>(Exception("firebase error"))
        val validationError = AccountResult.error<String>(IllegalArgumentException("invalid data"))
        val genericError = AccountResult.error<String>(Exception("random error"))
        
        assertTrue(networkError.getErrorOrNull()!!.message.contains("conexión"))
        assertTrue(authError.getErrorOrNull()!!.message.contains("autenticación"))
        assertTrue(firebaseError.getErrorOrNull()!!.message.contains("servidor"))
        assertTrue(validationError.getErrorOrNull()!!.message.contains("inválidos"))
        assertTrue(genericError.getErrorOrNull()!!.message.contains("inesperado"))
    }
}