package com.dynamox.quizchallenge.data.remote

import com.dynamox.quizchallenge.domain.model.AppError
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Runs a suspend Retrofit call and converts failures into a domain [AppError].
 * Deliberately does not catch a blanket [Exception]: that would swallow CancellationException.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: HttpException) {
    val error = when (e.code()) {
        400 -> AppError.BadRequest
        404 -> AppError.NotFound
        in 500..599 -> AppError.ServerError
        else -> AppError.Unknown(e.message())
    }
    Result.failure(error)
} catch (e: IOException) {
    Result.failure(AppError.Network)
} catch (e: SerializationException) {
    Result.failure(AppError.Unknown(e.message))
}
