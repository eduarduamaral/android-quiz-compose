package com.dynamox.quizchallenge.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retries a request a few times on [IOException]: the backend runs on Cloud Run and a cold start
 * can make the first connection time out.
 *
 * Only GET is retried. A POST that failed mid-flight may already have been processed by the
 * server, so replaying it could record the same answer twice.
 */
internal class RetryInterceptor(
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val backoffMillis: Long = DEFAULT_BACKOFF_MILLIS,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)

        var attempt = 0
        while (true) {
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                if (attempt >= maxRetries) throw e
                attempt++
                backOff(backoffMillis * attempt, e)
            }
        }
    }

    private fun backOff(millis: Long, cause: IOException) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw cause
        }
    }

    private companion object {
        const val DEFAULT_MAX_RETRIES = 2
        const val DEFAULT_BACKOFF_MILLIS = 500L
    }
}
