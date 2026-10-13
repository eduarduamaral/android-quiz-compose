package com.dynamox.quizchallenge.data.repository

import com.dynamox.quizchallenge.data.remote.QuizApi
import com.dynamox.quizchallenge.domain.model.AppError
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Exercises [QuizRepositoryImpl] against a local HTTP server, so serialization and error mapping
 * are verified through the real Retrofit/OkHttp stack.
 */
class QuizRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: QuizRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = QuizRepositoryImpl(retrofit.create(QuizApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchQuestion parses a successful response into a domain Question`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"id":"22","statement":"What?","options":["A","B","C"]}"""),
        )

        val question = repository.fetchQuestion().getOrThrow()

        assertEquals("22", question.id)
        assertEquals(listOf("A", "B", "C"), question.options)
    }

    @Test
    fun `fetchQuestion fails when the response has fewer than 2 options`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"id":"22","statement":"What?","options":["Only one"]}"""),
        )

        val result = repository.fetchQuestion()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.Unknown)
    }

    @Test
    fun `fetchQuestion maps a 500 response to AppError ServerError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val result = repository.fetchQuestion()

        assertEquals(AppError.ServerError, result.exceptionOrNull())
    }

    @Test
    fun `submitAnswer maps a plain-text 400 response to AppError BadRequest`() = runTest {
        // The real backend answers an unknown questionId with a plain-text 400 body, not JSON.
        server.enqueue(MockResponse().setResponseCode(400).setBody("400 BAD REQUEST: Question not found."))

        val result = repository.submitAnswer("unknown-id", "A")

        assertEquals(AppError.BadRequest, result.exceptionOrNull())
    }

    @Test
    fun `fetchQuestion maps a 404 response to AppError NotFound`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))

        val result = repository.fetchQuestion()

        assertEquals(AppError.NotFound, result.exceptionOrNull())
    }

    @Test
    fun `submitAnswer parses the result flag from a successful response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"result": true}"""))

        val result = repository.submitAnswer("22", "Dynamox")

        assertTrue(result.getOrThrow())
    }

    @Test
    fun `fetchQuestion maps an unreachable server to AppError Network`() = runTest {
        server.shutdown() // closing the server before the call simulates a connection failure

        val result = repository.fetchQuestion()

        assertEquals(AppError.Network, result.exceptionOrNull())
    }
}
