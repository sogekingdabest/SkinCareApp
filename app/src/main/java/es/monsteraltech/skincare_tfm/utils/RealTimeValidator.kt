package es.monsteraltech.skincare_tfm.utils
import android.text.Editable
import android.text.TextWatcher
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
class RealTimeValidator {
    data class ValidationConfig(
        val editText: TextInputEditText,
        val layout: TextInputLayout,
        val validators: List<Validator>,
        val showErrorImmediately: Boolean = false
    )
    interface Validator {
        fun validate(text: String): ValidationResult
    }
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
    object Validators {
        fun required(errorMessage: String = "Este campo es obligatorio"): Validator {
            return object : Validator {
                override fun validate(text: String): ValidationResult {
                    return if (text.trim().isNotEmpty()) {
                        ValidationResult(true)
                    } else {
                        ValidationResult(false, errorMessage)
                    }
                }
            }
        }
        fun minLength(minLength: Int, errorMessage: String? = null): Validator {
            return object : Validator {
                override fun validate(text: String): ValidationResult {
                    return if (text.length >= minLength) {
                        ValidationResult(true)
                    } else {
                        ValidationResult(false, errorMessage ?: "Debe tener al menos $minLength caracteres")
                    }
                }
            }
        }
        fun passwordStrength(): Validator {
            return object : Validator {
                override fun validate(text: String): ValidationResult {
                    val hasMinLength = text.length >= 8
                    val hasUppercase = text.any { it.isUpperCase() }
                    val hasLowercase = text.any { it.isLowerCase() }
                    val hasNumber = text.any { it.isDigit() }
                    return when {
                        !hasMinLength -> ValidationResult(false, "Debe tener al menos 8 caracteres")
                        !hasUppercase -> ValidationResult(false, "Debe tener al menos una mayúscula")
                        !hasLowercase -> ValidationResult(false, "Debe tener al menos una minúscula")
                        !hasNumber -> ValidationResult(false, "Debe tener al menos un número")
                        else -> ValidationResult(true)
                    }
                }
            }
        }
        fun passwordMatch(otherEditText: TextInputEditText, errorMessage: String = "Las contraseñas no coinciden"): Validator {
            return object : Validator {
                override fun validate(text: String): ValidationResult {
                    val otherText = otherEditText.text?.toString() ?: ""
                    return if (text == otherText && text.isNotEmpty()) {
                        ValidationResult(true)
                    } else {
                        ValidationResult(false, errorMessage)
                    }
                }
            }
        }
        fun email(errorMessage: String = "Email inválido"): Validator {
            return object : Validator {
                override fun validate(text: String): ValidationResult {
                    val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
                    return if (text.matches(emailPattern.toRegex())) {
                        ValidationResult(true)
                    } else {
                        ValidationResult(false, errorMessage)
                    }
                }
            }
        }
        fun custom(validationFunction: (String) -> ValidationResult): Validator {
            return object : Validator {
                override fun validate(text: String): ValidationResult {
                    return validationFunction(text)
                }
            }
        }
    }
    private val validationConfigs = mutableListOf<ValidationConfig>()
    private val textWatchers = mutableMapOf<TextInputEditText, TextWatcher>()
    fun addField(config: ValidationConfig) {
        validationConfigs.add(config)
        setupRealTimeValidation(config)
    }
    fun addField(
        editText: TextInputEditText,
        layout: TextInputLayout,
        vararg validators: Validator,
        showErrorImmediately: Boolean = false
    ) {
        val config = ValidationConfig(editText, layout, validators.toList(), showErrorImmediately)
        addField(config)
    }
    private fun setupRealTimeValidation(config: ValidationConfig) {
        val textWatcher = object : TextWatcher {
            private var hasUserInteracted = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!hasUserInteracted && !config.showErrorImmediately) {
                    hasUserInteracted = true
                    return
                }
                val text = s?.toString() ?: ""
                validateField(config, text, showError = hasUserInteracted || config.showErrorImmediately)
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        config.editText.addTextChangedListener(textWatcher)
        textWatchers[config.editText] = textWatcher
        if (config.showErrorImmediately) {
            val initialText = config.editText.text?.toString() ?: ""
            validateField(config, initialText, showError = true)
        }
    }
    private fun validateField(config: ValidationConfig, text: String, showError: Boolean = true): Boolean {
        for (validator in config.validators) {
            val result = validator.validate(text)
            if (!result.isValid) {
                if (showError) {
                    config.layout.error = result.errorMessage
                    UIUtils.shakeView(config.layout)
                }
                return false
            }
        }
        config.layout.error = null
        return true
    }
    fun validateAll(): Boolean {
        var allValid = true
        for (config in validationConfigs) {
            val text = config.editText.text?.toString() ?: ""
            val isValid = validateField(config, text, showError = true)
            if (!isValid) {
                allValid = false
            }
        }
        return allValid
    }
    fun clearErrors() {
        for (config in validationConfigs) {
            config.layout.error = null
        }
    }
    fun removeAllValidations() {
        for ((editText, textWatcher) in textWatchers) {
            editText.removeTextChangedListener(textWatcher)
        }
        textWatchers.clear()
        validationConfigs.clear()
    }
    fun removeFieldValidation(editText: TextInputEditText) {
        textWatchers[editText]?.let { textWatcher ->
            editText.removeTextChangedListener(textWatcher)
            textWatchers.remove(editText)
        }
        validationConfigs.removeAll { it.editText == editText }
    }
    fun getValidationStatus(): Map<TextInputEditText, Boolean> {
        val status = mutableMapOf<TextInputEditText, Boolean>()
        for (config in validationConfigs) {
            val text = config.editText.text?.toString() ?: ""
            status[config.editText] = validateField(config, text, showError = false)
        }
        return status
    }
    fun setupPasswordValidation(
        passwordEditText: TextInputEditText,
        passwordLayout: TextInputLayout,
        confirmPasswordEditText: TextInputEditText,
        confirmPasswordLayout: TextInputLayout
    ) {
        addField(
            passwordEditText,
            passwordLayout,
            Validators.required("Ingresa una contraseña"),
            Validators.passwordStrength()
        )
        addField(
            confirmPasswordEditText,
            confirmPasswordLayout,
            Validators.required("Confirma la contraseña"),
            Validators.passwordMatch(passwordEditText)
        )
        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!confirmPasswordEditText.text.isNullOrEmpty()) {
                    val confirmText = confirmPasswordEditText.text?.toString() ?: ""
                    val config = validationConfigs.find { it.editText == confirmPasswordEditText }
                    config?.let { validateField(it, confirmText, showError = true) }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}