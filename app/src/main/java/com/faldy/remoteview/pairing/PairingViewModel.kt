package com.faldy.remoteview.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class PairingViewModel(baseUrl: String) : ViewModel() {
    private val api: PairingApi? = if (baseUrl.isBlank()) null else runCatching { HttpPairingApi(baseUrl) }.getOrNull()
    private val configurationError = if (baseUrl.isBlank()) {
        "API belum dikonfigurasi. Atur Gradle property faldyApiBaseUrl."
    } else if (api == null) {
        "API URL tidak valid atau server publik tidak memakai HTTPS."
    } else null

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(PairingUiState(error = configurationError))
    val state: kotlinx.coroutines.flow.StateFlow<PairingUiState> = _state
    private var pollingJob: Job? = null

    fun createSession() = runOperation {
        val created = io { requireApi().createSession() }
        _state.value = PairingUiState(
            status = PairingStatus.Waiting,
            role = PairingRole.Controller,
            sessionId = created.sessionId,
            pairingCode = created.pairingCode,
            expiresAt = created.expiresAt
        )
        controllerToken = created.controllerToken
        receiverToken = null
        startPolling(created.sessionId, created.controllerToken)
    }

    fun joinSession(code: String) {
        if (!code.matches(Regex("\\d{6}"))) {
            _state.value = _state.value.copy(error = "Masukkan kode pairing enam digit.")
            return
        }
        runOperation {
            val joined = io { requireApi().joinSession(code) }
            _state.value = PairingUiState(
                status = PairingStatus.ApprovalPending,
                role = PairingRole.Receiver,
                sessionId = joined.sessionId,
                expiresAt = joined.expiresAt
            )
            receiverToken = joined.receiverToken
            controllerToken = null
            startPolling(joined.sessionId, joined.receiverToken)
        }
    }

    fun approve() = sessionAction("approve") { api, id, token -> api.approve(id, token) }
    fun reject() = endSession { api, id, token -> api.reject(id, token) }
    fun cancel() = endSession { api, id, token -> api.cancel(id, token) }
    fun leave() = endSession { api, id, token -> api.leave(id, token) }
    fun disconnect() = endSession { api, id, token -> api.disconnect(id, token) }

    private fun sessionAction(name: String, action: (PairingApi, String, String) -> Unit) {
        val current = _state.value
        val credentials = credentials(current) ?: return
        runOperation {
            io { action(requireApi(), credentials.first, credentials.second) }
            if (name == "approve") _state.value = _state.value.copy(status = PairingStatus.Connected)
        }
    }

    private fun endSession(action: (PairingApi, String, String) -> Unit) {
        val current = _state.value
        val credentials = credentials(current) ?: return
        runOperation {
            io { action(requireApi(), credentials.first, credentials.second) }
            pollingJob?.cancel()
            controllerToken = null
            receiverToken = null
            _state.value = PairingUiState(status = PairingStatus.Disconnected)
        }
    }

    private fun startPolling(sessionId: String, bearer: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var waitMs = 1_500L
            while (isActive) {
                delay(waitMs)
                try {
                    val remoteStatus = io { requireApi().getStatus(sessionId, bearer) }
                    val mapped = PairingStatus.fromApi(remoteStatus)
                        ?: throw PairingApiException("Status sesi dari server tidak dikenal")
                    if (mapped == PairingStatus.Disconnected) {
                        controllerToken = null
                        receiverToken = null
                        _state.value = PairingUiState(status = mapped, error = "Sesi telah berakhir atau kedaluwarsa.")
                        break
                    }
                    _state.value = _state.value.copy(status = mapped, isReconnecting = false, error = null)
                    waitMs = 2_000L
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (exception: Exception) {
                    if (exception is PairingApiException && exception.httpStatus == 404) {
                        controllerToken = null
                        receiverToken = null
                        _state.value = PairingUiState(status = PairingStatus.Disconnected, error = "Sesi tidak tersedia atau telah kedaluwarsa.")
                        break
                    }
                    if (exception is PairingApiException && exception.httpStatus == 401) {
                        controllerToken = null
                        receiverToken = null
                        _state.value = PairingUiState(status = PairingStatus.Disconnected, error = "Autentikasi sesi tidak berlaku lagi.")
                        break
                    }
                    _state.value = _state.value.copy(
                        isReconnecting = true,
                        error = "Koneksi terganggu. Memeriksa ulang dalam ${waitMs / 1_000} detik."
                    )
                    waitMs = (waitMs * 2).coerceAtMost(10_000L)
                }
            }
        }
    }

    private fun credentials(state: PairingUiState): Pair<String, String>? {
        val id = state.sessionId ?: return null
        val role = state.role ?: return null
        val token = when (role) {
            PairingRole.Controller -> controllerToken
            PairingRole.Receiver -> receiverToken
        } ?: return null
        return id to token
    }

    // Credentials remain in process memory and are discarded when the session ends.
    private var controllerToken: String? = null
    private var receiverToken: String? = null

    private fun runOperation(block: suspend () -> Unit) {
        if (_state.value.isLoading) return
        if (api == null) {
            _state.value = _state.value.copy(error = configurationError ?: "API belum siap.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                block()
                _state.value = _state.value.copy(isLoading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = friendlyError(exception))
            }
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun requireApi(): PairingApi = api ?: throw PairingApiException(configurationError ?: "API belum siap")

    private fun friendlyError(exception: Exception): String = when (exception) {
        is PairingApiException -> when {
            exception.httpStatus == null -> "Jaringan timeout atau terputus. Operasi tidak diulang otomatis karena hasilnya mungkin sudah diproses server."
            exception.apiError == "pairing_code_unavailable" -> "Kode tidak ditemukan, sudah dipakai, atau kedaluwarsa."
            exception.apiError == "rate_limited" -> "Terlalu banyak percobaan. Tunggu sebelum mencoba lagi."
            exception.apiError == "unauthorized" -> "Token sesi ditolak oleh server."
            else -> exception.message ?: "Permintaan pairing gagal."
        }
        is IOException -> "Jaringan bermasalah. Operasi tidak diulang otomatis; periksa status sesi sebelum mencoba lagi."
        else -> exception.message ?: "Terjadi kesalahan saat pairing."
    }

    override fun onCleared() {
        pollingJob?.cancel()
        controllerToken = null
        receiverToken = null
        super.onCleared()
    }

    class Factory(private val baseUrl: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PairingViewModel::class.java))
            return PairingViewModel(baseUrl) as T
        }
    }
}
