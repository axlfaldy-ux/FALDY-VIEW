package com.faldy.remoteview.pairing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ScreenShareStatus { Stopped, Starting, Active, Error }

data class ScreenShareUiState(
    val status: ScreenShareStatus = ScreenShareStatus.Stopped,
    val message: String = "Berbagi layar tidak aktif"
)

object ScreenShareState {
    private val mutableState = MutableStateFlow(ScreenShareUiState())
    val state: StateFlow<ScreenShareUiState> = mutableState

    fun starting() {
        mutableState.value = ScreenShareUiState(ScreenShareStatus.Starting, "Memulai berbagi layar…")
    }

    fun active() {
        mutableState.value = ScreenShareUiState(ScreenShareStatus.Active, "Layar Receiver sedang dibagikan")
    }

    fun stopped(message: String = "Berbagi layar dihentikan") {
        mutableState.value = ScreenShareUiState(ScreenShareStatus.Stopped, message)
    }

    fun error(message: String) {
        mutableState.value = ScreenShareUiState(ScreenShareStatus.Error, message)
    }
}
