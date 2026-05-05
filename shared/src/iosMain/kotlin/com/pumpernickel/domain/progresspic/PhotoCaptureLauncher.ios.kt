@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel.domain.progresspic

import kotlin.concurrent.Volatile
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import platform.CoreGraphics.CGFloat
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * Holder for the current root UIViewController. The iOS app entry point
 * (typically iosApp.swift or the AppRootView's UIKit bridge) sets this
 * during onAppear / scene activation. The 17-IOS-HANDOFF doc (plan 17-08)
 * documents how the SwiftUI side wires this.
 */
object PhotoCapturePresenterHolder {
    @Volatile
    var current: UIViewController? = null

    fun attach(controller: UIViewController) { current = controller }
    fun detach(controller: UIViewController) {
        if (current === controller) current = null
    }
}

/**
 * iOS-side actual for [PhotoCaptureLauncher]. Bridges UIImagePickerController
 * (camera, D-17-03) and PHPickerViewController (library, D-17-03) to Kotlin
 * suspend functions, returning resized JPEG bytes (1600px long edge, quality
 * 0.8 per D-17-07). Returns null on cancel.
 */
actual class PhotoCaptureLauncher {

    // REVIEW M-05: hold the active delegate explicitly on the class instance
    // until the deferred completes. The picker holds its delegate via a weak
    // ObjC property, so the only thing keeping it alive across the suspension
    // point is the reference graph from `this`. Relying on a local `val` and
    // a `@Suppress("UNUSED_EXPRESSION")` reference is fragile — K/N's
    // continuation lowering may discard the local before the callback fires.
    // Field-level retention is the K/N-idiomatic fix.
    @Volatile
    private var currentCameraDelegate: ImagePickerDelegate? = null

    @Volatile
    private var currentLibraryDelegate: PhPickerDelegate? = null

    actual suspend fun captureFromCamera(): ByteArray? {
        // Simulator has no camera and some iPads/iPods report unavailable.
        // Without this guard, setting sourceType = .camera below raises
        // NSInvalidArgumentException ("Source type 1 not available") which
        // K/N propagates as a fatal crash. Throw instead so the shared VM's
        // Throwable catch surfaces the message inline (D-17-17).
        if (!UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            )
        ) {
            throw IllegalStateException("Kamera ist auf diesem Gerät nicht verfügbar.")
        }

        val presenter = PhotoCapturePresenterHolder.current ?: return null

        val deferred = CompletableDeferred<UIImage?>()
        val delegate = ImagePickerDelegate(deferred)
        currentCameraDelegate = delegate
        try {
            val picker = UIImagePickerController()
            picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            picker.delegate = delegate

            presenter.presentViewController(picker, animated = true, completion = null)
            val image = deferred.await()
            if (image == null) return null

            return image.toResizedJpegBytes(maxLongEdgePx = 1600.0, quality = 0.8)
        } finally {
            // Only clear if it's still us — guards against a concurrent
            // captureFromCamera() that overwrote the field after we yielded.
            if (currentCameraDelegate === delegate) currentCameraDelegate = null
        }
    }

    actual suspend fun pickFromLibrary(): ByteArray? {
        val presenter = PhotoCapturePresenterHolder.current ?: return null

        val deferred = CompletableDeferred<UIImage?>()
        val config = PHPickerConfiguration().apply {
            selectionLimit = 1
            filter = PHPickerFilter.imagesFilter
        }
        val delegate = PhPickerDelegate(deferred)
        currentLibraryDelegate = delegate
        try {
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = delegate

            presenter.presentViewController(picker, animated = true, completion = null)
            val image = deferred.await()
            if (image == null) return null

            return image.toResizedJpegBytes(maxLongEdgePx = 1600.0, quality = 0.8)
        } finally {
            if (currentLibraryDelegate === delegate) currentLibraryDelegate = null
        }
    }
}

private class ImagePickerDelegate(
    private val deferred: CompletableDeferred<UIImage?>
) : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        deferred.complete(image)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        deferred.complete(null)
    }
}

private class PhPickerDelegate(
    private val deferred: CompletableDeferred<UIImage?>
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        val first = results.firstOrNull()
        if (first == null) {
            deferred.complete(null)
            return
        }
        val provider: NSItemProvider = first.itemProvider
        // REVIEW M-04: avoid the `UIImage as NSItemProviderReadingProtocol`
        // metaclass cast (it required a CAST_NEVER_SUCCEEDS suppression and
        // was K/N-version-fragile). Instead, request the raw image bytes via
        // loadDataRepresentationForTypeIdentifier and decode them with
        // UIImage's NSData initializer. Result: no protocol cast, no
        // suppression, and the picker fails cleanly if the underlying
        // identifier is missing.
        provider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            if (data == null) {
                deferred.complete(null)
            } else {
                deferred.complete(UIImage.imageWithData(data))
            }
        }
    }
}

/**
 * Resizes the source UIImage so the long edge is [maxLongEdgePx], preserving
 * aspect ratio, then encodes JPEG at [quality] (0..1). Returns null only if
 * UIImageJPEGRepresentation returns null (very rare).
 */
private fun UIImage.toResizedJpegBytes(maxLongEdgePx: Double, quality: Double): ByteArray? {
    val (w, h) = this.size.useContents { width to height }
    val longEdge = maxOf(w, h)
    val scaleFactor: CGFloat = if (longEdge <= maxLongEdgePx) 1.0 else (maxLongEdgePx / longEdge)
    val targetW = w * scaleFactor
    val targetH = h * scaleFactor

    val resized = if (scaleFactor == 1.0) this else this.resized(targetW, targetH)
    val jpegData: NSData = UIImageJPEGRepresentation(resized, quality) ?: return null
    return jpegData.toByteArray()
}

private fun UIImage.resized(targetW: Double, targetH: Double): UIImage {
    val newSize = CGSizeMake(targetW, targetH)
    UIGraphicsBeginImageContextWithOptions(newSize, false, this.scale)
    this.drawInRect(CGRectMake(0.0, 0.0, targetW, targetH))
    val out = UIGraphicsGetImageFromCurrentImageContext() ?: this
    UIGraphicsEndImageContext()
    return out
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
    return out
}
