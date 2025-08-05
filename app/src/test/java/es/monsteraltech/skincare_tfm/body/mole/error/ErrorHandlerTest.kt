package es.monsteraltech.skincare_tfm.body.mole.error
import android.content.Context
import es.monsteraltech.skincare_tfm.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
class ErrorHandlerTest {
    private lateinit var mockContext: Context
    @Before
    fun setup() {
        mockContext = mockk()
        every { mockContext.getString(R.string.error_network_connection) } returns "Sin conexión a internet"
        every { mockContext.getString(R.string.error_network_timeout) } returns "Timeout de conexión"
        every { mockContext.getString(R.string.error_network_ssl) } returns "Error SSL"
        every { mockContext.getString(R.string.error_authentication) } returns "Error de autenticación"
        every { mockContext.getString(R.string.error_data_not_found) } returns "Datos no encontrados"
        every { mockContext.getString(R.string.error_validation) } returns "Error de validación"
        every { mockContext.getString(R.string.error_unknown) } returns "Error desconocido"
        every { mockContext.getString(R.string.error_action_check_connection) } returns "Verifica conexión"
        every { mockContext.getString(R.string.error_action_retry_later) } returns "Reintentar más tarde"
        every { mockContext.getString(R.string.error_action_login_again) } returns "Iniciar sesión"
        every { mockContext.getString(R.string.error_action_refresh_data) } returns "Actualizar datos"
        every { mockContext.getString(R.string.error_action_try_again) } returns "Intentar de nuevo"
    }
    @Test
    fun `handleError should return network error for ConnectException`() {
        val exception = ConnectException("Connection failed")
        val result = ErrorHandler.handleError(mockContext, exception, "test operation")
        assertEquals(ErrorHandler.ErrorType.NETWORK_ERROR, result.type)
        assertEquals("Sin conexión a internet", result.userMessage)
        assertTrue(result.isRetryable)
        assertEquals("Verifica conexión", result.suggestedAction)
    }
    @Test
    fun `handleError should return network error for SocketTimeoutException`() {
        val exception = SocketTimeoutException("Timeout")
        val result = ErrorHandler.handleError(mockContext, exception, "test operation")
        assertEquals(ErrorHandler.ErrorType.NETWORK_ERROR, result.type)
        assertEquals("Timeout de conexión", result.userMessage)
        assertTrue(result.isRetryable)
        assertEquals("Reintentar más tarde", result.suggestedAction)
    }
    @Test
    fun `handleError should return authentication error for SecurityException with auth message`() {
        val exception = SecurityException("Usuario no autenticado")
        val result = ErrorHandler.handleError(mockContext, exception, "test operation")
        assertEquals(ErrorHandler.ErrorType.AUTHENTICATION_ERROR, result.type)
        assertEquals("Error de autenticación", result.userMessage)
        assertFalse(result.isRetryable)
        assertEquals("Iniciar sesión", result.suggestedAction)
    }
    @Test
    fun `handleError should return data not found error for not found message`() {
        val exception = Exception("No se encontró el lunar")
        val result = ErrorHandler.handleError(mockContext, exception, "test operation")
        assertEquals(ErrorHandler.ErrorType.DATA_NOT_FOUND, result.type)
        assertEquals("Datos no encontrados", result.userMessage)
        assertTrue(result.isRetryable)
        assertEquals("Actualizar datos", result.suggestedAction)
    }
    @Test
    fun `handleError should return validation error for validation message`() {
        val exception = IllegalArgumentException("Datos de validación inválidos")
        val result = ErrorHandler.handleError(mockContext, exception, "test operation")
        assertEquals(ErrorHandler.ErrorType.VALIDATION_ERROR, result.type)
        assertEquals("Error de validación", result.userMessage)
        assertFalse(result.isRetryable)
    }
    @Test
    fun `isRecoverableError should return true for network errors`() {
        assertTrue(ErrorHandler.isRecoverableError(ConnectException()))
        assertTrue(ErrorHandler.isRecoverableError(UnknownHostException()))
        assertTrue(ErrorHandler.isRecoverableError(SocketTimeoutException()))
    }
    @Test
    fun `isRecoverableError should return false for validation errors`() {
        assertFalse(ErrorHandler.isRecoverableError(IllegalArgumentException("Invalid data")))
        assertFalse(ErrorHandler.isRecoverableError(SecurityException("Permission denied")))
    }
    @Test
    fun `getRetryDelay should return appropriate delays for different error types`() {
        val networkDelay = ErrorHandler.getRetryDelay(1, ErrorHandler.ErrorType.NETWORK_ERROR)
        val dataDelay = ErrorHandler.getRetryDelay(1, ErrorHandler.ErrorType.DATA_NOT_FOUND)
        assertTrue("Network delay should be at least 2000ms", networkDelay >= 2000L)
        assertTrue("Data delay should be at least 1000ms", dataDelay >= 1000L)
    }
    @Test
    fun `getRetryDelay should implement exponential backoff`() {
        val delay1 = ErrorHandler.getRetryDelay(1, ErrorHandler.ErrorType.NETWORK_ERROR)
        val delay2 = ErrorHandler.getRetryDelay(2, ErrorHandler.ErrorType.NETWORK_ERROR)
        val delay3 = ErrorHandler.getRetryDelay(3, ErrorHandler.ErrorType.NETWORK_ERROR)
        assertTrue("Delay should increase exponentially", delay2 > delay1)
        assertTrue("Delay should increase exponentially", delay3 > delay2)
    }
    @Test
    fun `getMaxRetries should return appropriate retry counts`() {
        assertEquals(3, ErrorHandler.getMaxRetries(ErrorHandler.ErrorType.NETWORK_ERROR))
        assertEquals(2, ErrorHandler.getMaxRetries(ErrorHandler.ErrorType.DATA_NOT_FOUND))
        assertEquals(1, ErrorHandler.getMaxRetries(ErrorHandler.ErrorType.PARSING_ERROR))
    }
}