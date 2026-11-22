package com.faldy.remoteview.pairing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.faldy.remoteview.MainActivity

class ScreenCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val imageThread = HandlerThread("FALDY-VIEW-Capture").apply { start() }
    private val imageHandler = Handler(imageThread.looper)
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var displayManager: DisplayManager? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var densityDpi: Int = 0
    private var stopping = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            releaseResources(stopProjection = false)
            ScreenShareState.stopped("Berbagi layar dihentikan oleh sistem atau pengguna")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width > 0 && height > 0) resizeCapture(width, height)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            return START_NOT_STICKY
        }
        if (projection != null) return START_NOT_STICKY

        try {
            startAsForegroundService()
            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            val resultData = projectionResult(intent)
            if (resultCode != Activity.RESULT_OK || resultData == null) {
                throw IllegalArgumentException("Persetujuan MediaProjection tidak valid")
            }
            val manager = getSystemService(MediaProjectionManager::class.java)
            projection = manager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("MediaProjection tidak tersedia")
            projection?.registerCallback(projectionCallback, handler)
            setupInitialDisplay()
            val captureReader = imageReader ?: throw IllegalStateException("Surface capture tidak tersedia")
            ScreenShareState.active(captureReader.width, captureReader.height)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) registerDisplayListener()
        } catch (exception: Exception) {
            fail("Tidak dapat memulai berbagi layar: ${exception.message ?: "kesalahan sistem"}")
        }
        return START_NOT_STICKY
    }

    private fun projectionResult(intent: Intent?): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent?.getParcelableExtra(EXTRA_RESULT_DATA)
    }

    private fun startAsForegroundService() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        ScreenShareState.starting()
    }

    private fun setupInitialDisplay() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        densityDpi = metrics.densityDpi
        createCaptureSurface(metrics.widthPixels, metrics.heightPixels)
    }

    private fun createCaptureSurface(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Dimensi layar tidak valid" }
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ source ->
            try {
                source.acquireLatestImage()?.close()
            } catch (_: IllegalStateException) {
                // The surface can close while a frame callback is queued.
            }
        }, imageHandler)
        val currentProjection = projection ?: run {
            reader.close()
            throw IllegalStateException("Sesi MediaProjection sudah berhenti")
        }

        var installed = false
        try {
            if (virtualDisplay == null) {
                virtualDisplay = currentProjection.createVirtualDisplay(
                    "FALDY VIEW screen capture",
                    width,
                    height,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface,
                    null,
                    handler
                ) ?: throw IllegalStateException("Virtual display tidak dapat dibuat")
            } else {
                virtualDisplay?.resize(width, height, densityDpi)
                virtualDisplay?.setSurface(reader.surface)
            }
            val oldReader = imageReader
            imageReader = reader
            installed = true
            oldReader?.setOnImageAvailableListener(null, null)
            oldReader?.close()
        } finally {
            if (!installed) reader.close()
        }
    }

    private fun resizeCapture(width: Int, height: Int) {
        if (stopping || (imageReader?.width == width && imageReader?.height == height)) return
        runCatching { createCaptureSurface(width, height) }
            .onSuccess { imageReader?.let { ScreenShareState.resized(it.width, it.height) } }
            .onFailure { fail("Ukuran layar berubah, tetapi capture tidak dapat diperbarui") }
    }

    private fun registerDisplayListener() {
        val manager = getSystemService(DisplayManager::class.java)
        displayManager = manager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == android.view.Display.DEFAULT_DISPLAY) refreshDisplaySize()
            }
        }
        displayListener = listener
        manager.registerDisplayListener(listener, handler)
    }

    private fun refreshDisplaySize() {
        if (stopping) return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        if (metrics.densityDpi != densityDpi) densityDpi = metrics.densityDpi
        resizeCapture(metrics.widthPixels, metrics.heightPixels)
    }

    private fun stopCapture() {
        stopping = true
        releaseResources(stopProjection = true)
        ScreenShareState.stopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(message: String) {
        stopping = true
        releaseResources(stopProjection = true)
        ScreenShareState.error(message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseResources(stopProjection: Boolean) {
        stopping = true
        val oldListener = displayListener
        displayListener = null
        runCatching { if (oldListener != null) displayManager?.unregisterDisplayListener(oldListener) }
        displayManager = null
        val oldReader = imageReader
        imageReader = null
        runCatching { oldReader?.setOnImageAvailableListener(null, null) }
        runCatching { oldReader?.close() }
        val oldDisplay = virtualDisplay
        virtualDisplay = null
        runCatching { oldDisplay?.setSurface(null) }
        runCatching { oldDisplay?.release() }
        val current = projection
        projection = null
        runCatching { current?.unregisterCallback(projectionCallback) }
        if (stopProjection) runCatching { current?.stop() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, "Berbagi layar", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Indikator sesi berbagi layar FALDY VIEW"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = PendingIntent.getService(
            this,
            2,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("FALDY VIEW sedang berbagi layar")
            .setContentText("Ketuk Hentikan untuk mengakhiri sesi layar")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, "Hentikan", stopAction)
            .build()
    }

    override fun onDestroy() {
        if (projection != null) {
            releaseResources(stopProjection = true)
        }
        if (ScreenShareState.state.value.status == ScreenShareStatus.Active ||
            ScreenShareState.state.value.status == ScreenShareStatus.Starting
        ) {
            ScreenShareState.stopped("Berbagi layar dihentikan")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        imageThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.faldy.remoteview.action.STOP_SCREEN_SHARE"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        private const val CHANNEL_ID = "faldy_screen_share"
        private const val NOTIFICATION_ID = 51
    }
}
