package es.monsteraltech.skincare_tfm.analysis

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests unitarios para la clase AnalysisError y sus tipos específicos
 */
class AnalysisErrorTest {

    @Test
    fun `timeout error should have correct properties`() {
        val error = AnalysisError.Timeout
        
        assertEquals("El análisis está tomando más tiempo del esperado", error.message)
        assertTrue(error.isRecoverable())
        assertEquals(RecoveryStrategy.EXTEND_TIMEOUT, error.getRecoveryStrategy())
        assertTrue(error.getUserFriendlyMessage().contains("tardando"))
    }

    @Test
    fun `out of memory error should have correct properties`() {
        val error = AnalysisError.OutOfMemory
        
        assertEquals("Memoria insuficiente para procesar la imagen", error.message)
        assertTrue(error.isRecoverable())
        assertEquals(RecoveryStrategy.REDUCE_IMAGE_RESOLUTION, error.getRecoveryStrategy())
        assertTrue(error.getUserFriendlyMessage().contains("grande"))
    }

    @Test
    fun `ai model error should have correct properties`() {
        val details = "Model loading failed"
        val error = AnalysisError.AIModelError(details)
        
        assertEquals("Error en el modelo de IA: $details", error.message)
        assertTrue(error.isRecoverable())
        assertEquals(RecoveryStrategy.FALLBACK_TO_ABCDE, error.getRecoveryStrategy())
        assertTrue(error.getUserFriendlyMessage().contains("IA no está disponible"))
    }

    @Test
    fun `image processing error should not be recoverable`() {
        val details = "Invalid image format"
        val error = AnalysisError.ImageProcessingError(details)
        
        assertEquals("Error al procesar la imagen: $details", error.message)
        assertFalse(error.isRecoverable())
        assertEquals(RecoveryStrategy.NONE, error.getRecoveryStrategy())
    }

    @Test
    fun `network error should be recoverable`() {
        val details = "Connection timeout"
        val error = AnalysisError.NetworkError(details)
        
        assertEquals("Error de conectividad: $details", error.message)
        assertTrue(error.isRecoverable())
        assertEquals(RecoveryStrategy.RETRY_WITH_DELAY, error.getRecoveryStrategy())
        assertTrue(error.getUserFriendlyMessage().contains("conectividad"))
    }

    @Test
    fun `abcde analysis error should be recoverable`() {
        val details = "OpenCV processing failed"
        val error = AnalysisError.ABCDEAnalysisError(details)
        
        assertEquals("Error en análisis ABCDE: $details", error.message)
        assertTrue(error.isRecoverable())
        assertEquals(RecoveryStrategy.USE_DEFAULT_VALUES, error.getRecoveryStrategy())
    }

    @Test
    fun `configuration error should be recoverable`() {
        val details = "Invalid timeout value"
        val error = AnalysisError.ConfigurationError(details)
        
        assertEquals("Configuración inválida: $details", error.message)
        assertTrue(error.isRecoverable())
        assertEquals(RecoveryStrategy.USE_DEFAULT_CONFIG, error.getRecoveryStrategy())
    }

    @Test
    fun `invalid image error should not be recoverable`() {
        val details = "Image is corrupted"
        val error = AnalysisError.InvalidImageError(details)
        
        assertEquals("Imagen inválida: $details", error.message)
        assertFalse(error.isRecoverable())
        assertEquals(RecoveryStrategy.NONE, error.getRecoveryStrategy())
    }

    @Test
    fun `user cancellation should not be recoverable`() {
        val error = AnalysisError.UserCancellation
        
        assertEquals("Procesamiento cancelado por el usuario", error.message)
        assertFalse(error.isRecoverable())
        assertEquals(RecoveryStrategy.NONE, error.getRecoveryStrategy())
        assertEquals("Análisis cancelado.", error.getUserFriendlyMessage())
    }

    @Test
    fun `unknown error should not be recoverable`() {
        val details = "Unexpected exception"
        val cause = RuntimeException("Original cause")
        val error = AnalysisError.UnknownError(details, cause)
        
        assertEquals("Error desconocido: $details", error.message)
        assertEquals(cause, error.cause)
        assertFalse(error.isRecoverable())
        assertEquals(RecoveryStrategy.NONE, error.getRecoveryStrategy())
    }

    @Test
    fun `all recoverable errors should have valid strategies`() {
        val recoverableErrors = listOf(
            AnalysisError.Timeout,
            AnalysisError.OutOfMemory,
            AnalysisError.AIModelError("test"),
            AnalysisError.NetworkError("test"),
            AnalysisError.ABCDEAnalysisError("test"),
            AnalysisError.ConfigurationError("test")
        )
        
        recoverableErrors.forEach { error ->
            assertTrue("${error.javaClass.simpleName} should be recoverable", error.isRecoverable())
            assertNotEquals("${error.javaClass.simpleName} should have a recovery strategy", 
                RecoveryStrategy.NONE, error.getRecoveryStrategy())
        }
    }

    @Test
    fun `all non-recoverable errors should have no strategy`() {
        val nonRecoverableErrors = listOf(
            AnalysisError.ImageProcessingError("test"),
            AnalysisError.InvalidImageError("test"),
            AnalysisError.UserCancellation,
            AnalysisError.UnknownError("test")
        )
        
        nonRecoverableErrors.forEach { error ->
            assertFalse("${error.javaClass.simpleName} should not be recoverable", error.isRecoverable())
            assertEquals("${error.javaClass.simpleName} should have no recovery strategy", 
                RecoveryStrategy.NONE, error.getRecoveryStrategy())
        }
    }

    @Test
    fun `user friendly messages should be appropriate`() {
        val errors = mapOf(
            AnalysisError.Timeout to "tardando",
            AnalysisError.OutOfMemory to "grande",
            AnalysisError.AIModelError("test") to "IA no está disponible",
            AnalysisError.ImageProcessingError("test") to "problema al procesar",
            AnalysisError.NetworkError("test") to "conectividad",
            AnalysisError.ABCDEAnalysisError("test") to "ABCDE",
            AnalysisError.ConfigurationError("test") to "configuración",
            AnalysisError.InvalidImageError("test") to "no es válida",
            AnalysisError.UserCancellation to "cancelado",
            AnalysisError.UnknownError("test") to "inesperado"
        )
        
        errors.forEach { (error, expectedKeyword) ->
            val message = error.getUserFriendlyMessage().lowercase()
            assertTrue("Message for ${error.javaClass.simpleName} should contain '$expectedKeyword'", 
                message.contains(expectedKeyword.lowercase()))
        }
    }
}