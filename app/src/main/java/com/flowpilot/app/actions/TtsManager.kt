package com.flowpilot.app.actions

import android.content.Context
import com.flowpilot.app.data.model.TtsVoiceOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Offline-first TTS synthesis manager and audio cache resolver. */
class TtsManager(
    context: Context,
    private val engine: TtsEngine = AndroidTtsEngine(context),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val ttsDir: File by lazy {
        File(context.filesDir, "tts_cache").apply { if (!exists()) mkdirs() }
    }

    /** Synthesis is serialized: the platform TTS service is expensive and not reentrant. */
    private val synthesisMutex = Mutex()

    /** Cleanup is cheap enough to run periodically, but not after every synthesis. */
    private var successfulSyntheses = 0

    suspend fun getAvailableOfflineVoices(): List<TtsVoiceOption> = engine.availableVoices()

    /**
     * Returns a cached file for [text], synthesizing it when missing.
     *
     * @return the cached file, or null when the text is invalid or synthesis failed.
     */
    suspend fun ensureSpeech(text: String, voiceName: String, rate: Float): File? {
        if (text.isBlank() || text.length > MAX_TTS_TEXT_LENGTH) return null

        return withContext(Dispatchers.IO) {
            val fileName = contentCacheFileName(text, voiceName, rate)
            val cached = getCacheFile(fileName)
            if (cached != null && cached.isFile && cached.length() > 0L) {
                touch(cached)
                return@withContext cached
            }

            synthesisMutex.withLock {
                // Another coroutine may have produced the file while this one waited for the lock.
                val target = getCacheFile(fileName) ?: return@withLock null
                if (target.isFile && target.length() > 0L) {
                    touch(target)
                    return@withLock target
                }

                val temp = File(ttsDir, "${target.name}.${java.util.UUID.randomUUID()}$TEMP_SUFFIX")
                try {
                    if (temp.exists()) temp.delete()
                    val synthesized = engine.synthesize(text, voiceName, rate, temp)
                    if (!synthesized || !temp.isFile || temp.length() <= 0L) {
                        temp.delete()
                        return@withLock null
                    }
                    // The target is only replaced after a complete temporary file exists.
                    if (!replaceFile(temp, target)) {
                        temp.delete()
                        return@withLock null
                    }
                    touch(target)
                    successfulSyntheses++
                    if (successfulSyntheses % CLEANUP_INTERVAL == 0) cleanup()
                    target.takeIf { it.isFile && it.length() > 0L }
                } catch (ce: CancellationException) {
                    temp.delete()
                    throw ce
                } catch (_: Throwable) {
                    temp.delete()
                    null
                }
            }
        }
    }

    /** Content-addressed cache key for runtime speech; independent of rule identity. */
    fun contentCacheFileName(text: String, voiceName: String, rate: Float): String {
        val payload = buildString {
            append(normalizeText(text))
            append('\u0000')
            append(voiceName)
            append('\u0000')
            append(rate)
            append('\u0000')
            append(engine.enginePackageName)
        }
        return "tts_${sha256Hex(payload).take(CONTENT_HASH_LENGTH)}$WAV_SUFFIX"
    }

    /**
     * Legacy rule-owned cache name. Kept byte-for-byte compatible with persisted rule references
     * and backup/import validation.
     */
    fun computeCacheFileName(ruleId: String, text: String, voiceName: String, rate: Float): String {
        val input = "$ruleId|$text|$voiceName|$rate"
        val hash = sha256Hex(input).take(16)
        val safeRule = ruleId.filter { it.isLetterOrDigit() || it == '-' }.take(20).ifBlank { "preview" }
        return "tts_${safeRule}_${hash}$WAV_SUFFIX"
    }

    fun isValidCacheFileName(fileName: String): Boolean =
        legacyCacheFileNameRegex.matches(fileName) || contentCacheFileNameRegex.matches(fileName)

    fun getCacheFile(fileName: String): File? {
        if (!isValidCacheFileName(fileName)) return null
        val file = File(ttsDir, fileName)
        // Ensure path remains strictly within ttsDir and cannot traverse
        val canonicalDir = ttsDir.canonicalFile
        val canonicalTarget = file.canonicalFile
        if (canonicalTarget.parentFile != canonicalDir) return null
        return file
    }

    /** Marks [file] as recently used so both TTL and LRU cleanup leave it alone. */
    fun touch(file: File) {
        try {
            file.setLastModified(clock())
        } catch (_: Throwable) {
        }
    }

    /**
     * Prunes the cache: deletes files older than [RETENTION_MS], then evicts least-recently-used
     * files until the cache fits in [MAX_CACHE_BYTES]. Active, temporary, and recently touched
     * files are never removed. Never throws.
     */
    fun cleanup(activeFileNames: Set<String> = emptySet()) {
        try {
            val now = clock()
            val files = ttsDir.listFiles()?.filter { it.isFile } ?: return
            val candidates = files.filter { file ->
                !isProtected(file.name, activeFileNames) &&
                    now - file.lastModified() >= RECENT_WINDOW_MS
            }

            for (file in candidates) {
                if (now - file.lastModified() > RETENTION_MS) {
                    file.delete()
                }
            }

            val surviving = ttsDir.listFiles()?.filter { it.isFile } ?: return
            var totalBytes = surviving.sumOf { it.length() }
            if (totalBytes <= MAX_CACHE_BYTES) return

            val evictable = surviving
                .filter { file ->
                    !isProtected(file.name, activeFileNames) &&
                        now - file.lastModified() >= RECENT_WINDOW_MS
                }
                .sortedBy { it.lastModified() }

            for (file in evictable) {
                if (totalBytes <= MAX_CACHE_BYTES) break
                val size = file.length()
                if (file.delete()) totalBytes -= size
            }
        } catch (_: Throwable) {
        }
    }

    /** Deletes every cache file after an explicit user request; playback is not protected. */
    fun clearCache(): Int {
        var deleted = 0
        try {
            ttsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.delete()) deleted++
            }
        } catch (_: Throwable) {
        }
        return deleted
    }

    private fun isProtected(fileName: String, activeFileNames: Set<String>): Boolean =
        fileName in activeFileNames || fileName.endsWith(TEMP_SUFFIX)

    /** Moves [source] onto [target] without deleting a valid target before the move succeeds. */
    private fun replaceFile(source: File, target: File): Boolean {
        val sourcePath = source.toPath()
        val targetPath = target.toPath()
        return try {
            try {
                Files.move(
                    sourcePath,
                    targetPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (_: Throwable) {
            // Some filesystems report atomic moves as unsupported with other exception types.
            try {
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun normalizeText(text: String): String = text.trim().replace(WHITESPACE_REGEX, " ")

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (byte in digest) append("%02x".format(byte))
        }
    }

    private val legacyCacheFileNameRegex = Regex("^tts_[A-Za-z0-9-]+_[0-9a-f]{16}\\.wav$")
    private val contentCacheFileNameRegex = Regex("^tts_[0-9a-f]{32}\\.wav$")

    private companion object {
        const val WAV_SUFFIX = ".wav"
        const val TEMP_SUFFIX = ".tmp"
        const val CONTENT_HASH_LENGTH = 32
        const val MAX_TTS_TEXT_LENGTH = 2_000
        const val RECENT_WINDOW_MS = 10 * 60 * 1_000L
        const val RETENTION_MS = 7 * 24 * 60 * 60 * 1_000L
        const val MAX_CACHE_BYTES = 50L * 1024 * 1024
        const val CLEANUP_INTERVAL = 10
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
