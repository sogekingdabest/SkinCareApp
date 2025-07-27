package es.monsteraltech.skincare_tfm.account

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class PasswordChangeManagerTest {

    private lateinit var passwordChangeManager: PasswordChangeManager

    @Before
    fun setUp() {
        passwordChangeManager = PasswordChangeManager()
    }

    @Test
    fun `validateNewPassword should return valid for strong password`() {
        // Given
        val strongPassword = "StrongPass123"
        
        // When
        val result = passwordChangeManager.validateNewPassword(strongPassword)
        
        // Then
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `validateNewPassword should return invalid for empty password`() {
        // Given
        val emptyPassword = ""
        
        // When
        val result = passwordChangeManager.validateNewPassword(emptyPassword)
        
        // Then
        assertFalse(result.isValid)
        assertEquals("La nueva contraseña no puede estar vacía", result.errorMessage)
    }

    @Test
    fun `validateNewPassword should return invalid for short password`() {
        // Given
        val shortPassword = "Short1"
        
        // When
        val result = passwordChangeManager.validateNewPassword(shortPassword)
        
        // Then
        assertFalse(result.isValid)
        assertEquals("La contraseña debe tener al menos 8 caracteres", result.errorMessage)
    }

    @Test
    fun `validateNewPassword should return invalid for password without uppercase`() {
        // Given
        val noUppercasePassword = "lowercase123"
        
        // When
        val result = passwordChangeManager.validateNewPassword(noUppercasePassword)
        
        // Then
        assertFalse(result.isValid)
        assertEquals("La contraseña debe tener al menos una mayúscula", result.errorMessage)
    }

    @Test
    fun `validateNewPassword should return invalid for password without lowercase`() {
        // Given
        val noLowercasePassword = "UPPERCASE123"
        
        // When
        val result = passwordChangeManager.validateNewPassword(noLowercasePassword)
        
        // Then
        assertFalse(result.isValid)
        assertEquals("La contraseña debe tener al menos una minúscula", result.errorMessage)
    }

    @Test
    fun `validateNewPassword should return invalid for password without number`() {
        // Given
        val noNumberPassword = "NoNumberPass"
        
        // When
        val result = passwordChangeManager.validateNewPassword(noNumberPassword)
        
        // Then
        assertFalse(result.isValid)
        assertEquals("La contraseña debe tener al menos un número", result.errorMessage)
    }

    @Test
    fun `validateNewPassword should return invalid for same as current password`() {
        // Given
        val currentPassword = "CurrentPass123"
        val samePassword = "CurrentPass123"
        
        // When
        val result = passwordChangeManager.validateNewPassword(samePassword, currentPassword)
        
        // Then
        assertFalse(result.isValid)
        assertEquals("La nueva contraseña debe ser diferente a la actual", result.errorMessage)
    }

    @Test
    fun `validateNewPassword should return invalid for common weak password`() {
        // Given
        val commonPassword = "password123"
        
        // When
        val result = passwordChangeManager.validateNewPassword(commonPassword)
        
        // Then
        assertFalse(result.isValid)
        assertEquals("Esta contraseña es muy común. Elige una más segura", result.errorMessage)
    }

    @Test
    fun `validatePasswordConfirmation should return valid for matching passwords`() {
        // Given
        val password = "StrongPass123"
        val confirmPassword = "StrongPass123"
        
        // When
        val result = passwordChangeManager.validatePasswordConfirmation(password, confirmPassword)
        
        // Then
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `validatePasswordConfirmation should return invalid for empty confirmation`() {
        // Given
        val password = "StrongPass123"
        val confirmPassword = ""
        
        // When
        val result = passwordChangeManager.validatePasswordConfirmation(password, confirmPassword)
        
        // Then
        assertFalse(result.isValid)
        assertEquals("Confirma la nueva contraseña", result.errorMessage)
    }

    @Test
    fun `validatePasswordConfirmation should return invalid for mismatched passwords`() {
        // Given
        val password = "StrongPass123"
        val confirmPassword = "DifferentPass123"
        
        // When
        val result = passwordChangeManager.validatePasswordConfirmation(password, confirmPassword)
        
        // Then
        assertFalse(result.isValid)
        assertEquals("Las contraseñas no coinciden", result.errorMessage)
    }

    @Test
    fun `validatePasswordChangeForm should return valid for complete valid form`() {
        // Given
        val currentPassword = "CurrentPass123"
        val newPassword = "NewStrongPass456"
        val confirmPassword = "NewStrongPass456"
        
        // When
        val result = passwordChangeManager.validatePasswordChangeForm(currentPassword, newPassword, confirmPassword)
        
        // Then
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `validatePasswordChangeForm should return invalid for empty current password`() {
        // Given
        val currentPassword = ""
        val newPassword = "NewStrongPass456"
        val confirmPassword = "NewStrongPass456"
        
        // When
        val result = passwordChangeManager.validatePasswordChangeForm(currentPassword, newPassword, confirmPassword)
        
        // Then
        assertFalse(result.isValid)
        assertEquals("Ingresa tu contraseña actual", result.errorMessage)
    }

    @Test
    fun `validatePasswordChangeForm should return invalid for weak new password`() {
        // Given
        val currentPassword = "CurrentPass123"
        val newPassword = "weak" // Too short
        val confirmPassword = "weak"
        
        // When
        val result = passwordChangeManager.validatePasswordChangeForm(currentPassword, newPassword, confirmPassword)
        
        // Then
        assertFalse(result.isValid)
        assertEquals("La contraseña debe tener al menos 8 caracteres", result.errorMessage)
    }

    @Test
    fun `validatePasswordChangeForm should return invalid for mismatched confirmation`() {
        // Given
        val currentPassword = "CurrentPass123"
        val newPassword = "NewStrongPass456"
        val confirmPassword = "DifferentPass456"
        
        // When
        val result = passwordChangeManager.validatePasswordChangeForm(currentPassword, newPassword, confirmPassword)
        
        // Then
        assertFalse(result.isValid)
        assertEquals("Las contraseñas no coinciden", result.errorMessage)
    }
}