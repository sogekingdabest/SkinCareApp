package es.monsteraltech.skincare_tfm.account
sealed class AccountResult<T> {
    data class Success<T>(val data: T) : AccountResult<T>()
    data class Error<T>(
        val exception: Exception,
        val message: String,
        val errorType: ErrorType = ErrorType.GENERIC_ERROR
    ) : AccountResult<T>()
    data class Loading<T>(val message: String = "Cargando...") : AccountResult<T>()
    enum class ErrorType {
        NETWORK_ERROR,
        AUTHENTICATION_ERROR,
        VALIDATION_ERROR,
        FIREBASE_ERROR,
        PERMISSION_ERROR,
        GENERIC_ERROR
    }
    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
    fun isLoading(): Boolean = this is Loading
    fun getDataOrNull(): T? = if (this is Success) data else null
    fun getErrorOrNull(): Error<T>? = if (this is Error) this else null
    inline fun onSuccess(action: (T) -> Unit): AccountResult<T> {
        if (this is Success) action(data)
        return this
    }
    inline fun onError(action: (Error<T>) -> Unit): AccountResult<T> {
        if (this is Error) action(this)
        return this
    }
    inline fun onLoading(action: (Loading<T>) -> Unit): AccountResult<T> {
        if (this is Loading) action(this)
        return this
    }
    companion object {
        fun <T> success(data: T): AccountResult<T> = Success(data)
        fun <T> error(
            exception: Exception,
            message: String? = null,
            errorType: ErrorType? = null
        ): AccountResult<T> {
            val finalMessage = message ?: getDefaultErrorMessage(exception)
            val finalErrorType = errorType ?: detectErrorType(exception)
            return Error(exception, finalMessage, finalErrorType)
        }
        fun <T> loading(message: String = "Cargando..."): AccountResult<T> = Loading(message)
        private fun detectErrorType(exception: Exception): ErrorType {
            return when {
                exception.message?.contains("network", ignoreCase = true) == true ||
                exception.message?.contains("timeout", ignoreCase = true) == true ||
                exception.message?.contains("connection", ignoreCase = true) == true ->
                    ErrorType.NETWORK_ERROR
                exception.message?.contains("auth", ignoreCase = true) == true ||
                exception.message?.contains("permission", ignoreCase = true) == true ||
                exception.message?.contains("unauthorized", ignoreCase = true) == true ->
                    ErrorType.AUTHENTICATION_ERROR
                exception.message?.contains("firebase", ignoreCase = true) == true ||
                exception.message?.contains("firestore", ignoreCase = true) == true ->
                    ErrorType.FIREBASE_ERROR
                exception is IllegalArgumentException ||
                exception.message?.contains("validation", ignoreCase = true) == true ||
                exception.message?.contains("invalid", ignoreCase = true) == true ->
                    ErrorType.VALIDATION_ERROR
                else -> ErrorType.GENERIC_ERROR
            }
        }
        private fun getDefaultErrorMessage(exception: Exception): String {
            return when (detectErrorType(exception)) {
                ErrorType.NETWORK_ERROR -> "Error de conexión. Verifica tu conexión a internet e inténtalo de nuevo."
                ErrorType.AUTHENTICATION_ERROR -> "Error de autenticación. Por favor, inicia sesión nuevamente."
                ErrorType.FIREBASE_ERROR -> "Error del servidor. Inténtalo de nuevo más tarde."
                ErrorType.VALIDATION_ERROR -> "Datos inválidos. Verifica la información ingresada."
                ErrorType.PERMISSION_ERROR -> "No tienes permisos para realizar esta acción."
                ErrorType.GENERIC_ERROR -> "Ha ocurrido un error inesperado. Inténtalo de nuevo."
            }
        }
    }
}
inline fun <T, R> AccountResult<T>.map(transform: (T) -> R): AccountResult<R> {
    return when (this) {
        is AccountResult.Success -> AccountResult.Success(transform(data))
        is AccountResult.Error -> AccountResult.Error(exception, message, errorType)
        is AccountResult.Loading -> AccountResult.Loading(message)
    }
}
inline fun <T, R> AccountResult<T>.flatMap(transform: (T) -> AccountResult<R>): AccountResult<R> {
    return when (this) {
        is AccountResult.Success -> transform(data)
        is AccountResult.Error -> AccountResult.Error(exception, message, errorType)
        is AccountResult.Loading -> AccountResult.Loading(message)
    }
}