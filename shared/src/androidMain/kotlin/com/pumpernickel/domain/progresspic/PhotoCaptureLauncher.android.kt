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
    private var pendingCameraDeferred: CompletableDeferred<Uri?>? = null
    private var pendingLibraryDeferred: CompletableDeferred<Uri?>? = null

    private val cameraLauncher = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = if (success) pendingCameraFile?.let { Uri.fromFile(it) } else null
        pendingCameraDeferred?.complete(uri)
        pendingCameraDeferred = null
    }

    private val libraryLauncher = activity.registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        pendingLibraryDeferred?.complete(uri)
        pendingLibraryDeferred = null
    }

    suspend fun launchCamera(): Uri? {
        val cacheDir = File(context.cacheDir, "capture").apply { mkdirs() }
        val file = File(cacheDir, "${UUID.randomUUID()}.jpg")
        pendingCameraFile = file
        val deferred = CompletableDeferred<Uri?>()
        pendingCameraDeferred = deferred
        // Use FileProvider to grant the camera app write access to our cache file.
        val authority = "${context.packageName}.provider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        cameraLauncher.launch(contentUri)
        return deferred.await()
    }

    suspend fun launchLibrary(): Uri? {
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
        val uri = host.launchCamera() ?: return@withContext null
        val bytes = readUriBytes(uri) ?: return@withContext null
        resizeAndEncode(bytes)
    }

    actual suspend fun pickFromLibrary(): ByteArray? = withContext(Dispatchers.IO) {
        val host = PhotoCaptureLauncherActivityHolder.current ?: return@withContext null
        val uri = host.launchLibrary() ?: return@withContext null
        val bytes = readUriBytes(uri) ?: return@withContext null
        resizeAndEncode(bytes)
    }

    private fun readUriBytes(uri: Uri): ByteArray? {
        return if (uri.scheme == "file") {
            uri.toFile().takeIf { it.exists() }?.readBytes()
        } else {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    }

    /**
     * Resizes the source bytes so the long edge is 1600px and re-encodes as
     * JPEG quality 0.8 (D-17-07). Falls back to source bytes if decode fails.
     */
    private fun resizeAndEncode(source: ByteArray): ByteArray {
        val original = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return source
        val w = original.width
        val h = original.height
        val longEdge = maxOf(w, h)
        val scaled = if (longEdge <= 1600) {
            original
        } else {
            val ratio = 1600f / longEdge
            Bitmap.createScaledBitmap(
                original,
                (w * ratio).toInt(),
                (h * ratio).toInt(),
                /* filter = */ true
            )
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, /* quality = */ 80, out)
        return out.toByteArray()
    }
}

private fun Uri.toFile(): File = File(requireNotNull(path) { "URI has no path: $this" })
