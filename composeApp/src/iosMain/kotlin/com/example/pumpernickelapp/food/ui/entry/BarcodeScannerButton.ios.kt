package com.example.pumpernickelapp.food.ui.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.compose.resources.stringResource
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue
import pumpernickelapp.composeapp.generated.resources.Res
import pumpernickelapp.composeapp.generated.resources.action_cancel
import pumpernickelapp.composeapp.generated.resources.action_scan_barcode
import pumpernickelapp.composeapp.generated.resources.msg_camera_permission_denied

@Composable
actual fun BarcodeScannerButton(onBarcodeScanned: (String) -> Unit, modifier: Modifier) {
    var showScanner by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    Button(
        onClick = {
            val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
            when (status) {
                AVAuthorizationStatusAuthorized -> {
                    showScanner = true
                    permissionDenied = false
                }
                AVAuthorizationStatusNotDetermined -> {
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                        if (granted) {
                            showScanner = true
                            permissionDenied = false
                        } else {
                            permissionDenied = true
                        }
                    }
                }
                else -> {
                    permissionDenied = true
                }
            }
        },
        modifier = modifier
    ) {
        Text(stringResource(Res.string.action_scan_barcode))
    }

    if (permissionDenied) {
        Text(
            text = stringResource(Res.string.msg_camera_permission_denied),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (showScanner) {
        BarcodeScannerDialog(
            onBarcodeDetected = { barcode ->
                showScanner = false
                onBarcodeScanned(barcode)
            },
            onDismiss = { showScanner = false }
        )
    }
}

@Composable
private fun BarcodeScannerDialog(
    onBarcodeDetected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            IosCameraPreview(onBarcodeDetected = onBarcodeDetected)

            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
            ) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
private fun IosCameraPreview(onBarcodeDetected: (String) -> Unit) {
    var detected by remember { mutableStateOf(false) }

    val captureSession = remember { AVCaptureSession() }

    val metadataDelegate = remember {
        object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
            override fun captureOutput(
                output: platform.AVFoundation.AVCaptureOutput,
                didOutputMetadataObjects: List<*>,
                fromConnection: platform.AVFoundation.AVCaptureConnection
            ) {
                if (detected) return
                val metadata = didOutputMetadataObjects.firstOrNull()
                    as? AVMetadataMachineReadableCodeObject ?: return
                val value = metadata.stringValue ?: return
                detected = true
                captureSession.stopRunning()
                onBarcodeDetected(value)
            }
        }
    }

    DisposableEffect(Unit) {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device != null) {
            val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            if (input != null && captureSession.canAddInput(input)) {
                captureSession.addInput(input)
            }

            val metadataOutput = AVCaptureMetadataOutput()
            if (captureSession.canAddOutput(metadataOutput)) {
                captureSession.addOutput(metadataOutput)
                metadataOutput.setMetadataObjectsDelegate(metadataDelegate, dispatch_get_main_queue())
                metadataOutput.metadataObjectTypes = listOf(
                    AVMetadataObjectTypeEAN13Code,
                    AVMetadataObjectTypeEAN8Code
                )
            }

            captureSession.startRunning()
        }

        onDispose {
            captureSession.stopRunning()
        }
    }

    val previewLayer = remember {
        AVCaptureVideoPreviewLayer(session = captureSession).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }
    }

    UIKitView(
        factory = {
            val view = UIView()
            view.layer.addSublayer(previewLayer)
            view
        },
        update = { view ->
            previewLayer.setFrame(view.bounds)
        },
        modifier = Modifier.fillMaxSize()
    )
}
