package com.faldy.remoteview.pairing

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenShareStateTest {
    @Test
    fun tracksConsentStartActiveAndStopStates() {
        ScreenShareState.stopped()
        assertEquals(ScreenShareStatus.Stopped, ScreenShareState.state.value.status)

        ScreenShareState.starting()
        assertEquals(ScreenShareStatus.Starting, ScreenShareState.state.value.status)

        ScreenShareState.active()
        assertEquals(ScreenShareStatus.Active, ScreenShareState.state.value.status)
        assertEquals("Layar Receiver sedang dibagikan", ScreenShareState.state.value.message)

        ScreenShareState.stopped("Izin dibatalkan")
        assertEquals(ScreenShareStatus.Stopped, ScreenShareState.state.value.status)
        assertEquals("Izin dibatalkan", ScreenShareState.state.value.message)
    }
}
