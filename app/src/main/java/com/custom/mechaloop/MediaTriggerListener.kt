package com.custom.mechaloop

import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat

/**
 * Watches active MediaSessions (YouTube Morphe, ReVanced, YouTube Music, Spotify, local players).
 * Automatically starts MechaMusicSyncService the moment music playback starts!
 */
class MediaTriggerListener : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            checkControllers(controllers)
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = android.content.ComponentName(this, MediaTriggerListener::class.java)
            sessionManager?.addOnActiveSessionsChangedListener(activeSessionsListener, componentName)
            checkControllers(sessionManager?.getActiveSessions(componentName))
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val prefs = getSharedPreferences("mecha_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_music_sync", true)) return

        val pkg = sbn?.packageName ?: return
        // Matches YouTube Morphe, ReVanced, standard YouTube, YT Music, Spotify, etc.
        if (pkg.contains("youtube", ignoreCase = true) ||
            pkg.contains("morphe", ignoreCase = true) ||
            pkg.contains("revanced", ignoreCase = true) ||
            pkg.contains("music", ignoreCase = true) ||
            pkg.contains("spotify", ignoreCase = true)
        ) {
            startSyncService()
        }
    }

    private fun checkControllers(controllers: List<MediaController>?) {
        val prefs = getSharedPreferences("mecha_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_music_sync", true)) return

        val isAnyPlaying = controllers?.any {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: false

        if (isAnyPlaying) {
            startSyncService()
        } else {
            stopSyncService()
        }
    }

    private fun startSyncService() {
        val intent = Intent(this, MechaMusicSyncService::class.java).apply {
            action = MechaMusicSyncService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopSyncService() {
        val intent = Intent(this, MechaMusicSyncService::class.java).apply {
            action = MechaMusicSyncService.ACTION_STOP
        }
        startService(intent)
    }
}
