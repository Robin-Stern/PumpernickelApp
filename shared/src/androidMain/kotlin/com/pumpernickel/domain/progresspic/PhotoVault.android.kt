package com.pumpernickel.domain.progresspic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android-side actual for [PhotoVault]. Files live in app-private storage at
 * <filesDir>/progress_pics/{id}.jpg (D-17-05). The DB row stores the relative
 * path "progress_pics/{id}.jpg"; this class resolves it against filesDir.
 *
 * Backup exclusion is handled at the manifest layer
 * (data_extraction_rules.xml + backup_rules.xml — Task 1) — no per-file flag
 * is required on Android (T-CLOUD-LEAK mitigation).
 *
 * Constructor takes [Context] so PlatformModule.android.kt (plan 17-08) can
 * bind it via `single<PhotoVault> { PhotoVault(androidContext()) }`.
 */
actual class PhotoVault(private val context: Context) {

    private val rootDir: File by lazy {
        File(context.filesDir, "progress_pics").apply { mkdirs() }
    }

    /**
     * Resolve a relative path inside the vault, returning null if the path
     * escapes the vault root (path-traversal defence — REVIEW B-01). The
     * Phase 17 writer only ever produces "progress_pics/{uuid}.jpg" so any
     * relative path that doesn't start with "progress_pics/" or that
     * canonicalises outside [rootDir] is rejected — defence in depth against
     * a stale / tampered DB row.
     */
    private fun resolveSafe(relativePath: String): File? {
        if (!relativePath.startsWith("progress_pics/")) return null
        val candidate = File(context.filesDir, relativePath)
        val rootCanonical = rootDir.canonicalPath
        val candidateCanonical = try {
            candidate.canonicalPath
        } catch (_: Throwable) {
            return null
        }
        // Must be strictly inside the vault root, not the root itself, and not
        // a sibling whose name happens to share the prefix.
        if (candidateCanonical == rootCanonical) return null
        if (!candidateCanonical.startsWith(rootCanonical + File.separator)) return null
        return candidate
    }

    actual suspend fun write(id: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val file = File(rootDir, "$id.jpg")
            file.writeBytes(bytes)
            "progress_pics/$id.jpg"
        }

    actual suspend fun read(relativePath: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = resolveSafe(relativePath) ?: return@withContext null
            if (file.exists()) file.readBytes() else null
        }

    actual suspend fun delete(relativePath: String) {
        withContext(Dispatchers.IO) {
            val file = resolveSafe(relativePath) ?: return@withContext
            file.delete()
        }
    }

    actual suspend fun deleteAll(relativePaths: List<String>) {
        withContext(Dispatchers.IO) {
            relativePaths.forEach { rp ->
                resolveSafe(rp)?.delete()
            }
        }
    }
}
