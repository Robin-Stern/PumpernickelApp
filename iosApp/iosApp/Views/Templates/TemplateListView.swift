import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct TemplateListView: View {
    private let viewModel = KoinHelper.shared.getTemplateListViewModel()

    @State private var templates: [WorkoutTemplate] = []
    @State private var showDeleteConfirmation = false
    @State private var templateToDelete: WorkoutTemplate? = nil

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
        .alert("Delete Template?", isPresented: $showDeleteConfirmation, presenting: templateToDelete) { template in
            Button("Delete", role: .destructive) {
                viewModel.deleteTemplate(id: template.id)
            }
            Button("Cancel", role: .cancel) {}
        } message: { template in
            Text("This cannot be undone.")
        }
        .task {
            await observeTemplates()
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
}
