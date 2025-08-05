package es.monsteraltech.skincare_tfm.account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
class PasswordChangeManagerSimpleTest {
    private lateinit var passwordChangeManager: PasswordChangeManager
    @Before
    fun setUp() {
        passwordChangeManager = PasswordChangeManager()
    }
    @Test
    fun validateNewPassword_validStrongPassword_returnsValid() {
        val result = passwordChangeManager.validateNewPassword("StrongPass123")
        assertTrue("Strong password should be valid", result.isValid)
        assertNull("No error message should be present", result.errorMessage)
    }
    @Test
    fun validateNewPassword_emptyPassword_returnsInvalid() {
        val result = passwordChangeManager.validateNewPassword("")
        assertFalse("Empty password should be invalid", result.isValid)
        assertEquals("La nueva contraseña no puede estar vacía", result.errorMessage)
    }
    @Test
    fun validateNewPassword_shortPassword_returnsInvalid() {
        val result = passwordChangeManager.validateNewPassword("Short1")
        assertFalse("Short password should be invalid", result.isValid)
        assertEquals("La contraseña debe tener al menos 8 caracteres", result.errorMessage)
    }
    @Test
    fun validateNewPassword_noUppercase_returnsInvalid() {
        val result = passwordChangeManager.validateNewPassword("lowercase123")
        assertFalse("Password without uppercase should be invalid", result.isValid)
        assertEquals("La contraseña debe tener al menos una mayúscula", result.errorMessage)
    }
    @Test
    fun validateNewPassword_noLowercase_returnsInvalid() {
        val result = passwordChangeManager.validateNewPassword("UPPERCASE123")
        assertFalse("Password without lowercase should be invalid", result.isValid)
        assertEquals("La contraseña debe tener al menos una minúscula", result.errorMessage)
    }
    @Test
    fun validateNewPassword_noNumber_returnsInvalid() {
        val result = passwordChangeManager.validateNewPassword("NoNumberPass")
        assertFalse("Password without number should be invalid", result.isValid)
        assertEquals("La contraseña debe tener al menos un número", result.errorMessage)
    }
    @Test
    fun validateNewPassword_sameAsCurrent_returnsInvalid() {
        val currentPassword = "CurrentPass123"
        val result = passwordChangeManager.validateNewPassword(currentPassword, currentPassword)
        assertFalse("Password same as current should be invalid", result.isValid)
        assertEquals("La nueva contraseña debe ser diferente a la actual", result.errorMessage)
    }
    @Test
    fun validateNewPassword_commonWeakPassword_returnsInvalid() {
        val result = passwordChangeManager.validateNewPassword("password123")
        assertFalse("Common weak password should be invalid", result.isValid)
        assertEquals("Esta contraseña es muy común. Elige una más segura", result.errorMessage)
    }
    @Test
    fun validatePasswordConfirmation_matchingPasswords_returnsValid() {
        val password = "StrongPass123"
        val result = passwordChangeManager.validatePasswordConfirmation(password, password)
        assertTrue("Matching passwords should be valid", result.isValid)
        assertNull("No error message should be present", result.errorMessage)
    }
    @Test
    fun validatePasswordConfirmation_emptyConfirmation_returnsInvalid() {
        val result = passwordChangeManager.validatePasswordConfirmation("StrongPass123", "")
        assertFalse("Empty confirmation should be invalid", result.isValid)
        assertEquals("Confirma la nueva contraseña", result.errorMessage)
    }
    @Test
    fun validatePasswordConfirmation_mismatchedPasswords_returnsInvalid() {
        val result = passwordChangeManager.validatePasswordConfirmation("StrongPass123", "DifferentPass123")
        assertFalse("Mismatched passwords should be invalid", result.isValid)
        assertEquals("Las contraseñas no coinciden", result.errorMessage)
    }
    @Test
    fun validatePasswordChangeForm_validCompleteForm_returnsValid() {
        val result = passwordChangeManager.validatePasswordChangeForm(
            "CurrentPass123",
            "NewStrongPass456",
            "NewStrongPass456"
        )
        assertTrue("Valid complete form should be valid", result.isValid)
        assertNull("No error message should be present", result.errorMessage)
    }
    @Test
    fun validatePasswordChangeForm_emptyCurrentPassword_returnsInvalid() {
        val result = passwordChangeManager.validatePasswordChangeForm(
            "",
            "NewStrongPass456",
            "NewStrongPass456"
        )
        assertFalse("Empty current password should be invalid", result.isValid)
        assertEquals("Ingresa tu contraseña actual", result.errorMessage)
    }
    @Test
    fun validatePasswordChangeForm_weakNewPassword_returnsInvalid() {
        val result = passwordChangeManager.validatePasswordChangeForm(
            "CurrentPass123",
            "weak",
            "weak"
        )
        assertFalse("Weak new password should be invalid", result.isValid)
        assertEquals("La contraseña debe tener al menos 8 caracteres", result.errorMessage)
    }
    @Test
    fun validatePasswordChangeForm_mismatchedConfirmation_returnsInvalid() {
        val result = passwordChangeManager.validatePasswordChangeForm(
            "CurrentPass123",
            "NewStrongPass456",
            "DifferentPass456"
        )
        assertFalse("Mismatched confirmation should be invalid", result.isValid)
        assertEquals("Las contraseñas no coinciden", result.errorMessage)
    }
    @Test
    fun validatePasswordChangeForm_newPasswordSameAsCurrent_returnsInvalid() {
        val currentPassword = "CurrentPass123"
        val result = passwordChangeManager.validatePasswordChangeForm(
            currentPassword,
            currentPassword,
            currentPassword
        )
        assertFalse("New password same as current should be invalid", result.isValid)
        assertEquals("La nueva contraseña debe ser diferente a la actual", result.errorMessage)
    }
    @Test
    fun validateNewPassword_blankPassword_returnsInvalid() {
        val result = passwordChangeManager.validateNewPassword("   ")
        assertFalse("Blank password should be invalid", result.isValid)
        assertEquals("La nueva contraseña no puede estar vacía", result.errorMessage)
    }
    @Test
    fun validateNewPassword_exactlyEightCharacters_withAllRequirements_returnsValid() {
        val result = passwordChangeManager.validateNewPassword("Pass123a")
        assertTrue("Password with exactly 8 chars and all requirements should be valid", result.isValid)
        assertNull("No error message should be present", result.errorMessage)
    }
    @Test
    fun validateNewPassword_multipleNumbers_returnsValid() {
        val result = passwordChangeManager.validateNewPassword("StrongPass12345")
        assertTrue("Password with multiple numbers should be valid", result.isValid)
        assertNull("No error message should be present", result.errorMessage)
    }
    @Test
    fun validateNewPassword_specialCharacters_returnsValid() {
        val result = passwordChangeManager.validateNewPassword("Strong@Pass123!")
        assertTrue("Password with special characters should be valid", result.isValid)
        assertNull("No error message should be present", result.errorMessage)
    }
}