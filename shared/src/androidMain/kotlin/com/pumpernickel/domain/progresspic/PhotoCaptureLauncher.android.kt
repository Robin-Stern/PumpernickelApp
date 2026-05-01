package com.pumpernickel.domain.progresspic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Holder for the host ComponentActivity. The MainActivity (plan 17-05)
 * registers itself in onCreate via [PhotoCaptureLauncherActivityHolder.attach].
 * Both ActivityResultLauncher contracts (TakePicture + PickVisualMedia) are
 * pre-registered eagerly so capture flow can launch without onCreate-only
 * constraints.
 *
 * Symmetric with [BiometricGateActivityHolder] — one consistent setup hook
 * for both Activity-bound services in MainActivity.onCreate.
 */
object PhotoCaptureLauncherActivityHolder {
    @Volatile
    var current: PhotoCaptureLauncherHost? = null
        private set

    fun attach(host: PhotoCaptureLauncherHost) { current = host }
    fun detach(host: PhotoCaptureLauncherHost) {
        if (current === host) current = null
    }
}

/**
 * Per-Activity host that owns the ActivityResultLauncher registrations.
 * The MainActivity instantiates this in onCreate (BEFORE setContent — the
 * Activity Result API requires registration before the Activity reaches
 * STARTED state) and calls [PhotoCaptureLauncherActivityHolder.attach(this)].
 *
 * Holds at most one pending capture deferred per modality at a time — fine
 * because the UI flow only launches one capture at a time.
 */
class PhotoCaptureLauncherHost(
    activity: ComponentActivity,
    private val context: Context
) {

    private var pendingCameraFile: File? = null
    // REVIEW B-03: camera deferred returns the *file we passed to the camera*,
    // not the URI the camera reports back. The TakePicture contract gives us a
    // boolean, not a URI, so the only attacker-controllable value here is the
    // success flag. We deliberately do NOT trust any returned URI / Uri.toFile()
    // path — we re-open the cache file we ourselves created.
    private var pendingCameraDeferred: CompletableDeferred<File?>? = null
    private var pendingLibraryDeferred: CompletableDeferred<Uri?>? = null

    private val cameraLauncher = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = if (success) pendingCameraFile else null
        pendingCameraDeferred?.complete(file)
        pendingCameraDeferred = null
        pendingCameraFile = null
    }

    private val libraryLauncher = activity.registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        pendingLibraryDeferred?.complete(uri)
        pendingLibraryDeferred = null
    }

    /**
     * Launches the system camera. Returns the [File] inside our app cache that
     * the camera wrote to (the same File we passed via FileProvider URI), or
     * null on cancel. Crucially, we never read from a URI the camera echoed
     * back — REVIEW B-03 — so a malicious package cannot redirect us to read
     * an arbitrary file.
     *
     * REVIEW M-01: a previous in-flight camera launch is cancelled (its
     * deferred completes with null) before the new one starts, so no awaiter
     * is left dangling and no result is silently dropped. The VM-layer busy
     * flag still prevents re-entrant taps in the normal flow; this is defence
     * in depth for any path that reaches the host directly.
     */
    suspend fun launchCamera(): File? {
        // M-01: cancel any prior in-flight camera capture so the previous
        // awaiter doesn't suspend forever.
        pendingCameraDeferred?.complete(null)
        pendingCameraDeferred = null

        val cacheDir = File(context.cacheDir, "capture").apply { mkdirs() }
        val file = File(cacheDir, "${UUID.randomUUID()}.jpg")
        pendingCameraFile = file
        val deferred = CompletableDeferred<File?>()
        pendingCameraDeferred = deferred
        // Use FileProvider to grant the camera app write access to our cache file.
        val authority = "${context.packageName}.provider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        cameraLauncher.launch(contentUri)
        return deferred.await()
    }

    suspend fun launchLibrary(): Uri? {
        // M-01: cancel any prior in-flight library pick the same way.
        pendingLibraryDeferred?.complete(null)
        pendingLibraryDeferred = null

        val deferred = CompletableDeferred<Uri?>()
        pendingLibraryDeferred = deferred
        libraryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
        return deferred.await()
    }
}

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

