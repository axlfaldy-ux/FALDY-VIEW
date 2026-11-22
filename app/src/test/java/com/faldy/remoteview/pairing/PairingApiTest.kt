package com.faldy.remoteview.pairing

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PairingApiTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(readTimeoutMs: Int = 1_000) = HttpPairingApi(
        baseUrl = server.url("/").toString().trimEnd('/'),
        connectTimeoutMs = 1_000,
        readTimeoutMs = readTimeoutMs
    )

    @Test
    fun createSessionParsesCodeTokenExpiryAndUsesExpectedRoute() {
        server.enqueue(json("""
            {"sessionId":"a1234567-1234-1234-1234-123456789012","pairingCode":"004219","controllerToken":"secret-token","status":"waiting","expiresAt":"2026-11-22T04:00:00Z"}
        """))

        val created = api().createSession()
        val request = server.takeRequest()

        assertEquals("004219", created.pairingCode)
        assertEquals("secret-token", created.controllerToken)
        assertEquals("POST", request.method)
        assertEquals("/v1/sessions", request.path)
        assertFalse(request.path!!.contains("secret-token"))
    }

    @Test
    fun joinAndApproveUseBearerCredentialsWithoutPuttingThemInUrl() {
        server.enqueue(json("""
            {"sessionId":"a1234567-1234-1234-1234-123456789012","receiverToken":"receiver-secret","status":"approval_pending","expiresAt":"2026-11-22T04:00:00Z"}
        """))
        server.enqueue(json("""{"sessionId":"a1234567-1234-1234-1234-123456789012","status":"approved"}"""))
        server.enqueue(json("""{"sessionId":"a1234567-1234-1234-1234-123456789012","status":"approved"}"""))

        val client = api()
        val joined = client.joinSession("004219")
        val joinRequest = server.takeRequest()
        assertEquals("POST", joinRequest.method)
        assertEquals("/v1/sessions/join", joinRequest.path)
        assertTrue(joinRequest.body.readUtf8().contains("004219"))
        assertEquals("receiver-secret", joined.receiverToken)

        assertEquals("approved", client.getStatus(joined.sessionId, joined.receiverToken))
        val statusRequest = server.takeRequest()
        assertEquals("GET", statusRequest.method)
        assertEquals("Bearer receiver-secret", statusRequest.getHeader("Authorization"))
        assertFalse(statusRequest.path!!.contains("receiver-secret"))

        client.approve(joined.sessionId, "controller-secret")
        val approveRequest = server.takeRequest()
        assertEquals("/v1/sessions/${joined.sessionId}/approve", approveRequest.path)
        assertEquals("Bearer controller-secret", approveRequest.getHeader("Authorization"))
    }

    @Test
    fun rejectsPublicHttpAndMalformedCodes() {
        assertThrows(IllegalArgumentException::class.java) { HttpPairingApi("http://example.com") }
        assertThrows(IllegalArgumentException::class.java) { HttpPairingApi("https://user:password@example.com") }
        assertThrows(IllegalArgumentException::class.java) { api().joinSession("123") }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun surfacesServerErrorsAndDoesNotRetryMutatingRequestsAfterTimeout() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate_limited"}"""))
        val error = assertThrows(PairingApiException::class.java) { api().createSession() }
        assertEquals(429, error.httpStatus)
        assertEquals("rate_limited", error.apiError)
        server.takeRequest()

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertThrows(PairingApiException::class.java) { api(readTimeoutMs = 150).createSession() }
        server.takeRequest()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun mapsServerStatesToUiStates() {
        assertEquals(PairingStatus.Waiting, PairingStatus.fromApi("waiting"))
        assertEquals(PairingStatus.ApprovalPending, PairingStatus.fromApi("approval_pending"))
        assertEquals(PairingStatus.Connected, PairingStatus.fromApi("approved"))
        assertEquals(PairingStatus.Disconnected, PairingStatus.fromApi("session_unavailable"))
        assertEquals(null, PairingStatus.fromApi("unexpected"))
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body.trimIndent())
}
