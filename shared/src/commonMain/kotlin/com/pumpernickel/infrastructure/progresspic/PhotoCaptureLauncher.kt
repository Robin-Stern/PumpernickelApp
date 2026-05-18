package com.pumpernickel.infrastructure.progresspic

/**
 * System camera + photo-library wrapper. Returns JPEG bytes already resized
 * to a 1600px long edge, encoded at quality 0.8 (D-17-07). Returns null when
 * the user cancels.
 *
 * Android `actual` uses ActivityResultContracts.TakePicture +
 * ActivityResultContracts.PickVisualMedia (per CONTEXT discretion line 22 —
 * simpler than CameraX for a single still).
 *
 * iOS `actual` uses UIImagePickerController (camera) and PHPickerViewController
 * (library), both bridged via suspendCancellableCoroutine.
 */
expect class PhotoCaptureLauncher {
    suspend fun captureFromCamera(): ByteArray?
    suspend fun pickFromLibrary(): ByteArray?
}
