package com.university.messenger.server

sealed class AppException(message: String) : RuntimeException(message) {
    class BadRequest(message: String) : AppException(message)
    class Unauthorized(message: String) : AppException(message)
    class Forbidden(message: String) : AppException(message)
    class NotFound(message: String) : AppException(message)
    class Conflict(message: String) : AppException(message)
}
