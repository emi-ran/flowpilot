package com.flowpilot.app.engine

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.permission.CapabilityManager

/** Event data is transient and must not be written to logs or history. */
data class MediaSessionEvent(
    val event: TriggerEvent,
    val packageName: String,
    val title: String,
    val artist: String,
)

internal data class MediaSnapshot(
    val playing: Boolean?,
    val mediaId: String?,
    val title: String,
    val artist: String,
    val lastTrack: MediaTrack? = null,
) {
    val hasTrack: Boolean get() = !mediaId.isNullOrEmpty() || title.isNotEmpty() || artist.isNotEmpty()
}

internal data class MediaTrack(val mediaId: String?, val title: String, val artist: String)

/** Per-session comparisons; the first observation only seeds state. */
internal object MediaSessionReducer {
    fun nextSnapshot(previous: MediaSnapshot?, current: MediaSnapshot): MediaSnapshot = current.copy(
        playing = current.playing ?: previous?.playing,
        lastTrack = if (current.hasTrack) MediaTrack(current.mediaId, current.title, current.artist) else previous?.lastTrack
            ?: previous?.takeIf { it.hasTrack }?.let { MediaTrack(it.mediaId, it.title, it.artist) },
    )

    fun playbackUpdate(previous: MediaSnapshot, playing: Boolean?): MediaSnapshot = previous.copy(playing = playing)

    fun metadataUpdate(previous: MediaSnapshot, mediaId: String?, title: String, artist: String): MediaSnapshot =
        previous.copy(mediaId = mediaId, title = title, artist = artist)

    fun reduce(packageName: String, previous: MediaSnapshot?, current: MediaSnapshot): List<MediaSessionEvent> {
        if (previous == null) return emptyList()
        val events = mutableListOf<MediaSessionEvent>()
        fun emit(event: TriggerEvent) { events.add(MediaSessionEvent(event, packageName, current.title, current.artist)) }
        if (current.playing != null && previous.playing != null && current.playing != previous.playing) {
            emit(if (current.playing) TriggerEvent.MEDIA_PLAYING else TriggerEvent.MEDIA_PAUSED)
        }
        val previousTrack = previous.lastTrack ?: previous.takeIf { it.hasTrack }
            ?.let { MediaTrack(it.mediaId, it.title, it.artist) }
        if (previousTrack != null && current.hasTrack &&
            previousTrack != MediaTrack(current.mediaId, current.title, current.artist)
        ) emit(TriggerEvent.MEDIA_TRACK_CHANGED)
        return events
    }
}

/** Remembers tokens for the lifetime of a tracker start, including temporary disappearances. */
internal class MediaSessionObservations<T> {
    private val seen = mutableSetOf<T>()

    fun initialEvent(token: T, packageName: String, snapshot: MediaSnapshot, seeding: Boolean): MediaSessionEvent? {
        val firstObservation = seen.add(token)
        return if (!seeding && firstObservation && snapshot.playing == true) {
            MediaSessionEvent(TriggerEvent.MEDIA_PLAYING, packageName, snapshot.title, snapshot.artist)
        } else null
    }

    fun clear() = seen.clear()
}

/** Tracks all active sessions only while explicitly started by the engine. */
class MediaSessionTracker(context: Context, private val onEvent: (MediaSessionEvent) -> Unit) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listenerComponent = ComponentName(appContext, FlowPilotNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val sessions = mutableMapOf<MediaSession.Token, Session>()
    private val observations = MediaSessionObservations<MediaSession.Token>()
    private var started = false

    private inner class Session(val controller: MediaController) {
        var snapshot: MediaSnapshot? = null
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?): Unit = update(this@Session) {
                MediaSessionReducer.playbackUpdate(it, playbackValue(state))
            }
            override fun onMetadataChanged(metadata: MediaMetadata?): Unit = update(this@Session) {
                MediaSessionReducer.metadataUpdate(
                    it,
                    metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                    metadata?.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString().orEmpty(),
                    metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)?.toString().orEmpty(),
                )
            }
            override fun onSessionDestroyed(): Unit = remove(this@Session)
        }
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        synchronized(lock) { if (started) reconcile(controllers.orEmpty()) }
    }

    /** Returns false if notification-listener access is missing or was revoked. */
    fun start(): Boolean = synchronized(lock) {
        if (!CapabilityManager(appContext).hasNotificationListenerAccess()) {
            stopLocked()
            return false
        }
        if (started) return true
        try {
            started = true
            manager.addOnActiveSessionsChangedListener(sessionsChanged, listenerComponent, handler)
            reconcile(manager.getActiveSessions(listenerComponent), seeding = true)
            true
        } catch (_: SecurityException) {
            stopLocked()
            false
        }
    }

    fun stop() = synchronized(lock) { stopLocked() }

    private fun stopLocked() {
        if (!started) return
        started = false
        try { manager.removeOnActiveSessionsChangedListener(sessionsChanged) } catch (_: SecurityException) { }
        sessions.values.forEach {
            try { it.controller.unregisterCallback(it.callback) } catch (_: SecurityException) { }
        }
        sessions.clear()
        observations.clear()
    }

    private fun reconcile(controllers: List<MediaController>, seeding: Boolean = false) {
        val tokens = controllers.mapTo(mutableSetOf()) { it.sessionToken }
        sessions.keys.filter { it !in tokens }.forEach { token ->
            sessions.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
        }
        controllers.forEach { controller ->
            if (controller.sessionToken !in sessions) {
                val session = Session(controller)
                sessions[controller.sessionToken] = session
                controller.registerCallback(session.callback, handler)
                val initial = snapshot(controller)
                session.snapshot = initial
                observations.initialEvent(controller.sessionToken, controller.packageName, initial, seeding)?.let(onEvent)
            }
        }
    }

    private fun update(session: Session, change: (MediaSnapshot) -> MediaSnapshot): Unit = synchronized(lock) {
        if (!started || sessions[session.controller.sessionToken] !== session) return
        val previous = session.snapshot ?: return
        val current = change(previous)
        val events = MediaSessionReducer.reduce(session.controller.packageName, session.snapshot, current)
        session.snapshot = MediaSessionReducer.nextSnapshot(session.snapshot, current)
        events.forEach(onEvent)
    }

    private fun remove(session: Session): Unit = synchronized(lock) {
        if (sessions[session.controller.sessionToken] === session) {
            sessions.remove(session.controller.sessionToken)
            session.controller.unregisterCallback(session.callback)
        }
    }

    private fun snapshot(controller: MediaController): MediaSnapshot {
        val metadata = controller.metadata
        val playing = playbackValue(controller.playbackState)
        return MediaSnapshot(
            playing = playing,
            mediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            title = metadata?.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString().orEmpty(),
            artist = metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)?.toString().orEmpty(),
        )
    }

    private fun playbackValue(state: PlaybackState?): Boolean? = when (state?.state) {
        PlaybackState.STATE_PLAYING -> true
        PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED -> false
        else -> null
    }
}
