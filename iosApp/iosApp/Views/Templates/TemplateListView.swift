import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct TemplateListView: View {
    private let viewModel = KoinHelper.shared.getTemplateListViewModel()
    private let workoutViewModel = KoinHelper.shared.getWorkoutSessionViewModel()

    @State private var templates: [WorkoutTemplate] = []
    @State private var showDeleteConfirmation = false
    @State private var templateToDelete: WorkoutTemplate? = nil

    // Workout navigation state
    @State private var showResumePrompt = false
    @State private var activeWorkoutNavigation = false
    @State private var isResumeNavigation = false
    @State private var selectedTemplateId: Int64 = 0
    @State private var selectedTemplateName: String = ""

    var body: some View {
        Group {
            if templates.isEmpty {
                emptyState
            } else {
                templateList
            }
        }
        .navigationTitle("Workout")
        .toolbar {
            if !templates.isEmpty {
                ToolbarItem(placement: .navigationBarTrailing) {
                    NavigationLink(destination: TemplateEditorView()) {
                        Image(systemName: "plus")
                    }
                }
            }
        }
        .navigationDestination(isPresented: $activeWorkoutNavigation) {
            WorkoutSessionView(
                templateId: selectedTemplateId,
                templateName: selectedTemplateName,
                isResume: isResumeNavigation
            )
        }
        .alert("Delete Template?", isPresented: $showDeleteConfirmation, presenting: templateToDelete) { template in
            Button("Delete", role: .destructive) {
                viewModel.deleteTemplate(id: template.id)
            }
            Button("Cancel", role: .cancel) {}
        } message: { template in
            Text("This cannot be undone.")
        }
        .alert("Resume Workout?", isPresented: $showResumePrompt) {
            Button("Resume") {
                isResumeNavigation = true
                activeWorkoutNavigation = true
            }
            Button("Discard", role: .destructive) {
                workoutViewModel.discardWorkout()
            }
        } message: {
            Text("You have an unfinished workout. Would you like to continue?")
        }
        .task {
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await observeTemplates() }
                group.addTask { await observeHasActiveSession() }
            }
        }
        .onAppear {
            workoutViewModel.checkForActiveSession()
        }
    }

    // MARK: - Empty State (per D-01)
    private var emptyState: some View {
        VStack(spacing: 0) {
            Spacer()
            VStack(spacing: 16) {
                Image(systemName: "list.bullet.clipboard")
                    .font(.system(size: 64))
                    .foregroundColor(Color(white: 0.62))
                Text("No Templates Yet")
                    .font(.title3.weight(.semibold))
                Text("Create a workout template to get started.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }
            Spacer()
            NavigationLink(destination: TemplateEditorView()) {
                Text("Create Template")
                    .font(.body.weight(.semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color(red: 0.4, green: 0.733, blue: 0.416))
                    .cornerRadius(12)
            }
            .padding(.horizontal, 32)
            .padding(.bottom, 32)
        }
    }

    // MARK: - Template List
    private var templateList: some View {
        List {
            ForEach(templates, id: \.id) { template in
                HStack {
                    NavigationLink(destination: TemplateEditorView(templateId: template.id)) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(template.name)
                                .font(.body.weight(.semibold))
                            Text("\(template.exercises.count) exercise\(template.exercises.count == 1 ? "" : "s")")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        .frame(minHeight: 40)
                    }

                    Button {
                        selectedTemplateId = template.id
                        selectedTemplateName = template.name
                        isResumeNavigation = false
                        activeWorkoutNavigation = true
                    } label: {
                        Image(systemName: "play.circle.fill")
                            .font(.title2)
                            .foregroundColor(Color(red: 0.4, green: 0.733, blue: 0.416))
                    }
                    .accessibilityLabel("Start Workout")
                    .buttonStyle(.plain)
                }
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) {
                        templateToDelete = template
                        showDeleteConfirmation = true
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }
                }
            }
        }
        .listStyle(.plain)
    }

    // MARK: - Flow Observation
    private func observeTemplates() async {
        do {
            for try await value in asyncSequence(for: viewModel.templatesFlow) {
                self.templates = value
            }
        } catch {
            print("TemplateList flow observation error: \(error)")
        }
    }

    private func observeHasActiveSession() async {
        do {
            for try await hasActive in asyncSequence(for: workoutViewModel.hasActiveSessionFlow) {
                if hasActive.boolValue {
                    showResumePrompt = true
                }
            }
        } catch {
            print("Error observing hasActiveSession: \(error)")
        }
    }
}
