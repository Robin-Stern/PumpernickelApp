@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel.domain.progresspic

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionComplete
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.posix.memcpy

/**
 * iOS-side actual for [PhotoVault]. Files live at <Documents>/progress_pics/{id}.jpg
 * (D-17-05). Each saved file is written with NSDataWritingFileProtectionComplete
 * so the bytes are unreadable while the device is locked (T-PHOTO-EXFIL, D-17-06).
 * After write, the file URL is marked NSURLIsExcludedFromBackupKey = true so the
 * file is excluded from iCloud + iTunes backups (T-CLOUD-LEAK, D-17-06).
 */
actual class PhotoVault {

    private val documentsDir: NSURL by lazy {
        requireNotNull(
            NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null
            )
        ) { "Failed to resolve Documents directory" }
    }

    private val rootDir: NSURL by lazy {
        val dir = documentsDir.URLByAppendingPathComponent("progress_pics", isDirectory = true)
            ?: error("Failed to build progress_pics URL")
        // Ensure dir exists; create=true above only creates the parent.
        NSFileManager.defaultManager.createDirectoryAtURL(
            url = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        dir
    }

    actual suspend fun write(id: String, bytes: ByteArray): String {
        val fileUrl = rootDir.URLByAppendingPathComponent("$id.jpg", isDirectory = false)
            ?: error("Failed to build file URL for id=$id")

        // Convert ByteArray -> NSData via NSData.create(bytes:, length:).
        val data: NSData = bytes.toNSData()

        // Write with NSDataWritingFileProtectionComplete (D-17-06 / T-PHOTO-EXFIL).
        // Atomic write avoids torn files on crash.
        val writeOptions = NSDataWritingAtomic or NSDataWritingFileProtectionComplete
        memScoped {
            val errorVar = alloc<ObjCObjectVar<NSError?>>()
            val ok = data.writeToURL(
                url = fileUrl,
                options = writeOptions,
                error = errorVar.ptr
            )
            if (!ok) {
                error("Failed to write photo $id: ${errorVar.value?.localizedDescription}")
            }
        }

        // Mark file as excluded from iCloud backup (D-17-06 / T-CLOUD-LEAK).
        // Best-effort: a non-null error is acceptable — the protection-complete
        // flag is the load-bearing mitigation; backup-exclusion is defence in depth.
        memScoped {
            val errorVar = alloc<ObjCObjectVar<NSError?>>()
            fileUrl.setResourceValue(
                value = true as Any?,
                forKey = NSURLIsExcludedFromBackupKey,
                error = errorVar.ptr
            )
        }

        return "progress_pics/$id.jpg"
    }

    actual suspend fun read(relativePath: String): ByteArray? {
        val fileUrl = documentsDir.URLByAppendingPathComponent(relativePath, isDirectory = false)
            ?: return null
        val data = NSData.dataWithContentsOfURL(fileUrl) ?: return null
        return data.toByteArray()
    }

    actual suspend fun delete(relativePath: String) {
        val fileUrl = documentsDir.URLByAppendingPathComponent(relativePath, isDirectory = false)
            ?: return
        NSFileManager.defaultManager.removeItemAtURL(fileUrl, error = null)
    }

    actual suspend fun deleteAll(relativePaths: List<String>) {
        relativePaths.forEach { delete(it) }
    }
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, length)
    }
    return out
}
