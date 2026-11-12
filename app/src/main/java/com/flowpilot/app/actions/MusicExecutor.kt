package com.flowpilot.app.actions

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.flowpilot.app.data.model.ActionType
import java.net.URI

/** Strictly matches supported public track/video links without following redirects. */
object MusicLinkValidator {
    const val SPOTIFY = "com.spotify.music"
    const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

    fun isSupportedPackage(packageName: String): Boolean = packageName == SPOTIFY || packageName == YOUTUBE_MUSIC

    /** Templates are checked for syntax here; the rendered URL is validated before launch. */
    fun isValidTemplate(packageName: String, text: String): Boolean {
        if (!isSupportedPackage(packageName)) return false
        if (isValidLink(packageName, text)) return true
        val result = TemplateRenderer.renderResult(text, TemplateContext(), RenderMode.STRICT)
        return result.parseErrors.isEmpty() && result.expressionCount > 0
    }

    fun isValidLink(packageName: String, link: String): Boolean {
        val normalizedLink = if (packageName == YOUTUBE_MUSIC) link.trim() else link
        val uri = try { URI(normalizedLink) } catch (_: Exception) { return false }
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port != -1 || uri.rawFragment != null) return false
        val host = uri.host ?: return false
        val path = uri.rawPath ?: return false
        return when (packageName) {
            SPOTIFY -> host.equals("open.spotify.com", ignoreCase = true) &&
                Regex("/track/[A-Za-z0-9]+/?").matches(path)
            YOUTUBE_MUSIC -> when {
                host.equals("youtu.be", ignoreCase = true) -> Regex("/[A-Za-z0-9_-]{11}/?").matches(path)
                host.equals("music.youtube.com", ignoreCase = true) && path == "/watch" -> {
                    val videoIds = uri.rawQuery.orEmpty().split('&').filter { it.startsWith("v=") }
                    videoIds.size == 1 && Regex("v=[A-Za-z0-9_-]{11}").matches(videoIds.single())
                }
                else -> false
            }
            else -> false
        }
    }

    /** Describes rejected links without retaining the link, host, query, or identifier. */
    fun diagnoseInvalidLink(packageName: String, link: String): ActionDiagnostic? {
        if (isValidLink(packageName, link)) return null
        fun reason(value: ActionDiagnosticReason) = ActionDiagnostic(value)
        val normalized = if (packageName == YOUTUBE_MUSIC) link.trim() else link
        val uri = try { URI(normalized) } catch (_: Exception) {
            return reason(ActionDiagnosticReason.MALFORMED_LINK)
        }
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port != -1 || uri.rawFragment != null) {
            return reason(ActionDiagnosticReason.INVALID_LINK_COMPONENTS)
        }
        val host = uri.host ?: return reason(ActionDiagnosticReason.UNSUPPORTED_LINK_HOST)
        val path = uri.rawPath ?: return reason(ActionDiagnosticReason.INVALID_LINK_PATH)
        if (packageName == SPOTIFY) {
            return reason(if (host.equals("open.spotify.com", ignoreCase = true)) ActionDiagnosticReason.INVALID_LINK_PATH else ActionDiagnosticReason.UNSUPPORTED_LINK_HOST)
        }
        if (packageName != YOUTUBE_MUSIC) return reason(ActionDiagnosticReason.UNSUPPORTED_LINK_HOST)
        val id = when {
            host.equals("youtu.be", ignoreCase = true) -> {
                if (!path.startsWith('/') || path.drop(1).trimEnd('/').contains('/')) {
                    return reason(ActionDiagnosticReason.INVALID_LINK_PATH)
                }
                path.drop(1).removeSuffix("/")
            }
            host.equals("music.youtube.com", ignoreCase = true) -> {
                if (path != "/watch") return reason(ActionDiagnosticReason.INVALID_LINK_PATH)
                val ids = uri.rawQuery.orEmpty().split('&').filter { it.startsWith("v=") }
                if (ids.isEmpty()) return reason(ActionDiagnosticReason.MISSING_VIDEO_ID)
                if (ids.size > 1) return reason(ActionDiagnosticReason.MULTIPLE_VIDEO_IDS)
                ids.single().substring(2)
            }
            else -> return reason(ActionDiagnosticReason.UNSUPPORTED_LINK_HOST)
        }
        if (id.length != 11) return ActionDiagnostic(
            ActionDiagnosticReason.INVALID_VIDEO_ID_LENGTH,
            expectedLength = 11,
            observedLength = id.length.coerceAtMost(1000),
        )
        return reason(ActionDiagnosticReason.INVALID_VIDEO_ID_CHARACTERS)
    }
}

/** Opens search results or a validated track link; playback is not verified. */
class MusicExecutor internal constructor(private val launch: (Intent) -> Unit) : ActionExecutor {
    constructor(context: Context) : this({ context.startActivity(it) })

    override val supportedTypes = setOf(ActionType.MUSIC_SEARCH, ActionType.MUSIC_PLAY_LINK)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (action !in supportedTypes || !MusicLinkValidator.isSupportedPackage(parameters.musicAppPackage)) {
            return ActionResult(false, "Invalid music action or app")
        }
        val intent = when (action) {
            ActionType.MUSIC_SEARCH -> {
                if (parameters.musicQuery.isBlank()) return ActionResult(false, "Missing music query")
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                    .putExtra(SearchManager.QUERY, parameters.musicQuery)
            }
            ActionType.MUSIC_PLAY_LINK -> {
                val link = if (parameters.musicAppPackage == MusicLinkValidator.YOUTUBE_MUSIC) parameters.musicLink.trim() else parameters.musicLink
                if (!MusicLinkValidator.isValidLink(parameters.musicAppPackage, link)) {
                    return ActionResult(false, "Invalid music link", diagnostic = MusicLinkValidator.diagnoseInvalidLink(parameters.musicAppPackage, link))
                }
                Intent(Intent.ACTION_VIEW, Uri.parse(link))
            }
            else -> return ActionResult(false, "Invalid music action")
        }.setPackage(parameters.musicAppPackage).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            launch(intent)
            ActionResult(true, if (action == ActionType.MUSIC_SEARCH) "Music search opened; playback not verified" else "Music link opened; playback not verified")
        } catch (_: ActivityNotFoundException) {
            ActionResult(false, "Music app unavailable or unable to handle this action; install or enable it and try again")
        } catch (_: Exception) {
            ActionResult(false, "Unable to open music app")
        }
    }
}
