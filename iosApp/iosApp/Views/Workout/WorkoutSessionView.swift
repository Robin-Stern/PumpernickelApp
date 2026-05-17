import SwiftUI
import Shared
import KMPNativeCoroutinesAsync
import UIKit
import UserNotifications
import CoreLocation

/// Swift-native bridge for CoreLocation permission. The Kotlin/Native interop for
/// `CLLocationManager.requestWhenInUseAuthorization()` silently swallowed the call —
/// no system dialog, no delegate callback. Implementing it natively in Swift bypasses
/// the K/N bridge entirely. Holds the manager as a strong reference (CLLocationManager
/// retains its delegate weakly).
final class LocationPermissionRequester: NSObject, CLLocationManagerDelegate {
    static let shared = LocationPermissionRequester()
    private let manager: CLLocationManager
    private var pending: ((CLAuthorizationStatus) -> Void)?

    override init() {
        // CLLocationManager MUST be created on a thread with a runloop (main thread).
        // Force main-thread init even if .shared is first touched from a BG context.
        if Thread.isMainThread {
            self.manager = CLLocationManager()
        } else {
            self.manager = DispatchQueue.main.sync { CLLocationManager() }
        }
        super.init()
        if Thread.isMainThread {
            manager.delegate = self
        } else {
            DispatchQueue.main.sync { manager.delegate = self }
        }
        let servicesEnabled = CLLocationManager.locationServicesEnabled()
        print("[SwiftPerm] init — locationServicesEnabled=\(servicesEnabled) initialStatus=\(manager.authorizationStatus.rawValue) thread=\(Thread.isMainThread ? "Main" : "BG")")
    }

    func currentStatus() -> CLAuthorizationStatus {
        return manager.authorizationStatus
    }

    /// Request WhenInUse permission. Triggers the iOS system dialog if status is
    /// `.notDetermined`. Returns the resulting status via the completion handler.
    func requestWhenInUse(completion: @escaping (CLAuthorizationStatus) -> Void) {
        let initial = manager.authorizationStatus
        print("[SwiftPerm] requestWhenInUse — initial status=\(initial.rawValue) servicesEnabled=\(CLLocationManager.locationServicesEnabled())")
        if initial != .notDetermined {
            print("[SwiftPerm] status already decided, returning immediately")
            completion(initial)
            return
        }
        pending = completion
        DispatchQueue.main.async { [weak self] in
            print("[SwiftPerm] calling requestWhenInUseAuthorization() on main")
            self?.manager.requestWhenInUseAuthorization()
        }
    }

    func requestAlways(completion: @escaping (CLAuthorizationStatus) -> Void) {
        pending = completion
        DispatchQueue.main.async { [weak self] in
            self?.manager.requestAlwaysAuthorization()
        }
    }

    /// Fetch a single location fix. Bypasses the Kotlin/Native bridge for
    /// `manager.requestLocation()` (same K/N-swallow risk as the permission API).
    func requestSingleLocation(completion: @escaping (CLLocation?) -> Void) {
        let status = manager.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else {
            print("[SwiftPerm] requestSingleLocation: not authorized (status=\(status.rawValue)) — returning nil")
            completion(nil)
            return
        }
        pendingLocation = completion
        DispatchQueue.main.async { [weak self] in
            print("[SwiftPerm] calling manager.requestLocation() on main")
            self?.manager.requestLocation()
        }
    }

    private var pendingLocation: ((CLLocation?) -> Void)?

    // iOS 14+ delegate callback — fires once on delegate-attach with current status,
    // and again after every authorization change. We MUST skip the initial notDetermined
    // emission to avoid consuming the pending callback before the user even sees the dialog.
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        print("[SwiftPerm] locationManagerDidChangeAuthorization: status=\(status.rawValue)")
        // notDetermined is never a real "decision" — skip and keep waiting.
        if status == .notDetermined {
            print("[SwiftPerm] skipping notDetermined emission")
            return
        }
        let cb = pending
        pending = nil
        cb?(status)
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        print("[SwiftPerm] didUpdateLocations: count=\(locations.count)")
        let loc = locations.last
        let cb = pendingLocation
        pendingLocation = nil
        cb?(loc)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("[SwiftPerm] didFailWithError: \(error.localizedDescription)")
        let cb = pendingLocation
        pendingLocation = nil
        cb?(nil)
    }
}

// Fix side-by-side wheel picker touch overlap (ENTRY-01, ENTRY-02)
// Source: swiftuirecipes.com/blog/multi-column-wheel-picker-in-swiftui
extension UIPickerView {
    open override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: 150)
    }
}

struct WorkoutSessionView: View {
    var templateId: Int64 = 0
    var templateName: String = ""
    var isResume: Bool = false

    @State private var viewModel = KoinHelper.shared.getWorkoutSessionViewModel()
    private let permissionController = KoinHelper.shared.getPermissionController()

    @Environment(\.dismiss) private var dismiss

    @State private var sessionState: WorkoutSessionState = WorkoutSessionState.Idle.shared
    @State private var elapsedSeconds: Int64 = 0
    @State private var showExerciseOverview = false
    @State private var previousPerformance: [String: CompletedExercise] = [:]
    @State private var personalBest: [String: KotlinInt] = [:]
    @State private var weightUnit: WeightUnit = .kg
    @State private var showAbandonDialog = false

    // Picker selections for current set (ENTRY-01, ENTRY-02)
    @State private var selectedReps: Int = 0
    @State private var selectedWeightKgX10: Int = 0
    @State private var selectedRir: Int = 2

    // Edit set sheet
    @State private var showEditSheet = false
    @State private var editExerciseIndex: Int32 = 0
    @State private var editSetIndex: Int32 = 0
    // Picker selections for edit sheet
    @State private var editSelectedReps: Int = 0
    @State private var editSelectedWeightKgX10: Int = 0
    @State private var editSelectedRir: Int = 2

    // Track previous rest state for haptic trigger
    @State private var previousRestWasResting = false

    // Undertrained muscles dialog
    @State private var showUndertrainedDialog = false
    @State private var undertrainedMuscles: [UndertrainedMuscle] = []

    // Phase 19 state
    @State private var geofenceState: GeofenceUiState = GeofenceUiState.Inactive.shared
    @State private var earlyExitBudget: EarlyExitBudget? = nil
    @State private var locationPermissionStatus: LocationPermissionStatus = .notDetermined
    @State private var showRationaleSheet = false
    @State private var rationaleActivatePending = false
    // WARN-19-2 fix — per-launch persistence via @AppStorage (UserDefaults-backed).
    // Cleared on cold-start by AppDelegate so each app launch shows the rationale
    // once per workout-try, matching UI-SPEC's "once per session" semantics.
    @AppStorage("workout.geofence.rationale_shown_session") private var hasShownRationaleThisSession = false
    @State private var showEarlyExitDialog = false
    @State private var earlyExitDialogConfig: EarlyExitDialogConfig? = nil
    @State private var wasInGracePeriod = false           // for re-enter notification

    // Minimal set screen toggle (UX-01, D-02)
    @State private var showSetInput: Bool = false

    // DEBUG-only sheet trigger for in-workout geofence mock panel (quick-260517-pzh)
    #if DEBUG
    @State private var showDebugGeofenceSheet: Bool = false
    #endif

    // Picker value arrays
    private let repsRange = Array(0...50)
    private let weightValuesKgX10 = Array(stride(from: 0, through: 10000, by: 25))

    var body: some View {
        Group {
            if let active = sessionState as? WorkoutSessionState.Active {
                activeWorkoutView(active)
            } else if let reviewing = sessionState as? WorkoutSessionState.Reviewing {
                recapView(reviewing)
            } else if let finished = sessionState as? WorkoutSessionState.Finished {
                WorkoutFinishedView(
                    workoutName: finished.workoutName,
                    durationMillis: finished.durationMillis,
                    totalSets: finished.totalSets,
                    totalExercises: finished.totalExercises,
                    workoutId: finished.workoutId,
                    onDone: {
                        viewModel.resetToIdle()
                        dismiss()
                    }
                )
            } else {
                // Idle / loading state
                VStack(spacing: 16) {
                    ProgressView()
                        .scaleEffect(1.5)
                    Text("Starting workout...")
                        .font(.headline)
                        .foregroundColor(.secondary)
                }
            }
        }
        .navigationBarBackButtonHidden(true)
        .sheet(isPresented: $showExerciseOverview) {
            if let active = sessionState as? WorkoutSessionState.Active {
                ExerciseOverviewSheet(
                    exercises: active.exercises,
                    currentExerciseIndex: active.currentExerciseIndex,
                    onSelect: { index in
                        viewModel.jumpToExercise(exerciseIndex: index)
                        showExerciseOverview = false
                    },
                    onMove: { from, to in
                        viewModel.reorderExercise(from: Int32(from), to: Int32(to))
                    },
                    onSkip: {
                        viewModel.skipExercise()
                    }
                )
            }
        }
        .sheet(isPresented: $showEditSheet) {
            editSetSheet
        }
        .task {
            // BUG FIX (ios-camera-post-workout-lost): only kick off the
            // session-start when the VM is genuinely idle. SwiftUI's `.task`
            // re-fires every time the view re-appears, including after a
            // fullscreen modal (UIImagePickerController .camera) is dismissed.
            // Without this guard the camera-dismiss event re-invoked
            // startWorkout(templateId:) which regressed the state machine
            // from .Finished back to .Active(0,0) and inserted a fresh
            // active_session row in Room — the user landed on Exercise 1 /
            // Set 1 of a phantom workout immediately after the photo accept,
            // with the only escape being "discard current workout".
            //
            // The gallery path is unaffected because PHPickerViewController
            // presents as `.formSheet`/`.pageSheet` and does not remove the
            // presenter's view from the window — `.task` is not cancelled
            // and not re-fired on its dismissal.
            //
            // Observers (sessionStateFlow, elapsedSeconds, etc.) are
            // intentionally re-attached on every re-appearance: re-binding
            // the same StateFlow yields the current value plus subsequent
            // emissions, with no duplication risk.
            if sessionState is WorkoutSessionState.Idle {
                if isResume {
                    viewModel.resumeWorkout()
                } else {
                    viewModel.startWorkout(templateId: templateId)
                }
            }
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await observeSessionState() }
                group.addTask { await observeElapsedSeconds() }
                group.addTask { await observePreviousPerformance() }
                group.addTask { await observeWeightUnit() }
                group.addTask { await observePreFill() }
                group.addTask { await observePersonalBest() }
                group.addTask { await observeUndertrainedMuscles() }
                group.addTask { await observeGeofenceState() }
                group.addTask { await observeEarlyExitBudget() }
                group.addTask { await refreshPermissionStatusOnAppear() }
                group.addTask {
                    // D-19-09: show rationale once per session if status != ALWAYS
                    try? await Task.sleep(nanoseconds: 500_000_000)   // 0.5s for VM to settle
                    print("[Rationale] eval — hasShownRationaleThisSession=\(hasShownRationaleThisSession) locationPermissionStatus=\(locationPermissionStatus)")
                    if !hasShownRationaleThisSession && locationPermissionStatus != .always {
                        print("[Rationale] showing rationale sheet")
                        await MainActor.run {
                            showRationaleSheet = true
                            hasShownRationaleThisSession = true
                        }
                    } else {
                        print("[Rationale] NOT showing — condition not met")
                    }
                }
            }
        }
    }

    // MARK: - Active Workout View

    @ViewBuilder
    private func activeWorkoutView(_ active: WorkoutSessionState.Active) -> some View {
        let exercises = active.exercises
        let exIdx = Int(active.currentExerciseIndex)
        let setIdx = Int(active.currentSetIndex)
        let currentExercise = exercises[exIdx]
        let restState = active.restState

        ScrollView {
            VStack(spacing: 20) {
                // D-19-10 / D-19-11 — persistent permission banner above header
                if locationPermissionStatus == .whenInUse {
                    PermissionBanner(variant: .whenInUseOnly)
                } else if locationPermissionStatus == .denied || locationPermissionStatus == .restricted {
                    PermissionBanner(variant: .denied)
                }

                // Header section (WORK-06, WORK-08)
                headerSection(
                    active: active,
                    exercise: currentExercise,
                    exIdx: exIdx,
                    setIdx: setIdx,
                    totalExercises: exercises.count
                )

                // Rest timer or set input
                if let resting = restState as? RestState.Resting {
                    RestTimerView(
                        remainingSeconds: resting.remainingSeconds,
                        totalSeconds: resting.totalSeconds
                    )
                    Button("Skip Rest") {
                        viewModel.skipRest()
                    }
                    .font(.body.weight(.medium))
                    .foregroundColor(.secondary)
                    .accessibilityLabel("Skip rest timer")
                } else if restState is RestState.RestComplete {
                    VStack(spacing: 8) {
                        Text("Rest Complete!")
                            .font(.title3.weight(.semibold))
                            .foregroundColor(.appAccent)
                    }
                    .padding(.vertical, 12)

                    Button("Continue") {
                        viewModel.skipRest()
                    }
                    .font(.body.weight(.semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color.appAccent)
                    .cornerRadius(12)
                    .padding(.horizontal, 32)
                    .accessibilityLabel("Continue to next set")
                } else {
                    // Check if all sets for this exercise are already completed
                    let allSetsCompleted = currentExercise.sets.allSatisfy { $0.isCompleted }
                    if allSetsCompleted {
                        // Jumped back to a completed exercise — show edit prompt
                        VStack(spacing: 8) {
                            Text("All sets completed")
                                .font(.title3.weight(.semibold))
                                .foregroundColor(.secondary)
                            Text("Tap a set below to edit")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        .padding(.vertical, 24)
                    } else if showSetInput {
                        setInputSection(exercise: currentExercise, setIdx: setIdx)
                    } else {
                        minimalSetScreen(exercise: currentExercise, setIdx: setIdx)
                    }
                }

                // Completed sets for current exercise (D-11)
                completedSetsSection(exercise: currentExercise, exIdx: Int32(exIdx))
            }
            .padding()
            .onChange(of: active.currentSetIndex) { _, _ in
                showSetInput = false
            }
            .onChange(of: active.currentExerciseIndex) { _, _ in
                showSetInput = false
            }
        }
        .navigationTitle(active.templateName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button {
                    let hasCompletedSets = exercises.contains { ex in
                        ex.sets.contains { $0.isCompleted }
                    }
                    if hasCompletedSets {
                        showAbandonDialog = true
                    } else {
                        viewModel.discardWorkout()
                        dismiss()
                    }
                } label: {
                    Image(systemName: "xmark")
                }
                .accessibilityLabel("Close workout")
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                GeofenceStatusChip(state: geofenceState)
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Menu {
                    Button {
                        viewModel.skipExercise()
                    } label: {
                        Label("Skip Exercise", systemImage: "forward.fill")
                    }
                    .disabled(Int(active.currentExerciseIndex) + 1 >= exercises.count)

                    Button {
                        showExerciseOverview = true
                    } label: {
                        Label("Exercise Overview", systemImage: "list.bullet")
                    }

                    Button {
                        viewModel.enterReview()
                    } label: {
                        Label("Finish Workout", systemImage: "checkmark.circle")
                    }

                    Button {
                        let allDone = active.exercises.allSatisfy { ex in ex.sets.allSatisfy { $0.isCompleted } }
                        if allDone {
                            viewModel.enterReview()
                        } else {
                            let planned = active.exercises.reduce(0) { $0 + Int($1.targetSets) }
                            let logged = active.exercises.reduce(0) { sum, ex in sum + ex.sets.filter({ $0.isCompleted }).count }
                            let missed = max(0, planned - logged)
                            let penaltyRaw = missed * 10
                            let penalty = min(200, max(50, penaltyRaw))
                            let budget = self.earlyExitBudget
                            earlyExitDialogConfig = EarlyExitDialogConfig(
                                total: 2,
                                remaining: Int(budget?.remaining ?? 0),
                                penaltyXp: penalty
                            )
                            showEarlyExitDialog = true
                        }
                    } label: {
                        Label("Workout beenden", systemImage: "xmark.circle")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .accessibilityLabel("Workout actions")
                }
            }
        }
        .confirmationDialog(
            "Abandon Workout?",
            isPresented: $showAbandonDialog,
            titleVisibility: .visible
        ) {
            Button("Save & Exit") {
                viewModel.enterReview()
                viewModel.saveReviewedWorkout()
                dismiss()
            }
            Button("Discard", role: .destructive) {
                viewModel.discardWorkout()
                dismiss()
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            let completedSetsCount = exercises.reduce(0) { sum, ex in
                sum + ex.sets.filter { $0.isCompleted }.count
            }
            Text("Exercise \(exIdx + 1)/\(exercises.count), \(completedSetsCount) sets completed")
        }
        .alert("Vernachlässigte Muskeln", isPresented: $showUndertrainedDialog) {
            Button("OK", role: .cancel) { }
        } message: {
            Text("Diese Muskelgruppen wurden in den letzten 7 Tagen kaum oder gar nicht trainiert:\n\n" +
                 undertrainedMuscles.map { "• \($0.group.displayName)" }.joined(separator: "\n"))
        }
        .sheet(isPresented: $showRationaleSheet, onDismiss: {
            // CRITICAL: trigger permission request AFTER sheet has dismissed.
            // iOS will silently swallow a permission dialog request while another
            // modal is mid-transition or still presented. The flag pattern lets
            // SwiftUI fully tear down the rationale sheet before we ask iOS for
            // the system dialog.
            if rationaleActivatePending {
                rationaleActivatePending = false
                print("[Rationale] sheet dismissed, NOW triggering Swift-native permission request")
                LocationPermissionRequester.shared.requestWhenInUse { status in
                    print("[Rationale] WhenInUse callback: status=\(status.rawValue)")
                    let mapped: LocationPermissionStatus = {
                        switch status {
                        case .authorizedAlways: return .always
                        case .authorizedWhenInUse: return .whenInUse
                        case .denied: return .denied
                        case .restricted: return .restricted
                        default: return .notDetermined
                        }
                    }()
                    self.locationPermissionStatus = mapped
                    Task {
                        let notifResult = try? await permissionController.requestNotifications()
                        print("[Rationale] requestNotifications returned: \(String(describing: notifResult))")
                    }
                    if status == .authorizedWhenInUse {
                        print("[Rationale] WhenInUse granted, escalating to Always")
                        LocationPermissionRequester.shared.requestAlways { after in
                            print("[Rationale] Always callback: status=\(after.rawValue)")
                            let mappedAfter: LocationPermissionStatus = {
                                switch after {
                                case .authorizedAlways: return .always
                                case .authorizedWhenInUse: return .whenInUse
                                case .denied: return .denied
                                case .restricted: return .restricted
                                default: return .notDetermined
                                }
                            }()
                            self.locationPermissionStatus = mappedAfter
                        }
                    }
                }
            }
        }) {
            PermissionRationaleSheet(
                onActivate: {
                    print("[Rationale] onActivate tapped — flagging activate, sheet will dismiss")
                    rationaleActivatePending = true
                    // The sheet's Button dismiss() runs after this closure.
                    // The .sheet onDismiss handler will trigger the actual permission request.
                },
                onLater: {
                    print("[Rationale] onLater tapped")
                }
            )
        }
        .confirmationDialog(
            earlyExitDialogConfig?.title ?? "",
            isPresented: $showEarlyExitDialog,
            titleVisibility: .visible,
            presenting: earlyExitDialogConfig
        ) { cfg in
            Button(cfg.primaryButtonLabel, role: cfg.primaryButtonRole) {
                let result = viewModel.requestEarlyExit()
                // Post notification matching the path taken.
                let center = UNUserNotificationCenter.current()
                if cfg.hasBudget {
                    center.postGeofenceNotification(.earlyExitWithBudget(remainingAfter: cfg.remaining - 1))
                } else {
                    center.postGeofenceNotification(.earlyExitWithPenalty(penaltyXp: cfg.penaltyXp))
                }
                _ = result   // VM has already transitioned to Finished via async path
            }
            Button("Abbrechen", role: .cancel) { }
        } message: { cfg in
            Text(cfg.message)
        }
        #if DEBUG
        .overlay(alignment: .bottomLeading) {
            Button(action: { showDebugGeofenceSheet = true }) {
                HStack(spacing: 4) {
                    Image(systemName: "ladybug.fill")
                    Text("DEBUG")
                        .font(.caption.weight(.bold))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.red.opacity(0.85))
                .foregroundColor(.white)
                .clipShape(Capsule())
                .shadow(radius: 3)
            }
            .padding(.leading, 16)
            .padding(.bottom, 16)
            .accessibilityLabel("Open debug geofence panel")
        }
        .sheet(isPresented: $showDebugGeofenceSheet) {
            NavigationStack {
                Form {
                    DebugGeofencePanel()
                }
                .navigationTitle("Debug — Geofence")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Done") { showDebugGeofenceSheet = false }
                    }
                }
            }
            .presentationDetents([.medium, .large])
        }
        #endif
    }

    // MARK: - Header Section

    private func headerSection(
        active: WorkoutSessionState.Active,
        exercise: SessionExercise,
        exIdx: Int,
        setIdx: Int,
        totalExercises: Int
    ) -> some View {
        VStack(spacing: 4) {
            HStack {
                Text("Exercise \(exIdx + 1) of \(totalExercises)")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                Spacer()
                Text(formatElapsed(elapsedSeconds))
                    .font(.subheadline.monospacedDigit())
                    .foregroundColor(.secondary)
            }

            HStack(alignment: .firstTextBaseline) {
                Text(exercise.exerciseName)
                    .font(.title2.weight(.bold))
                Spacer()
                Text("Set \(setIdx + 1)/\(exercise.targetSets)")
                    .font(.headline)
                    .foregroundColor(.secondary)
            }

            // Previous performance & personal best inline (HIST-04, D-08, D-09, ENTRY-07)
            let prevText: String? = {
                if let prevExercise = previousPerformance[exercise.exerciseId] {
                    let text = formatPreviousPerformance(prevExercise)
                    return text.isEmpty ? nil : text
                }
                return nil
            }()
            let pbText: String? = {
                if let pbKgX10 = personalBest[exercise.exerciseId] {
                    return weightUnit.formatWeight(kgX10: pbKgX10.int32Value)
                }
                return nil
            }()

            if prevText != nil || pbText != nil {
                HStack(spacing: 16) {
                    if let prevText {
                        Label(prevText, systemImage: "clock")
                            .font(.subheadline)
                            .foregroundColor(.orange)
                    }
                    if let pbText {
                        Label(pbText, systemImage: "trophy")
                            .font(.subheadline)
                            .foregroundColor(.blue)
                    }
                    Spacer()
                }
            }
        }
    }

    // MARK: - Minimal Set Screen

    @ViewBuilder
    private func minimalSetScreen(exercise: SessionExercise, setIdx: Int) -> some View {
        VStack(spacing: 16) {
            Spacer()

            Text("SET")
                .font(.title3.weight(.semibold))
                .foregroundColor(.secondary)

            Text("\(setIdx + 1)")
                .font(.system(size: 72, weight: .bold, design: .rounded))

            Text(exercise.exerciseName)
                .font(.title3)
                .foregroundColor(.secondary)

            Spacer()

            Text("Tap when done")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, minHeight: 300)
        .contentShape(Rectangle())
        .onTapGesture {
            withAnimation(.easeInOut(duration: 0.2)) {
                showSetInput = true
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Set \(setIdx + 1) of \(exercise.exerciseName)")
        .accessibilityHint("Tap to show weight and reps input")
    }

    // MARK: - Set Input Section

    private func setInputSection(exercise: SessionExercise, setIdx: Int) -> some View {
        VStack(spacing: 16) {
            GeometryReader { geometry in
                HStack(spacing: 0) {
                    // Reps picker (ENTRY-01)
                    VStack(spacing: 4) {
                        Text("Reps")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Picker("Reps", selection: $selectedReps) {
                            ForEach(repsRange, id: \.self) { value in
                                Text("\(value)").tag(value)
                            }
                        }
                        .pickerStyle(.wheel)
                        .frame(width: geometry.size.width / 2)
                        .clipped()
                        .accessibilityLabel("Reps picker")
                        .accessibilityValue("\(selectedReps) reps")
                    }

                    // Weight picker (ENTRY-02, ENTRY-03)
                    VStack(spacing: 4) {
                        Text("Weight (\(weightUnit.label))")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Picker("Weight", selection: $selectedWeightKgX10) {
                            ForEach(weightValuesKgX10, id: \.self) { kgX10 in
                                Text(weightUnit.formatWeight(kgX10: Int32(kgX10)))
                                    .tag(kgX10)
                            }
                        }
                        .pickerStyle(.wheel)
                        .frame(width: geometry.size.width / 2)
                        .clipped()
                        .accessibilityLabel("Weight picker")
                        .accessibilityValue(weightUnit.formatWeight(kgX10: Int32(selectedWeightKgX10)))
                    }
                }
            }
            .frame(height: 170)

            // RIR tap selector
            rirSelector(selectedRir: $selectedRir)

            // Complete Set button (ENTRY-06: disabled when reps == 0)
            Button("Complete Set") {
                let generator = UINotificationFeedbackGenerator()
                generator.notificationOccurred(.success)
                viewModel.completeSet(
                    reps: Int32(selectedReps),
                    weightKgX10: Int32(selectedWeightKgX10),
                    rir: Int32(selectedRir)
                )
            }
            .font(.body.weight(.semibold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(selectedReps == 0
                ? Color.gray
                : Color.appAccent)
            .cornerRadius(12)
            .padding(.horizontal, 32)
            .disabled(selectedReps == 0)
            .accessibilityLabel("Complete set")
            .accessibilityHint("Logs current reps and weight")
        }
        .padding(.vertical, 8)
    }

    // MARK: - Completed Sets Section

    private func completedSetsSection(exercise: SessionExercise, exIdx: Int32) -> some View {
        let completedSets = exercise.sets.filter { $0.isCompleted }
        return Group {
            if !completedSets.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Completed Sets")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.secondary)

                    ForEach(completedSets, id: \.setIndex) { set in
                        WorkoutSetRow(
                            setIndex: set.setIndex,
                            actualReps: set.actualReps?.int32Value ?? 0,
                            actualWeightKgX10: set.actualWeightKgX10?.int32Value ?? 0,
                            rir: set.rir?.int32Value ?? 2,
                            isCompleted: set.isCompleted,
                            weightUnit: weightUnit,
                            onTap: {
                                editExerciseIndex = exIdx
                                editSetIndex = set.setIndex
                                editSelectedReps = Int(set.actualReps?.int32Value ?? 0)
                                editSelectedWeightKgX10 = snapToWeightStep(Int(set.actualWeightKgX10?.int32Value ?? 0))
                                editSelectedRir = Int(set.rir?.int32Value ?? 2)
                                showEditSheet = true
                            }
                        )
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    // MARK: - Edit Set Sheet (D-11)

    private var editSetSheet: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Text("Edit Set \(editSetIndex + 1)")
                    .font(.title3.weight(.bold))

                GeometryReader { geometry in
                    HStack(spacing: 0) {
                        VStack(spacing: 4) {
                            Text("Reps")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Picker("Reps", selection: $editSelectedReps) {
                                ForEach(repsRange, id: \.self) { value in
                                    Text("\(value)").tag(value)
                                }
                            }
                            .pickerStyle(.wheel)
                            .frame(width: geometry.size.width / 2)
                            .clipped()
                            .accessibilityLabel("Edit reps picker")
                            .accessibilityValue("\(editSelectedReps) reps")
                        }

                        VStack(spacing: 4) {
                            Text("Weight (\(weightUnit.label))")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Picker("Weight", selection: $editSelectedWeightKgX10) {
                                ForEach(weightValuesKgX10, id: \.self) { kgX10 in
                                    Text(weightUnit.formatWeight(kgX10: Int32(kgX10)))
                                        .tag(kgX10)
                                }
                            }
                            .pickerStyle(.wheel)
                            .frame(width: geometry.size.width / 2)
                            .clipped()
                            .accessibilityLabel("Edit weight picker")
                            .accessibilityValue(weightUnit.formatWeight(kgX10: Int32(editSelectedWeightKgX10)))
                        }
                    }
                }
                .frame(height: 170)

                // RIR selector
                rirSelector(selectedRir: $editSelectedRir)

                Button("Save") {
                    viewModel.editCompletedSet(
                        exerciseIndex: editExerciseIndex,
                        setIndex: editSetIndex,
                        reps: Int32(editSelectedReps),
                        weightKgX10: Int32(editSelectedWeightKgX10),
                        rir: Int32(editSelectedRir)
                    )
                    showEditSheet = false
                }
                .font(.body.weight(.semibold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Color.appAccent)
                .cornerRadius(12)
                .padding(.horizontal, 32)
                .accessibilityLabel("Save edited set")

                Spacer()
            }
            .padding()
            .navigationTitle("Edit Set")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        showEditSheet = false
                    }
                    .accessibilityLabel("Cancel editing")
                }
            }
        }
    }

    // MARK: - Recap View (FLOW-01, FLOW-02)

    @ViewBuilder
    private func recapView(_ reviewing: WorkoutSessionState.Reviewing) -> some View {
        ScrollView {
            VStack(spacing: 20) {
                // Summary header
                VStack(spacing: 8) {
                    Text("Workout Recap")
                        .font(.title2.weight(.bold))
                    Text(reviewing.templateName)
                        .font(.headline)
                        .foregroundColor(.secondary)

                    // Duration and stats
                    HStack(spacing: 24) {
                        let completedExercises = reviewing.exercises.filter { ex in
                            ex.sets.contains { $0.isCompleted }
                        }
                        let totalSets = completedExercises.reduce(0) { sum, ex in
                            sum + ex.sets.filter { $0.isCompleted }.count
                        }

                        VStack {
                            Text("\(completedExercises.count)")
                                .font(.title3.weight(.bold))
                            Text("Exercises")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }

                        VStack {
                            Text("\(totalSets)")
                                .font(.title3.weight(.bold))
                            Text("Sets")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }

                        VStack {
                            Text(formatDuration(reviewing.durationMillis))
                                .font(.title3.weight(.bold))
                            Text("Duration")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.top, 4)
                }
                .padding()
                .frame(maxWidth: .infinity)
                .background(Color(UIColor.secondarySystemBackground))
                .cornerRadius(16)

                // Exercise sections (per D-03, D-04 - only exercises with completed sets shown)
                ForEach(Array(reviewing.exercises.enumerated()), id: \.offset) { originalIndex, exercise in
                    let completedSets = exercise.sets.filter { $0.isCompleted }
                    if !completedSets.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            // Exercise header (per D-04)
                            HStack {
                                Text(exercise.exerciseName)
                                    .font(.headline)
                                Spacer()
                                Text("\(completedSets.count) sets")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }

                            // Set rows - tappable for edit (per D-05, D-06, FLOW-02)
                            ForEach(completedSets, id: \.setIndex) { set in
                                WorkoutSetRow(
                                    setIndex: set.setIndex,
                                    actualReps: set.actualReps?.int32Value ?? 0,
                                    actualWeightKgX10: set.actualWeightKgX10?.int32Value ?? 0,
                                    rir: set.rir?.int32Value ?? 2,
                                    isCompleted: true,
                                    weightUnit: weightUnit,
                                    onTap: {
                                        editExerciseIndex = Int32(originalIndex)
                                        editSetIndex = set.setIndex
                                        editSelectedReps = Int(set.actualReps?.int32Value ?? 0)
                                        editSelectedWeightKgX10 = snapToWeightStep(Int(set.actualWeightKgX10?.int32Value ?? 0))
                                        editSelectedRir = Int(set.rir?.int32Value ?? 2)
                                        showEditSheet = true
                                    }
                                )
                            }
                        }
                        .padding()
                        .background(Color(UIColor.secondarySystemBackground))
                        .cornerRadius(12)
                    }
                }

                // Save Workout button (per D-02) - prominent, green, at bottom
                Button("Save Workout") {
                    viewModel.saveReviewedWorkout()
                }
                .font(.body.weight(.semibold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Color.appAccent)
                .cornerRadius(12)
                .padding(.horizontal, 32)
                .padding(.top, 8)
                .accessibilityLabel("Save workout to history")
            }
            .padding()
        }
        .navigationTitle("Recap")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
    }

    // MARK: - Duration Formatting

    private func formatDuration(_ millis: Int64) -> String {
        let totalSeconds = millis / 1000
        let h = totalSeconds / 3600
        let m = (totalSeconds % 3600) / 60
        let s = totalSeconds % 60
        if h > 0 { return String(format: "%dh %02dm", h, m) }
        return String(format: "%dm %02ds", m, s)
    }

    // MARK: - Flow Observation

    private func observeGeofenceState() async {
        do {
            for try await value in asyncSequence(for: viewModel.geofenceStateFlow) {
                handleGeofenceStateChange(old: self.geofenceState, new: value)
                self.geofenceState = value
            }
        } catch {
            print("geofence state observation error: \(error)")
        }
    }

    private func observeEarlyExitBudget() async {
        do {
            for try await value in asyncSequence(for: viewModel.earlyExitBudgetFlow) {
                self.earlyExitBudget = value
            }
        } catch {
            print("earlyExit budget observation error: \(error)")
        }
    }

    @MainActor
    private func refreshPermissionStatusOnAppear() async {
        do {
            let status = try await permissionController.currentLocationStatus()
            self.locationPermissionStatus = status
        } catch {
            self.locationPermissionStatus = .notDetermined
        }
    }

    private func handleGeofenceStateChange(old: GeofenceUiState, new: GeofenceUiState) {
        // D-19-15 notification triggers.
        let center = UNUserNotificationCenter.current()
        switch new {
        case is GeofenceUiState.GracePeriod:
            if !(old is GeofenceUiState.GracePeriod) {
                // Just entered grace
                center.postGeofenceNotification(.exitDetected)
                wasInGracePeriod = true
            }
        case is GeofenceUiState.InZone:
            if wasInGracePeriod {
                center.postGeofenceNotification(.reEntered)
                wasInGracePeriod = false
            }
        case is GeofenceUiState.Exited:
            // Grace period expired — post the grace-expired notification.
            let active = sessionState as? WorkoutSessionState.Active
            let logged = active?.exercises.reduce(0) { sum, ex in sum + ex.sets.filter({ $0.isCompleted }).count } ?? 0
            let planned = active?.exercises.reduce(0) { $0 + Int($1.targetSets) } ?? 0
            let missed = max(0, planned - logged)
            let penalty = min(200, max(50, missed * 10))
            center.postGeofenceNotification(.graceExpired(loggedSets: logged, penaltyXp: penalty))
        default: break
        }
    }

    private func observeSessionState() async {
        do {
            for try await value in asyncSequence(for: viewModel.sessionStateFlow) {
                let newState = value

                // Haptic feedback on rest complete (D-07, WORK-05)
                if let active = newState as? WorkoutSessionState.Active {
                    if active.restState is RestState.RestComplete && previousRestWasResting {
                        let generator = UINotificationFeedbackGenerator()
                        generator.prepare()
                        generator.notificationOccurred(.success)
                    }
                    previousRestWasResting = active.restState is RestState.Resting
                }

                self.sessionState = newState
            }
        } catch {
            print("SessionState observation error: \(error)")
        }
    }

    private func observeElapsedSeconds() async {
        do {
            for try await value in asyncSequence(for: viewModel.elapsedSecondsFlow) {
                self.elapsedSeconds = value.int64Value
            }
        } catch {
            print("ElapsedSeconds observation error: \(error)")
        }
    }

    private func observePreviousPerformance() async {
        do {
            for try await value in asyncSequence(for: viewModel.previousPerformanceFlow) {
                self.previousPerformance = value
            }
        } catch {
            print("Previous performance observation error: \(error)")
        }
    }

    private func observeWeightUnit() async {
        do {
            for try await value in asyncSequence(for: viewModel.weightUnitFlow) {
                self.weightUnit = value
            }
        } catch {
            print("Weight unit observation error: \(error)")
        }
    }

    private func observePreFill() async {
        do {
            for try await value in asyncSequence(for: viewModel.preFillFlow) {
                self.selectedReps = Int(value.reps)
                self.selectedWeightKgX10 = snapToWeightStep(Int(value.weightKgX10))
            }
        } catch {
            print("PreFill observation error: \(error)")
        }
    }

    private func observePersonalBest() async {
        do {
            for try await value in asyncSequence(for: viewModel.personalBestFlow) {
                self.personalBest = value
            }
        } catch {
            print("Personal best observation error: \(error)")
        }
    }

    private func observeUndertrainedMuscles() async {
        do {
            for try await value in asyncSequence(for: viewModel.undertrainedMusclesFlow) {
                if !value.isEmpty {
                    self.undertrainedMuscles = value
                    self.showUndertrainedDialog = true
                }
            }
        } catch {
            print("Undertrained muscles observation error: \(error)")
        }
    }

    // MARK: - Previous Performance Formatting

    private func formatPreviousPerformance(_ exercise: CompletedExercise) -> String {
        let sets = exercise.sets
        if sets.isEmpty { return "" }

        // If all sets have same reps and weight, show compact format: "3x10 @ 50.0 kg"
        let firstSet = sets[0]
        let allSame = sets.allSatisfy {
            $0.actualReps == firstSet.actualReps && $0.actualWeightKgX10 == firstSet.actualWeightKgX10
        }

        if allSame {
            return "\(sets.count)x\(firstSet.actualReps) @ \(weightUnit.formatWeight(kgX10: firstSet.actualWeightKgX10))"
        }

        // Different sets: show each briefly "10x50.0 kg, 8x50.0 kg, 6x50.0 kg"
        return sets.map { set in
            "\(set.actualReps)x\(weightUnit.formatWeight(kgX10: set.actualWeightKgX10))"
        }.joined(separator: ", ")
    }

    // MARK: - RIR Selector

    private func rirSelector(selectedRir: Binding<Int>) -> some View {
        VStack(spacing: 6) {
            Text("RIR (Reps in Reserve)")
                .font(.caption)
                .foregroundColor(.secondary)

            HStack(spacing: 10) {
                ForEach([0, 1, 2, 3, 4], id: \.self) { value in
                    let label = value >= 4 ? "4+" : "\(value)"
                    let isSelected = selectedRir.wrappedValue == value

                    Button(action: {
                        selectedRir.wrappedValue = value
                    }) {
                        Text(label)
                            .font(.body.weight(.bold))
                            .frame(width: 44, height: 44)
                            .background(
                                isSelected ? Color.appAccent : Color(.systemGray5)
                            )
                            .foregroundColor(
                                isSelected ? .white : .primary
                            )
                            .cornerRadius(10)
                    }
                    .accessibilityLabel("RIR \(label)")
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
        }
    }

    // MARK: - Helpers

    /// Snap a kgX10 value to the nearest valid picker step (multiple of 25)
    private func snapToWeightStep(_ kgX10: Int) -> Int {
        return ((kgX10 + 12) / 25) * 25  // round to nearest 25
    }

    private func formatElapsed(_ seconds: Int64) -> String {
        let h = seconds / 3600
        let m = (seconds % 3600) / 60
        let s = seconds % 60
        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        }
        return String(format: "%d:%02d", m, s)
    }
}
