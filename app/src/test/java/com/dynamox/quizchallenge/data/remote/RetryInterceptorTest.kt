package com.dynamox.quizchallenge.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RetryInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            // Isolates the interceptor under test from OkHttp's own route-level retry.
            .retryOnConnectionFailure(false)
            .addInterceptor(RetryInterceptor(maxRetries = 2, backoffMillis = 0))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a GET that fails with an IO error is retried and succeeds`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"1"}"""))

        val response = client.newCall(Request.Builder().url(server.url("/question")).get().build()).execute()

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a GET gives up after the configured number of retries`() {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }

        assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url(server.url("/question")).get().build()).execute()
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a POST that fails with an IO error is not retried`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"result":true}"""))
        val body = """{"answer":"A"}""".toRequestBody("application/json".toMediaType())

        assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url(server.url("/answer")).post(body).build()).execute()
        }
        assertEquals(1, server.requestCount)
    }
}
