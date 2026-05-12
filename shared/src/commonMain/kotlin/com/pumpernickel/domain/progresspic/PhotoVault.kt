package com.pumpernickel.domain.progresspic

/**
 * Platform-private photo storage. The `actual` class lives under each
 * platform's filesDir / Documents directory and stores files at the relative
 * path `progress_pics/{id}.jpg` (D-17-05). On iOS the `actual` adds
 * NSFileProtectionComplete + isExcludedFromBackupKey on every saved file
 * (D-17-06 / T-PHOTO-EXFIL / T-CLOUD-LEAK).
 *
 * `id` is the UUID stem; `relativePath` is the full "progress_pics/{id}.jpg"
 * string the DB row stores.
 */
expect class PhotoVault {
    /** Writes the JPEG bytes; returns the relative path stored in the DB row. */
    suspend fun write(id: String, bytes: ByteArray): String

    /** Reads the bytes for an existing relative path; null if missing. */
    suspend fun read(relativePath: String): ByteArray?

    /** Deletes a single file (best-effort; missing file is not an error). */
    suspend fun delete(relativePath: String)

    /** Bulk delete for cascade cleanup (T-DELETE-ORPHAN). */
    suspend fun deleteAll(relativePaths: List<String>)
}
