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
import platform.Foundation.NSItemProviderReadingProtocol
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

    actual suspend fun captureFromCamera(): ByteArray? {
        val presenter = PhotoCapturePresenterHolder.current ?: return null

        val deferred = CompletableDeferred<UIImage?>()
        val delegate = ImagePickerDelegate(deferred)
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.delegate = delegate

        // Hold a strong reference so the delegate is not GC'd while the
        // picker is presented (Kotlin/Native ObjC delegates are weakly held
        // by the picker; the local `delegate` val keeps it alive).
        presenter.presentViewController(picker, animated = true, completion = null)
        val image = deferred.await()
        // Keep the delegate alive past await(); referencing it after the
        // suspension point prevents the compiler from dropping it early.
        @Suppress("UNUSED_EXPRESSION") delegate
        if (image == null) return null

        return image.toResizedJpegBytes(maxLongEdgePx = 1600.0, quality = 0.8)
    }

    actual suspend fun pickFromLibrary(): ByteArray? {
        val presenter = PhotoCapturePresenterHolder.current ?: return null

        val deferred = CompletableDeferred<UIImage?>()
        val config = PHPickerConfiguration().apply {
            selectionLimit = 1
            filter = PHPickerFilter.imagesFilter
        }
        val delegate = PhPickerDelegate(deferred)
        val picker = PHPickerViewController(configuration = config)
        picker.delegate = delegate

        presenter.presentViewController(picker, animated = true, completion = null)
        val image = deferred.await()
        @Suppress("UNUSED_EXPRESSION") delegate
        if (image == null) return null

        return image.toResizedJpegBytes(maxLongEdgePx = 1600.0, quality = 0.8)
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
        // K/N quirk: loadObjectOfClass / canLoadObjectOfClass take an
        // NSItemProviderReadingProtocol parameter, but at runtime ObjC expects
        // a Class object. UIImage's metaclass conforms to NSItemProviderReading
        // (UIImage adopts the protocol via category), so the cast is safe.
        @Suppress("CAST_NEVER_SUCCEEDS")
        val uiImageReadingClass = UIImage as NSItemProviderReadingProtocol
        if (!provider.canLoadObjectOfClass(uiImageReadingClass)) {
            deferred.complete(null)
            return
        }
        provider.loadObjectOfClass(uiImageReadingClass) { obj, _ ->
            deferred.complete(obj as? UIImage)
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
