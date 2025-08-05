package es.monsteraltech.skincare_tfm.account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
class PasswordChangeManagerTest {
    private lateinit var passwordChangeManager: PasswordChangeManager
    @Before
    fun setUp() {
        passwordChangeManager = PasswordChangeManager()
    }
    @Test
    fun `validateNewPassword should return valid for strong password`() {
        val strongPassword = "StrongPass123"
        val result = passwordChangeManager.validateNewPassword(strongPassword)
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }
    @Test
    fun `validateNewPassword should return invalid for empty password`() {
        val emptyPassword = ""
        val result = passwordChangeManager.validateNewPassword(emptyPassword)
        assertFalse(result.isValid)
        assertEquals("La nueva contraseña no puede estar vacía", result.errorMessage)
    }
    @Test
    fun `validateNewPassword should return invalid for short password`() {
        val shortPassword = "Short1"
        val result = passwordChangeManager.validateNewPassword(shortPassword)
        assertFalse(result.isValid)
        assertEquals("La contraseña debe tener al menos 8 caracteres", result.errorMessage)
    }
    @Test
    fun `validateNewPassword should return invalid for password without uppercase`() {
        val noUppercasePassword = "lowercase123"
        val result = passwordChangeManager.validateNewPassword(noUppercasePassword)
        assertFalse(result.isValid)
        assertEquals("La contraseña debe tener al menos una mayúscula", result.errorMessage)
    }
    @Test
    fun `validateNewPassword should return invalid for password without lowercase`() {
        val noLowercasePassword = "UPPERCASE123"
        val result = passwordChangeManager.validateNewPassword(noLowercasePassword)
        assertFalse(result.isValid)
        assertEquals("La contraseña debe tener al menos una minúscula", result.errorMessage)
    }
    @Test
    fun `validateNewPassword should return invalid for password without number`() {
        val noNumberPassword = "NoNumberPass"
        val result = passwordChangeManager.validateNewPassword(noNumberPassword)
        assertFalse(result.isValid)
        assertEquals("La contraseña debe tener al menos un número", result.errorMessage)
    }
    @Test
    fun `validateNewPassword should return invalid for same as current password`() {
        val currentPassword = "CurrentPass123"
        val samePassword = "CurrentPass123"
        val result = passwordChangeManager.validateNewPassword(samePassword, currentPassword)
        assertFalse(result.isValid)
        assertEquals("La nueva contraseña debe ser diferente a la actual", result.errorMessage)
    }
    @Test
    fun `validateNewPassword should return invalid for common weak password`() {
        val commonPassword = "password123"
        val result = passwordChangeManager.validateNewPassword(commonPassword)
        assertFalse(result.isValid)
        assertEquals("Esta contraseña es muy común. Elige una más segura", result.errorMessage)
    }
    @Test
    fun `validatePasswordConfirmation should return valid for matching passwords`() {
        val password = "StrongPass123"
        val confirmPassword = "StrongPass123"
        val result = passwordChangeManager.validatePasswordConfirmation(password, confirmPassword)
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }
    @Test
    fun `validatePasswordConfirmation should return invalid for empty confirmation`() {
        val password = "StrongPass123"
        val confirmPassword = ""
        val result = passwordChangeManager.validatePasswordConfirmation(password, confirmPassword)
        assertFalse(result.isValid)
        assertEquals("Confirma la nueva contraseña", result.errorMessage)
    }
    @Test
    fun `validatePasswordConfirmation should return invalid for mismatched passwords`() {
        val password = "StrongPass123"
        val confirmPassword = "DifferentPass123"
        val result = passwordChangeManager.validatePasswordConfirmation(password, confirmPassword)
        assertFalse(result.isValid)
        assertEquals("Las contraseñas no coinciden", result.errorMessage)
    }
    @Test
    fun `validatePasswordChangeForm should return valid for complete valid form`() {
        val currentPassword = "CurrentPass123"
        val newPassword = "NewStrongPass456"
        val confirmPassword = "NewStrongPass456"
        val result = passwordChangeManager.validatePasswordChangeForm(currentPassword, newPassword, confirmPassword)
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }
    @Test
    fun `validatePasswordChangeForm should return invalid for empty current password`() {
        val currentPassword = ""
        val newPassword = "NewStrongPass456"
        val confirmPassword = "NewStrongPass456"
        val result = passwordChangeManager.validatePasswordChangeForm(currentPassword, newPassword, confirmPassword)
        assertFalse(result.isValid)
        assertEquals("Ingresa tu contraseña actual", result.errorMessage)
    }
    @Test
    fun `validatePasswordChangeForm should return invalid for weak new password`() {
        val currentPassword = "CurrentPass123"
        val newPassword = "weak"
        val confirmPassword = "weak"
        val result = passwordChangeManager.validatePasswordChangeForm(currentPassword, newPassword, confirmPassword)
        assertFalse(result.isValid)
        assertEquals("La contraseña debe tener al menos 8 caracteres", result.errorMessage)
    }
    @Test
    fun `validatePasswordChangeForm should return invalid for mismatched confirmation`() {
        val currentPassword = "CurrentPass123"
        val newPassword = "NewStrongPass456"
        val confirmPassword = "DifferentPass456"
        val result = passwordChangeManager.validatePasswordChangeForm(currentPassword, newPassword, confirmPassword)
        assertFalse(result.isValid)
        assertEquals("Las contraseñas no coinciden", result.errorMessage)
    }
}