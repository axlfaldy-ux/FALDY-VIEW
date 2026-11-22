package com.faldy.remoteview.pairing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ScreenShareStatus { Stopped, Starting, Active, Error }

data class ScreenShareUiState(
    val status: ScreenShareStatus = ScreenShareStatus.Stopped,
    val message: String = "Berbagi layar tidak aktif",
    val widthPx: Int = 0,
    val heightPx: Int = 0
)

object ScreenShareState {
    private val mutableState = MutableStateFlow(ScreenShareUiState())
    val state: StateFlow<ScreenShareUiState> = mutableState

    fun starting() {
        mutableState.value = ScreenShareUiState(ScreenShareStatus.Starting, "Memulai berbagi layar…")
    }

    fun active(widthPx: Int, heightPx: Int) {
        mutableState.value = ScreenShareUiState(
            ScreenShareStatus.Active,
            "Layar Receiver sedang dibagikan",
            widthPx,
            heightPx
        )
    }

    fun resized(widthPx: Int, heightPx: Int) {
        val current = mutableState.value
        if (current.status == ScreenShareStatus.Active && widthPx > 0 && heightPx > 0) {
            mutableState.value = current.copy(widthPx = widthPx, heightPx = heightPx)
        }
    }

    fun stopped(message: String = "Berbagi layar dihentikan") {
        mutableState.value = ScreenShareUiState(ScreenShareStatus.Stopped, message)
    }

    fun error(message: String) {
        mutableState.value = ScreenShareUiState(ScreenShareStatus.Error, message)
    }
}
