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

    actual suspend fun write(id: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val file = File(rootDir, "$id.jpg")
            file.writeBytes(bytes)
            "progress_pics/$id.jpg"
        }

    actual suspend fun read(relativePath: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, relativePath)
            if (file.exists()) file.readBytes() else null
        }

    actual suspend fun delete(relativePath: String) {
        withContext(Dispatchers.IO) {
            File(context.filesDir, relativePath).delete()
        }
    }

    actual suspend fun deleteAll(relativePaths: List<String>) {
        withContext(Dispatchers.IO) {
            relativePaths.forEach { File(context.filesDir, it).delete() }
        }
    }
}
