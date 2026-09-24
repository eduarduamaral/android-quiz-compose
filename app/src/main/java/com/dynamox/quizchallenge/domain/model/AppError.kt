package com.dynamox.quizchallenge.domain.model

/**
 * Domain-level representation of everything that can go wrong when talking to the quiz backend.
 * The data layer is responsible for translating raw exceptions/HTTP status codes into these
 * types, so the UI and ViewModels never need to know about Retrofit, OkHttp or HTTP codes.
 */
sealed class AppError : Throwable() {

    /** No usable network connection, or the request timed out. */
    data object Network : AppError()

    /** The server rejected the request as invalid, e.g. an unknown questionId (HTTP 400/404). */
    data object InvalidRequest : AppError()

    /** The server responded with an unexpected error (HTTP 5xx). */
    data object Server : AppError()

    /** Anything else: unexpected response shape, serialization failure, etc. */
    data class Unknown(override val message: String? = null) : AppError()
}
