package com.faldy.remoteview.pairing

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant

interface PairingApi {
    fun createSession(): CreatedSession
    fun joinSession(code: String): JoinedSession
    fun getStatus(sessionId: String, token: String): String
    fun approve(sessionId: String, token: String)
    fun reject(sessionId: String, token: String)
    fun cancel(sessionId: String, token: String)
    fun leave(sessionId: String, token: String)
    fun disconnect(sessionId: String, token: String)
}

data class CreatedSession(
    val sessionId: String,
    val pairingCode: String,
    val controllerToken: String,
    val expiresAt: Instant
)

data class JoinedSession(
    val sessionId: String,
    val receiverToken: String,
    val expiresAt: Instant
)

class PairingApiException(
    message: String,
    val httpStatus: Int? = null,
    val apiError: String? = null,
    cause: Throwable? = null
) : IOException(message, cause)

class HttpPairingApi(
    baseUrl: String,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 8_000,
    private val gson: Gson = Gson()
) : PairingApi {
    private val baseUrl: String

    init {
        val normalized = baseUrl.trim().trimEnd('/')
        val uri = runCatching { URI(normalized) }.getOrElse {
            throw IllegalArgumentException("API base URL tidak valid", it)
        }
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "API base URL harus berupa origin/path tanpa kredensial atau query"
        }
        val isLoopback = uri.host.equals("localhost", ignoreCase = true) ||
            uri.host == "127.0.0.1"
        require(uri.scheme.equals("https", ignoreCase = true) || (uri.scheme.equals("http", ignoreCase = true) && isLoopback)) {
            "HTTPS wajib untuk server publik; HTTP hanya diizinkan pada alamat loopback/local emulator"
        }
        this.baseUrl = normalized
    }

    override fun createSession(): CreatedSession {
        val response = request("POST", "/v1/sessions")
        val body = parse(response, CreateResponse::class.java)
        require(body.sessionId.isNotBlank() && body.controllerToken.isNotBlank() && body.status == "waiting" && body.pairingCode.matches(Regex("\\d{6}"))) {
            "Respons create session tidak valid"
        }
        return CreatedSession(body.sessionId, body.pairingCode, body.controllerToken, Instant.parse(body.expiresAt))
    }

    override fun joinSession(code: String): JoinedSession {
        require(code.matches(Regex("\\d{6}"))) { "Kode pairing harus terdiri dari enam digit" }
        val response = request("POST", "/v1/sessions/join", body = mapOf("code" to code))
        val body = parse(response, JoinResponse::class.java)
        require(body.sessionId.isNotBlank() && body.receiverToken.isNotBlank() && body.status == "approval_pending") {
            "Respons join session tidak valid"
        }
        return JoinedSession(body.sessionId, body.receiverToken, Instant.parse(body.expiresAt))
    }

    override fun getStatus(sessionId: String, token: String): String {
        val response = request("GET", sessionPath(sessionId), token)
        return parse(response, StatusResponse::class.java).status
    }

    override fun approve(sessionId: String, token: String) = action(sessionId, "approve", token)
    override fun reject(sessionId: String, token: String) = action(sessionId, "reject", token)
    override fun cancel(sessionId: String, token: String) = action(sessionId, "cancel", token)
    override fun leave(sessionId: String, token: String) = action(sessionId, "leave", token)
    override fun disconnect(sessionId: String, token: String) = action(sessionId, "disconnect", token)

    private fun action(sessionId: String, action: String, token: String) {
        request("POST", "${sessionPath(sessionId)}/$action", token)
    }

    private fun sessionPath(sessionId: String): String {
        require(sessionId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Session ID tidak valid" }
        return "/v1/sessions/$sessionId"
    }

    private fun request(method: String, path: String, bearerToken: String? = null, body: Any? = null): String {
        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            if (bearerToken != null) setRequestProperty("Authorization", "Bearer $bearerToken")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(gson.toJson(body).toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val apiError = runCatching { JsonParser.parseString(responseBody).asJsonObject.get("error")?.asString }.getOrNull()
                throw PairingApiException(apiError ?: "Permintaan API gagal", status, apiError)
            }
            return responseBody
        } catch (exception: PairingApiException) {
            throw exception
        } catch (exception: Exception) {
            throw PairingApiException("Tidak dapat menghubungi server pairing", cause = exception)
        } finally {
            connection.disconnect()
        }
    }

    private fun <T> parse(response: String, type: Class<T>): T = try {
        gson.fromJson(response, type) ?: throw PairingApiException("Respons server kosong")
    } catch (exception: PairingApiException) {
        throw exception
    } catch (exception: Exception) {
        throw PairingApiException("Format respons server tidak valid", cause = exception)
    }

    private data class CreateResponse(
        val sessionId: String = "",
        val pairingCode: String = "",
        val controllerToken: String = "",
        val status: String = "",
        val expiresAt: String = ""
    )

    private data class JoinResponse(
        val sessionId: String = "",
        val receiverToken: String = "",
        val status: String = "",
        val expiresAt: String = ""
    )

    private data class StatusResponse(val status: String = "")
}
