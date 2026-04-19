import SwiftUI
import AVFoundation

struct BarcodeScannerView: UIViewControllerRepresentable {
    let onBarcodeScanned: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> BarcodeScannerViewController {
        let vc = BarcodeScannerViewController()
        vc.onBarcodeScanned = { barcode in
            onBarcodeScanned(barcode)
        }
        vc.onCancel = {
            dismiss()
        }
        return vc
    }

    func updateUIViewController(_ uiViewController: BarcodeScannerViewController, context: Context) {}
}

class BarcodeScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onBarcodeScanned: ((String) -> Void)?
    var onCancel: (() -> Void)?

    private let captureSession = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var hasDetected = false
    private var cancelButton: UIButton!
    private var overlayView: ScannerOverlayView!

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        checkCameraPermission()
        setupOverlay()
        setupCancelButton()
        setupTapToFocus()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.captureSession.startRunning()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        captureSession.stopRunning()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.layer.bounds
    }

    private func checkCameraPermission() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            setupCamera()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted {
                        self?.setupCamera()
                    } else {
                        self?.showError("Kamerazugriff wurde verweigert.\nBitte in den Einstellungen aktivieren.")
                    }
                }
            }
        case .denied, .restricted:
            showError("Kamerazugriff wurde verweigert.\nBitte in den Einstellungen aktivieren.")
        @unknown default:
            showError("Kamera nicht verfügbar")
        }
    }

    private func setupCamera() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device) else {
            showError("Kamera nicht verfügbar")
            return
        }

        if captureSession.canAddInput(input) {
            captureSession.addInput(input)
        }

        let output = AVCaptureMetadataOutput()
        if captureSession.canAddOutput(output) {
            captureSession.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: .main)
            output.metadataObjectTypes = [.ean13, .ean8]
        }

        let layer = AVCaptureVideoPreviewLayer(session: captureSession)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.layer.bounds
        view.layer.insertSublayer(layer, at: 0)
        previewLayer = layer

        // Ensure overlay and button stay above camera preview
        if overlayView != nil {
            view.bringSubviewToFront(overlayView)
        }
        if cancelButton != nil {
            view.bringSubviewToFront(cancelButton)
        }
    }

    private func setupOverlay() {
        overlayView = ScannerOverlayView()
        overlayView.translatesAutoresizingMaskIntoConstraints = false
        overlayView.isUserInteractionEnabled = false
        overlayView.backgroundColor = .clear
        view.addSubview(overlayView)
        NSLayoutConstraint.activate([
            overlayView.topAnchor.constraint(equalTo: view.topAnchor),
            overlayView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlayView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlayView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    private func setupCancelButton() {
        var config = UIButton.Configuration.filled()
        config.title = "Abbrechen"
        config.baseForegroundColor = .white
        config.baseBackgroundColor = UIColor.black.withAlphaComponent(0.6)
        config.cornerStyle = .medium
        config.contentInsets = NSDirectionalEdgeInsets(top: 12, leading: 24, bottom: 12, trailing: 24)
        cancelButton = UIButton(configuration: config)
        cancelButton.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)
        cancelButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(cancelButton)
        NSLayoutConstraint.activate([
            cancelButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            cancelButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24)
        ])

        // Hint label above the viewfinder
        let hintLabel = UILabel()
        hintLabel.text = "Barcode in den Rahmen halten"
        hintLabel.textColor = UIColor.white.withAlphaComponent(0.85)
        hintLabel.font = .systemFont(ofSize: 15, weight: .medium)
        hintLabel.textAlignment = .center
        hintLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(hintLabel)
        NSLayoutConstraint.activate([
            hintLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            hintLabel.bottomAnchor.constraint(equalTo: cancelButton.topAnchor, constant: -20)
        ])
    }

    private func setupTapToFocus() {
        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTapToFocus(_:)))
        view.addGestureRecognizer(tap)
    }

    @objc private func handleTapToFocus(_ gesture: UITapGestureRecognizer) {
        guard let previewLayer = previewLayer else { return }
        let touchPoint = gesture.location(in: view)
        let focusPoint = previewLayer.captureDevicePointConverted(fromLayerPoint: touchPoint)

        guard let device = AVCaptureDevice.default(for: .video) else { return }
        do {
            try device.lockForConfiguration()
            if device.isFocusPointOfInterestSupported {
                device.focusPointOfInterest = focusPoint
                device.focusMode = .autoFocus
            }
            if device.isExposurePointOfInterestSupported {
                device.exposurePointOfInterest = focusPoint
                device.exposureMode = .autoExpose
            }
            device.unlockForConfiguration()

            showFocusIndicator(at: touchPoint)
        } catch {
            // Silently ignore focus errors
        }
    }

    private func showFocusIndicator(at point: CGPoint) {
        let size: CGFloat = 70
        let indicator = UIView(frame: CGRect(x: point.x - size / 2, y: point.y - size / 2, width: size, height: size))
        indicator.layer.borderColor = UIColor.white.cgColor
        indicator.layer.borderWidth = 1.5
        indicator.layer.cornerRadius = 8
        indicator.alpha = 0
        view.addSubview(indicator)

        UIView.animate(withDuration: 0.15, animations: {
            indicator.alpha = 1
            indicator.transform = CGAffineTransform(scaleX: 0.8, y: 0.8)
        }) { _ in
            UIView.animate(withDuration: 0.3, delay: 0.5, options: [], animations: {
                indicator.alpha = 0
            }) { _ in
                indicator.removeFromSuperview()
            }
        }
    }

    @objc private func cancelTapped() {
        captureSession.stopRunning()
        onCancel?()
    }

    private func showError(_ message: String) {
        let label = UILabel()
        label.text = message
        label.textColor = .white
        label.textAlignment = .center
        label.numberOfLines = 0
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard !hasDetected,
              let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let barcode = object.stringValue else { return }

        hasDetected = true
        captureSession.stopRunning()
        AudioServicesPlaySystemSound(SystemSoundID(kSystemSoundID_Vibrate))

        // Small delay ensures the session is fully stopped before dismissing
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { [weak self] in
            self?.onBarcodeScanned?(barcode)
        }
    }
}

// MARK: - Scanner Overlay (viewfinder rectangle)

private class ScannerOverlayView: UIView {
    private let viewfinderSize = CGSize(width: 280, height: 180)
    private let cornerRadius: CGFloat = 12
    private let cornerLength: CGFloat = 24
    private let cornerStrokeWidth: CGFloat = 4

    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext() else { return }

        let vfRect = CGRect(
            x: (bounds.width - viewfinderSize.width) / 2,
            y: (bounds.height - viewfinderSize.height) / 2 - 40,
            width: viewfinderSize.width,
            height: viewfinderSize.height
        )

        // Dim area outside viewfinder
        ctx.setFillColor(UIColor.black.withAlphaComponent(0.55).cgColor)
        ctx.fill(bounds)
        let cutout = UIBezierPath(roundedRect: vfRect, cornerRadius: cornerRadius)
        ctx.setBlendMode(.clear)
        cutout.fill()
        ctx.setBlendMode(.normal)

        // Thin border
        let border = UIBezierPath(roundedRect: vfRect, cornerRadius: cornerRadius)
        border.lineWidth = 1.5
        UIColor.white.withAlphaComponent(0.5).setStroke()
        border.stroke()

        // L-shaped corner markers
        UIColor.white.setStroke()
        let cLen = cornerLength
        let l = vfRect.minX, t = vfRect.minY, r = vfRect.maxX, b = vfRect.maxY

        let corners = UIBezierPath()
        corners.lineWidth = cornerStrokeWidth
        corners.lineCapStyle = .round

        // Top-left
        corners.move(to: CGPoint(x: l, y: t + cLen))
        corners.addLine(to: CGPoint(x: l, y: t))
        corners.addLine(to: CGPoint(x: l + cLen, y: t))
        // Top-right
        corners.move(to: CGPoint(x: r - cLen, y: t))
        corners.addLine(to: CGPoint(x: r, y: t))
        corners.addLine(to: CGPoint(x: r, y: t + cLen))
        // Bottom-left
        corners.move(to: CGPoint(x: l, y: b - cLen))
        corners.addLine(to: CGPoint(x: l, y: b))
        corners.addLine(to: CGPoint(x: l + cLen, y: b))
        // Bottom-right
        corners.move(to: CGPoint(x: r - cLen, y: b))
        corners.addLine(to: CGPoint(x: r, y: b))
        corners.addLine(to: CGPoint(x: r, y: b - cLen))

        corners.stroke()
    }
}
