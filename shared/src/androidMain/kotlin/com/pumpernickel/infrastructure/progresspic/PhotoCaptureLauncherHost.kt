package com.pumpernickel.infrastructure.progresspic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.UUID

/**
 * Holder for the host ComponentActivity. The MainActivity (plan 17-05)
 * registers itself in onCreate via [PhotoCaptureLauncherActivityHolder.attach].
 * Both ActivityResultLauncher contracts (TakePicture + PickVisualMedia) are
 * pre-registered eagerly so capture flow can launch without onCreate-only
 * constraints.
 *
 * Symmetric with [BiometricGateActivityHolder] —
 * one consistent setup hook for both Activity-bound services in MainActivity.onCreate.
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
    private var pendingCameraPermissionDeferred: CompletableDeferred<Boolean>? = null

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

    // Manifest declares <uses-permission CAMERA>, so on Android 6.0+ we must
    // grant it at runtime before launching IMAGE_CAPTURE — otherwise the system
    // blocks the intent with "Permission Denial: starting intent { act=
    // android.media.action.IMAGE_CAPTURE ... requires android.permission.CAMERA".
    private val cameraPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingCameraPermissionDeferred?.complete(granted)
        pendingCameraPermissionDeferred = null
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
        // Ensure the runtime CAMERA permission is granted before launching the
        // IMAGE_CAPTURE intent — see [cameraPermissionLauncher].
        if (!ensureCameraPermission()) return null

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

    private suspend fun ensureCameraPermission(): Boolean {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) return true

        pendingCameraPermissionDeferred?.complete(false)
        pendingCameraPermissionDeferred = null

        val deferred = CompletableDeferred<Boolean>()
        pendingCameraPermissionDeferred = deferred
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
