package com.faldy.remoteview.pairing

import java.time.Instant

enum class PairingStatus {
    Disconnected,
    Waiting,
    ApprovalPending,
    Connected;

    companion object {
        fun fromApi(value: String): PairingStatus? = when (value) {
            "waiting" -> Waiting
            "approval_pending" -> ApprovalPending
            "approved" -> Connected
            "cancelled", "rejected", "disconnected", "session_unavailable" -> Disconnected
            else -> null
        }
    }
}

enum class PairingRole { Controller, Receiver }

data class PairingUiState(
    val status: PairingStatus = PairingStatus.Disconnected,
    val role: PairingRole? = null,
    val sessionId: String? = null,
    val pairingCode: String? = null,
    val expiresAt: Instant? = null,
    val isLoading: Boolean = false,
    val isReconnecting: Boolean = false,
    val error: String? = null
)
