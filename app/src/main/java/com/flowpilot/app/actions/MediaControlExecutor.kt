package com.flowpilot.app.actions

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.engine.FlowPilotNotificationListener
import com.flowpilot.app.permission.CapabilityManager

/** A session's command and state snapshot, without media metadata. */
internal interface MediaControlSession {
    val packageName: String
    val state: Int?
    val actions: Long
    fun play()
    fun pause()
    fun next()
    fun previous()
}

private class AndroidMediaControlSession(private val controller: MediaController) : MediaControlSession {
    override val packageName get() = controller.packageName
    override val state get() = controller.playbackState?.state
    override val actions get() = controller.playbackState?.actions ?: 0L
    override fun play() = controller.transportControls.play()
    override fun pause() = controller.transportControls.pause()
    override fun next() = controller.transportControls.skipToNext()
    override fun previous() = controller.transportControls.skipToPrevious()
}

/** Sends commands to the highest-priority active session, or to the requested package. */
class MediaControlExecutor internal constructor(
    private val hasAccess: () -> Boolean,
    private val sessions: () -> List<MediaControlSession>?,
) : ActionExecutor {
    constructor(context: Context) : this(
        hasAccess = { CapabilityManager(context).hasNotificationListenerAccess() },
        sessions = {
            context.getSystemService(MediaSessionManager::class.java)
                ?.getActiveSessions(ComponentName(context, FlowPilotNotificationListener::class.java))
                ?.map(::AndroidMediaControlSession)
        },
    )

    override val supportedTypes = setOf(
        ActionType.MEDIA_PLAY, ActionType.MEDIA_PAUSE, ActionType.MEDIA_TOGGLE,
        ActionType.MEDIA_NEXT, ActionType.MEDIA_PREVIOUS,
    )

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (action !in supportedTypes) return ActionResult(false, "Unsupported media action")
        return try {
            if (!hasAccess()) return ActionResult(false, "Notification listener access required", ActionResultCode.PERMISSION_REQUIRED, diagnostic = ActionDiagnostic(ActionDiagnosticReason.NOTIFICATION_PERMISSION_REQUIRED))
            val active = sessions() ?: return ActionResult(false, "Media session service unavailable", diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_UNAVAILABLE))
            val target = parameters.mediaTargetPackage.trim()
            val session = (if (target.isEmpty()) active.firstOrNull() else active.firstOrNull { it.packageName == target })
                ?: return ActionResult(false, "No matching active media session", diagnostic = ActionDiagnostic(ActionDiagnosticReason.MEDIA_SESSION_UNAVAILABLE))
            val state = session.state
            val command = when (action) {
                ActionType.MEDIA_PLAY -> {
                    if (state == PlaybackState.STATE_PLAYING) return ActionResult(false, "Media is already playing")
                    ActionType.MEDIA_PLAY
                }
                ActionType.MEDIA_PAUSE -> {
                    if (state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_STOPPED) {
                        return ActionResult(false, "Media is already paused or stopped")
                    }
                    ActionType.MEDIA_PAUSE
                }
                ActionType.MEDIA_TOGGLE -> when (state) {
                    PlaybackState.STATE_PLAYING -> ActionType.MEDIA_PAUSE
                    PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED -> ActionType.MEDIA_PLAY
                    else -> return ActionResult(false, "Media playback state is unavailable for toggle", diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_UNAVAILABLE))
                }
                else -> action
            }
            val required = when (command) {
                ActionType.MEDIA_PLAY -> PlaybackState.ACTION_PLAY
                ActionType.MEDIA_PAUSE -> PlaybackState.ACTION_PAUSE
                ActionType.MEDIA_NEXT -> PlaybackState.ACTION_SKIP_TO_NEXT
                ActionType.MEDIA_PREVIOUS -> PlaybackState.ACTION_SKIP_TO_PREVIOUS
                else -> return ActionResult(false, "Unsupported media action")
            }
            if (session.actions and required == 0L) return ActionResult(false, "Media session does not support this action")
            when (command) {
                ActionType.MEDIA_PLAY -> session.play()
                ActionType.MEDIA_PAUSE -> session.pause()
                ActionType.MEDIA_NEXT -> session.next()
                ActionType.MEDIA_PREVIOUS -> session.previous()
                else -> return ActionResult(false, "Unsupported media action")
            }
            // Transport controls are asynchronous and provide no acknowledgement of playback.
            ActionResult(true, "Media command sent; playback change not verified")
        } catch (_: SecurityException) {
            ActionResult(false, "Notification listener access required", ActionResultCode.PERMISSION_REQUIRED, diagnostic = ActionDiagnostic(ActionDiagnosticReason.NOTIFICATION_PERMISSION_REQUIRED))
        } catch (_: Exception) {
            ActionResult(false, "Media command failed")
        }
    }
}
