# SessionManager Firebase Integration

## Overview

This document describes the Firebase Auth integration enhancements implemented in the SessionManager class as part of task 4 of the persistent session authentication feature.

## Key Enhancements

### 1. Network Retry Logic

The SessionManager now includes robust retry logic for handling network errors when communicating with Firebase Auth:

- **Maximum Retry Attempts**: 3 attempts for network-related errors
- **Exponential Backoff**: Retry delays increase with each attempt (1s, 2s, 3s)
- **Network Error Detection**: Automatically detects and handles various network error types:
  - `UnknownHostException` (DNS resolution failures)
  - `SocketTimeoutException` (Connection timeouts)
  - `IOException` (General network I/O errors)
  - Custom error message detection for network-related issues

### 2. Enhanced Token Verification

#### `verifyWithFirebase()` Method
- Implements retry logic for token verification
- Handles user mismatch detection between local session and Firebase
- Provides graceful fallback to offline verification on network errors

#### `verifyTokenWithRetry()` Method
- Dedicated method for retrying Firebase token verification
- Uses exponential backoff strategy
- Distinguishes between network and authentication errors

### 3. Improved Token Refresh

#### `refreshSession()` Method
- Enhanced with retry logic for token refresh operations
- Maintains current session during network errors if token hasn't expired
- Handles edge cases where Firebase user is no longer available

#### `refreshTokenWithRetry()` Method
- Dedicated method for retrying Firebase token refresh
- Forces token refresh from Firebase (`getIdToken(true)`)
- Implements same retry strategy as verification

### 4. Offline Verification Support

The SessionManager now provides robust offline support:

- **Valid Local Tokens**: Allows access when network is unavailable but local token hasn't expired
- **Expired Local Tokens**: Denies access even offline if local token has expired
- **Network Error Handling**: Gracefully handles various network conditions

### 5. Error Classification

#### Network Errors (Retryable)
- `UnknownHostException`
- `SocketTimeoutException` 
- `IOException`
- Messages containing: "network", "timeout", "connection", "unreachable"

#### Authentication Errors (Non-retryable)
- Invalid credentials
- User disabled
- Token format errors
- Permission denied

## Implementation Details

### Constants
```kotlin
private const val MAX_RETRY_ATTEMPTS = 3
private const val RETRY_DELAY_MS = 1000L
```

### Key Methods

#### `isNetworkError(exception: Exception): Boolean`
Determines if an exception represents a network error that should trigger retry logic.

#### `handleVerificationError(exception: Exception, sessionData: SessionData): Boolean`
Handles verification errors by determining appropriate response based on error type and session state.

#### `verifyTokenWithRetry(user: FirebaseUser): String?`
Performs Firebase token verification with retry logic for network errors.

#### `refreshTokenWithRetry(user: FirebaseUser): String?`
Performs Firebase token refresh with retry logic for network errors.

## Testing

### Integration Tests
- `SessionManagerFirebaseIntegrationTest`: Tests Firebase Auth integration scenarios
- `SessionManagerNetworkTest`: Tests network error handling and retry logic
- `SessionManagerCompilationTest`: Verifies compilation and basic functionality

### Test Coverage
- Firebase token verification success/failure scenarios
- Network error retry logic
- Offline access with valid/expired tokens
- Token refresh with various error conditions
- User mismatch detection
- Corrupted session data handling

## Requirements Fulfilled

This implementation fulfills the following requirements from task 4:

✅ **Integrar SessionManager con Firebase Auth para validación de tokens**
- Complete Firebase Auth integration with token verification

✅ **Implementar refreshSession() para renovar tokens expirados automáticamente**  
- Enhanced refreshSession() with automatic retry logic

✅ **Crear lógica de reintentos para manejo de errores de red**
- Comprehensive retry logic with exponential backoff

✅ **Implementar verificación offline para casos sin conectividad**
- Robust offline verification based on local token validity

✅ **Escribir tests de integración con Firebase Auth**
- Comprehensive test suite covering all integration scenarios

## Usage Examples

### Basic Session Validation
```kotlin
val sessionManager = SessionManager.getInstance(context)
val isValid = sessionManager.isSessionValid()
// Automatically handles network errors and retries
```

### Token Refresh
```kotlin
val refreshed = sessionManager.refreshSession()
// Automatically retries on network errors
// Maintains session during temporary network issues
```

### Offline Support
```kotlin
// Works offline if local token is still valid
val isValid = sessionManager.isSessionValid()
// Returns true for valid local tokens even without network
```

## Error Handling

The implementation provides comprehensive error handling:

1. **Network Errors**: Automatic retry with exponential backoff
2. **Authentication Errors**: Immediate failure without retry
3. **Offline Scenarios**: Graceful fallback to local token validation
4. **Corrupted Data**: Automatic cleanup and re-authentication requirement

## Performance Considerations

- **Retry Delays**: Exponential backoff prevents overwhelming the network
- **Timeout Handling**: Appropriate timeouts prevent indefinite blocking
- **Memory Management**: Proper cleanup of Firebase resources
- **Background Processing**: All operations run on appropriate coroutine dispatchers