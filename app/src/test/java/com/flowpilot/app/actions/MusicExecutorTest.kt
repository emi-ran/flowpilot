package com.flowpilot.app.actions

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.MediaStore
import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicExecutorTest {
    private val spotify = MusicLinkValidator.SPOTIFY
    private val youtube = MusicLinkValidator.YOUTUBE_MUSIC

    @Test fun linksAreBoundToTheirServiceAndTrackShape() {
        val valid = listOf(
            spotify to "https://open.spotify.com/track/abc123?si=xyz",
            youtube to "https://music.youtube.com/watch?v=abcdefghijk&si=xyz",
            youtube to "https://youtu.be/abcdefghijk?si=xyz",
        )
        valid.forEach { (pkg, link) -> assertThat(MusicLinkValidator.isValidLink(pkg, link)).isTrue() }
        listOf(
            spotify to "https://music.youtube.com/watch?v=abcdefghijk",
            youtube to "https://open.spotify.com/track/abc123",
            spotify to "https://open.spotify.com.evil.test/track/abc123",
            spotify to "https://open.spotify.com@evil.test/track/abc123",
            spotify to "http://open.spotify.com/track/abc123",
            spotify to "https://open.spotify.com/album/abc123",
            youtube to "https://music.youtube.com/watch?v=abcdefghijk&v=lmnopqrstuv",
            youtube to "https://youtu.be/short",
        ).forEach { (pkg, link) -> assertThat(MusicLinkValidator.isValidLink(pkg, link)).isFalse() }
    }

    @Test fun youtubeLinksTrimOuterWhitespaceBeforeValidationAndLaunch() {
        val link = "https://music.youtube.com/watch?v=dQw4w9WgXcQ"
        var launched: Intent? = null
        val executor = MusicExecutor { launched = it }
        val rule = Automation(id = "id", name = "music", createdAt = 1,
            action = ActionType.MUSIC_PLAY_LINK, musicAppPackage = youtube,
            musicLink = " \${notification.text|stripPrefix:\"playmusic\"|trim} ")
        val parameters = ActionParametersFactory.create(rule, TemplateContext(values = mapOf(
            "notification.text" to "playmusic $link")))
        assertThat(MusicLinkValidator.isValidTemplate(youtube, " $link ")).isTrue()
        assertThat(executor.execute(ActionType.MUSIC_PLAY_LINK, parameters).success).isTrue()
        assertThat(launched!!.data.toString()).isEqualTo(link)
        assertThat(MusicLinkValidator.isValidLink(youtube, "https://music.youtube.com/watch?v=abc defghijk")).isFalse()
        assertThat(MusicLinkValidator.isValidLink(youtube, " https://music.youtube.com.evil.test/watch?v=abcdefghijk ")).isFalse()
    }

    @Test fun templatesUseRendererSyntaxAndRequireAnExpression() {
        listOf(
            "https://open.spotify.com/track/abc123",
            "\${notification.text|trim}",
            "https://open.spotify.com/track/\${notification.text|default:\"abc\"}",
            "\${unknown.variable}",
        ).forEach { assertThat(MusicLinkValidator.isValidTemplate(spotify, it)).isTrue() }
        listOf(
            "https://open.spotify.com/album/abc",
            "\$\${notification.text}",
            "\${notification.text",
            "\${notification.text|notAFilter}",
            "\${notification.text} \${broken",
        ).forEach { assertThat(MusicLinkValidator.isValidTemplate(spotify, it)).isFalse() }
        assertThat(MusicLinkValidator.isValidTemplate("unknown", "\${notification.text}")).isFalse()
    }

    @Test fun renderedLinksAreValidatedBeforeLaunching() {
        var launches = 0
        val executor = MusicExecutor { launches++ }
        val rule = Automation(id = "id", name = "music", createdAt = 1,
            action = ActionType.MUSIC_PLAY_LINK, musicAppPackage = spotify,
            musicLink = "\${notification.text|trim}")
        fun run(value: String): ActionResult = executor.execute(
            ActionType.MUSIC_PLAY_LINK,
            ActionParametersFactory.create(rule, TemplateContext(values = mapOf("notification.text" to value))),
        )
        assertThat(run(" https://open.spotify.com/track/abc123 ").success).isTrue()
        assertThat(run("https://open.spotify.com.evil.test/track/abc123").success).isFalse()
        assertThat(run("Listen https://open.spotify.com/track/abc123").success).isFalse()
        assertThat(run("\${unknown.variable}").success).isFalse()
        val unresolved = rule.copy(musicLink = "\${unknown.variable}")
        assertThat(executor.execute(ActionType.MUSIC_PLAY_LINK, ActionParametersFactory.create(unresolved)).success).isFalse()
        assertThat(launches).isEqualTo(1)
    }

    @Test fun dispatchesExplicitIntentsAndDoesNotClaimPlayback() {
        var intent: Intent? = null
        val executor = MusicExecutor { intent = it }
        val search = executor.execute(ActionType.MUSIC_SEARCH, ActionParameters(musicAppPackage = spotify, musicQuery = "a song"))
        assertThat(search.success).isTrue()
        assertThat(search.message).contains("not verified")
        assertThat(intent!!.action).isEqualTo(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
        assertThat(intent!!.getStringExtra(SearchManager.QUERY)).isEqualTo("a song")
        assertThat(intent!!.getPackage()).isEqualTo(spotify)
        assertThat(intent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        val link = "https://youtu.be/abcdefghijk?si=test"
        val result = executor.execute(ActionType.MUSIC_PLAY_LINK, ActionParameters(musicAppPackage = youtube, musicLink = link))
        assertThat(result.success).isTrue()
        assertThat(result.message).contains("not verified")
        assertThat(intent!!.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent!!.data.toString()).isEqualTo(link)
        assertThat(intent!!.getPackage()).isEqualTo(youtube)
    }

    @Test fun rejectsInvalidInputAndRedactsFailures() {
        var launches = 0
        val executor = MusicExecutor { launches++; throw IllegalStateException("secret query") }
        assertThat(executor.execute(ActionType.MUSIC_SEARCH, ActionParameters(musicAppPackage = spotify, musicQuery = " ")).success).isFalse()
        assertThat(executor.execute(ActionType.MUSIC_PLAY_LINK, ActionParameters(musicAppPackage = youtube, musicLink = "https://open.spotify.com/track/id")).success).isFalse()
        assertThat(launches).isEqualTo(0)
        val failed = executor.execute(ActionType.MUSIC_SEARCH, ActionParameters(musicAppPackage = spotify, musicQuery = "secret query"))
        assertThat(failed.message).isEqualTo("Unable to open music app")
    }

    @Test fun missingMusicAppReportsActionableFailure() {
        val executor = MusicExecutor { throw ActivityNotFoundException("sensitive") }
        val result = executor.execute(ActionType.MUSIC_SEARCH, ActionParameters(musicAppPackage = spotify, musicQuery = "song"))
        assertThat(result.success).isFalse()
        assertThat(result.message).contains("install or enable")
        assertThat(result.message).doesNotContain("sensitive")
    }

    @Test fun invalidLinksProduceSafeStructuredDiagnostics() {
        var launches = 0
        val executor = MusicExecutor { launches++ }
        fun diagnostic(link: String) = executor.execute(
            ActionType.MUSIC_PLAY_LINK, ActionParameters(musicAppPackage = youtube, musicLink = link),
        ).diagnostic
        val shortId = diagnostic("https://youtu.be/secret1234?token=private")!!
        assertThat(shortId.reason).isEqualTo(ActionDiagnosticReason.INVALID_VIDEO_ID_LENGTH)
        assertThat(shortId.expectedLength).isEqualTo(11)
        assertThat(shortId.observedLength).isEqualTo(10)
        assertThat(diagnostic("https://music.youtube.com/watch?x=private")!!.reason).isEqualTo(ActionDiagnosticReason.MISSING_VIDEO_ID)
        assertThat(diagnostic("https://music.youtube.com/watch?v=abcdefghijk&v=lmnopqrstuv")!!.reason).isEqualTo(ActionDiagnosticReason.MULTIPLE_VIDEO_IDS)
        assertThat(diagnostic("https://youtu.be/abcdefghij%")!!.reason).isEqualTo(ActionDiagnosticReason.MALFORMED_LINK)
        assertThat(diagnostic("https://youtu.be/abcdefghij%21")!!.reason).isEqualTo(ActionDiagnosticReason.INVALID_VIDEO_ID_LENGTH)
        assertThat(diagnostic("https://evil.test/abcdefghijk")!!.reason).isEqualTo(ActionDiagnosticReason.UNSUPPORTED_LINK_HOST)
        assertThat(diagnostic("https://youtu.be/" + "a".repeat(1100))!!.observedLength).isEqualTo(1000)
        assertThat(launches).isEqualTo(0)
    }

    @Test fun diagnosticsRoundTripAndLegacyHistoryRemainsReadable() {
        val result = MusicExecutor { }.execute(
            ActionType.MUSIC_PLAY_LINK,
            ActionParameters(musicAppPackage = youtube, musicLink = "https://youtu.be/secret1234?token=private"),
        )
        val record = ActionExecutionRecord.create(ActionType.MUSIC_PLAY_LINK, result)
        val encoded = Json.encodeToString(record)
        assertThat(encoded).doesNotContain("secret1234")
        assertThat(encoded).doesNotContain("private")
        assertThat(encoded).doesNotContain("https")
        assertThat(Json.decodeFromString<ActionExecutionRecord>(encoded).diagnostic).isEqualTo(result.diagnostic)
        val legacy = """{"actionType":"MUSIC_PLAY_LINK","actionLabel":"Music","success":false,"message":"Invalid music link"}"""
        assertThat(Json.decodeFromString<ActionExecutionRecord>(legacy).diagnostic).isNull()
        assertThat(ActionExecutionRecord.create(ActionType.MUSIC_SEARCH, ActionResult(true, "ok")).diagnostic).isNull()
    }

    @Test fun fieldsSurviveSerializationAndParameterMapping() {
        val rule = Automation(id = "id", name = "music", createdAt = 1, action = ActionType.MUSIC_SEARCH,
            musicAppPackage = spotify, musicQuery = "song", musicLink = "https://open.spotify.com/track/id")
        val decoded = Json.decodeFromString<Automation>(Json.encodeToString(rule))
        val params = ActionParametersFactory.create(decoded)
        assertThat(params.musicAppPackage).isEqualTo(spotify)
        assertThat(params.musicQuery).isEqualTo("song")
        assertThat(params.musicLink).isEqualTo(rule.musicLink)
        assertThat(decoded.action).isEqualTo(ActionType.MUSIC_SEARCH)
    }
}
