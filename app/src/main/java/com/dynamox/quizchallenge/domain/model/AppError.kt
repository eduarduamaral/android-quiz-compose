package com.dynamox.quizchallenge.domain.model

/**
 * Everything that can go wrong when talking to the quiz backend, as seen by the domain and UI.
 * The data layer maps exceptions and HTTP status codes into these; nothing above it sees HTTP.
 */
sealed class AppError : Throwable() {

    /** No usable network connection, or the request timed out. */
    data object Network : AppError()

    /** HTTP 400: the server rejected the request as invalid (e.g. a malformed body). */
    data object BadRequest : AppError()

    /**
     * HTTP 404: the requested resource does not exist. The real backend currently reports an
     * unknown `questionId` as 400, but the mapping is kept complete.
     */
    data object NotFound : AppError()

    /** HTTP 5xx: the server encountered an unexpected error. */
    data object ServerError : AppError()

    /** Any other unexpected HTTP status code, or a response the app couldn't parse. */
    data class Unknown(override val message: String? = null) : AppError()
}
