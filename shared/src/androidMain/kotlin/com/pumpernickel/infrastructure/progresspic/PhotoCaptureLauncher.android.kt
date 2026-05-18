package com.pumpernickel.infrastructure.progresspic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Android-side actual for [PhotoCaptureLauncher]. Bridges the system camera
 * (TakePicture) and photo picker (PickVisualMedia) into a suspend API.
 *
 * Returns JPEG bytes already resized so the long edge is 1600px and re-encoded
 * at quality 0.8 (D-17-07). Returns null when the user cancels OR when no
 * host activity is attached.
 */
actual class PhotoCaptureLauncher(private val context: Context) {

    actual suspend fun captureFromCamera(): ByteArray? = withContext(Dispatchers.IO) {
        val host = PhotoCaptureLauncherActivityHolder.current ?: return@withContext null
        // REVIEW B-03 — read bytes directly from the cache File we created and
        // passed to the camera, NOT from a URI the camera echoed back. The
        // TakePicture contract carries no URI in its result, but a malicious
        // result-URI helper would have been the path-traversal vector.
        val file = host.launchCamera() ?: return@withContext null
        if (!file.exists()) return@withContext null
        val bytes = file.readBytes()
        resizeAndEncode(bytes)
    }

    actual suspend fun pickFromLibrary(): ByteArray? = withContext(Dispatchers.IO) {
        val host = PhotoCaptureLauncherActivityHolder.current ?: return@withContext null
        val uri = host.launchLibrary() ?: return@withContext null
        // Library URIs are content:// — go through ContentResolver. We never
        // call Uri.toFile() (it was a path-traversal foot-gun on file:// URIs).
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext null
        resizeAndEncode(bytes)
    }

    /**
     * Resizes the source bytes so the long edge is 1600px and re-encodes as
     * JPEG quality 0.8 (D-17-07). Falls back to source bytes if decode fails.
     *
     * REVIEW M-02: two-pass decode. First pass reads the source dimensions
     * cheaply via inJustDecodeBounds; we compute an inSampleSize so the second
     * decode produces a bitmap close to the target size, instead of allocating
     * a full ~50 MB ARGB_8888 bitmap from a modern phone camera and then
     * scaling down. After scaling we recycle the decoded original so it
     * doesn't sit in the heap waiting for a Full GC between captures.
     */
    private fun resizeAndEncode(source: ByteArray): ByteArray {
        // Pass 1: bounds only — no pixels allocated.
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, boundsOpts)
        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return source

        // Compute power-of-two sample size so the decoded bitmap's long edge
        // is no smaller than the target (we still apply a precise scale below).
        var sample = 1
        var longEdge = maxOf(srcW, srcH)
        while (longEdge / 2 >= 1600) {
            sample *= 2
            longEdge /= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, decodeOpts)
            ?: return source

        val w = decoded.width
        val h = decoded.height
        val maxEdge = maxOf(w, h)
        val scaled = if (maxEdge <= 1600) {
            decoded
        } else {
            val ratio = 1600f / maxEdge
            val tmp = Bitmap.createScaledBitmap(
                decoded,
                (w * ratio).toInt(),
                (h * ratio).toInt(),
                /* filter = */ true
            )
            // M-02 — recycle the intermediate decoded bitmap if the scale call
            // returned a new instance (it usually does for non-trivial scales).
            if (tmp !== decoded) decoded.recycle()
            tmp
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, /* quality = */ 80, out)
        // Recycle the final scaled bitmap as well — we have the JPEG bytes now.
        scaled.recycle()
        return out.toByteArray()
    }
}
