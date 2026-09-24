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
 * Integration test: exercises [QuizRepositoryImpl] against a real (local) HTTP server instead
 * of mocking Retrofit/OkHttp, so it also verifies the serialization + error-mapping wiring.
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
    fun `fetchQuestion maps a 500 response to AppError Server`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val result = repository.fetchQuestion()

        assertEquals(AppError.Server, result.exceptionOrNull())
    }

    @Test
    fun `submitAnswer maps a plain-text 400 response to AppError InvalidRequest`() = runTest {
        // The real backend returns a *plain text* body (not JSON) for this error, e.g.
        // "400 BAD REQUEST: Question not found." This guards against ever assuming the
        // error body is parseable JSON.
        server.enqueue(MockResponse().setResponseCode(400).setBody("400 BAD REQUEST: Question not found."))

        val result = repository.submitAnswer("unknown-id", "A")

        assertEquals(AppError.InvalidRequest, result.exceptionOrNull())
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
