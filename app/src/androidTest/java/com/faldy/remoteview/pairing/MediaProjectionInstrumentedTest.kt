package com.faldy.remoteview.pairing

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaProjectionInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private var scenario: ActivityScenario<CaptureConsentTestActivity>? = null

    @After
    fun cleanup() {
        scenario?.onActivity { it.pressStopAction() }
        scenario?.close()
        scenario = null
        waitForStatus(ScreenShareStatus.Stopped, timeoutMs = 5_000)
        device.setOrientationNatural()
    }

    @Test
    fun denyingSystemConsentDoesNotStartCapture() {
        launchConsentActivity()
        scenario!!.onActivity { it.requestCapture() }
        assertTrue("system consent dialog did not appear", device.wait(Until.hasObject(By.textContains("Start")), 10_000))

        device.pressBack()

        assertTrue("denial result was not handled", waitUntil(10_000) {
            ScreenShareState.state.value.status == ScreenShareStatus.Stopped &&
                ScreenShareState.state.value.message == "Izin berbagi layar dibatalkan"
        })
        assertEquals(0, ScreenShareState.state.value.widthPx)
        assertEquals(0, ScreenShareState.state.value.heightPx)
    }

    @Test
    fun grantedConsentStartsCaptureRotationResizesAndStopCleansUp() {
        launchConsentActivity()
        scenario!!.onActivity { it.requestCapture() }
        grantSystemConsent()

        assertTrue("capture did not become active", waitForStatus(ScreenShareStatus.Active, 20_000))
        val initialWidth = ScreenShareState.state.value.widthPx
        val initialHeight = ScreenShareState.state.value.heightPx
        assertTrue("active capture has invalid dimensions", initialWidth > 0 && initialHeight > 0)

        device.setOrientationLeft()
        assertTrue("capture size did not update after rotation", waitUntil(15_000) {
            val state = ScreenShareState.state.value
            state.status == ScreenShareStatus.Active && state.widthPx > 0 && state.heightPx > 0 &&
                (state.widthPx != initialWidth || state.heightPx != initialHeight)
        })

        scenario!!.onActivity { it.pressStopAction() }
        assertTrue("Stop action did not stop capture", waitForStatus(ScreenShareStatus.Stopped, 10_000))
        assertEquals(0, ScreenShareState.state.value.widthPx)
        assertEquals(0, ScreenShareState.state.value.heightPx)
    }

    @Test
    fun systemStoppingProjectionClearsActiveState() {
        launchConsentActivity()
        scenario!!.onActivity { it.requestCapture() }
        grantSystemConsent()
        assertTrue("capture did not become active", waitForStatus(ScreenShareStatus.Active, 20_000))

        device.sleep()
        val stoppedBySystem = waitForStatus(ScreenShareStatus.Stopped, 15_000)
        device.wakeUp()
        assertTrue("screen-off did not trigger MediaProjection stop callback", stoppedBySystem)
        assertEquals(0, ScreenShareState.state.value.widthPx)
        assertEquals(0, ScreenShareState.state.value.heightPx)
    }

    private fun launchConsentActivity() {
        ScreenShareState.stopped()
        val context = instrumentation.targetContext
        scenario = ActivityScenario.launch(
            Intent(context, CaptureConsentTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun grantSystemConsent() {
        val dialogVisible = device.wait(Until.hasObject(By.textContains("Start")), 10_000)
        assertTrue("system consent dialog did not appear", dialogVisible)
        val startButton = device.findObjects(By.clickable(true)).firstOrNull { node ->
            val label = node.text.orEmpty().lowercase()
            label.contains("start") || label.contains("share") || label.contains("now")
        }
        assertTrue("could not locate system consent confirmation button", startButton != null)
        startButton!!.click()
    }

    private fun waitForStatus(status: ScreenShareStatus, timeoutMs: Long = 10_000): Boolean =
        waitUntil(timeoutMs) { ScreenShareState.state.value.status == status }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }
}
