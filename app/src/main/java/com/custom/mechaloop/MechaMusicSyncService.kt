package com.custom.mechaloop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.math.hypot

/**
 * Captures real-time FFT audio output (session 0 = global device audio mix from YouTube Morphe)
 * and pulses the Infinix GT 20 Pro Mecha Loop brightness/color on every bass hit.
 */
class MechaMusicSyncService : Service() {

    companion object {
        const val ACTION_START = "com.custom.mechaloop.START_SYNC"
        const val ACTION_STOP = "com.custom.mechaloop.STOP_SYNC"
        const val CHANNEL_ID = "mecha_music_sync_channel"
        var isRunning = false
    }

    private var visualizer: Visualizer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVisualizer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isRunning = false
            }
            else -> {
                startForegroundNotification()
                startVisualizer()
                isRunning = true
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mecha Loop Music Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mecha Loop Music Sync Active")
            .setContentText("Pulsing GT 20 Pro LEDs to active music playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    private fun startVisualizer() {
        if (visualizer != null) return
        val prefs = getSharedPreferences("mecha_prefs", Context.MODE_PRIVATE)
        val baseR = prefs.getInt("color_r", 0)
        val baseG = prefs.getInt("color_g", 240)
        val baseB = prefs.getInt("color_b", 255)

        try {
            // Audio session 0 captures global output mix (YouTube Morphe, YT Music, games, etc.)
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        v: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                    }

                    override fun onFftDataCapture(
                        v: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null || fft.size < 16) return
                        // Extract low-end bass magnitude (bins 1..6)
                        var bassEnergy = 0.0
                        for (i in 2 until 12 step 2) {
                            val real = fft[i].toDouble()
                            val imag = fft[i + 1].toDouble()
                            bassEnergy += hypot(real, imag)
                        }
                        val normalized = (bassEnergy / 450.0).coerceIn(0.12, 1.0)
                        val brightness = (normalized * 255).toInt()

                        MechaHardwareController.applyCustomColor(
                            applicationContext,
                            baseR,
                            baseG,
                            baseB,
                            brightness
                        )
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) {
            // Fallback static glow if Visualizer session 0 is blocked by OEM policy
            MechaHardwareController.applyCustomColor(applicationContext, baseR, baseG, baseB, 255)
        }
    }

    private fun stopVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
    }

    override fun onDestroy() {
        stopVisualizer()
        isRunning = false
        super.onDestroy()
    }
}
