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

        ScreenShareState.active(1080, 2400)
        assertEquals(ScreenShareStatus.Active, ScreenShareState.state.value.status)
        assertEquals("Layar Receiver sedang dibagikan", ScreenShareState.state.value.message)
        assertEquals(1080, ScreenShareState.state.value.widthPx)
        assertEquals(2400, ScreenShareState.state.value.heightPx)

        ScreenShareState.resized(2400, 1080)
        assertEquals(2400, ScreenShareState.state.value.widthPx)
        assertEquals(1080, ScreenShareState.state.value.heightPx)

        ScreenShareState.stopped("Izin dibatalkan")
        assertEquals(ScreenShareStatus.Stopped, ScreenShareState.state.value.status)
        assertEquals("Izin dibatalkan", ScreenShareState.state.value.message)
        assertEquals(0, ScreenShareState.state.value.widthPx)
        assertEquals(0, ScreenShareState.state.value.heightPx)
    }
}
