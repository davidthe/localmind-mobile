import SwiftUI

struct ContentView: View {
    @ObservedObject var viewModel: SearchViewModel
    @FocusState private var isSearchFocused: Bool
    @State private var showWidgetGuide = false

    private var userResponses: [SmallLlmResponse] {
        viewModel.responses.dropLast(viewModel.responses.last?.query.isEmpty == true ? 1 : 0)
    }

    private var searchPlaceholder: String {
        switch viewModel.options.scope {
        case .all: return "Search file names & inside files…"
        case .fileNames: return "Search file names…"
        case .insideFiles: return "Search inside files…"
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                // Minimal Google / Gemini-style header
                HStack {
                    HStack(spacing: 6) {
                        Image(systemName: "sparkles")
                            .foregroundStyle(Color(red: 0.54, green: 0.71, blue: 0.97))
                        Text("LocalMind")
                            .font(.title3.weight(.medium))
                    }
                    Spacer()
                    Button("+ Widget") {
                        showWidgetGuide = true
                    }
                    .font(.caption.weight(.medium))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(Capsule())
                }

                if userResponses.isEmpty {
                    Spacer(minLength: 12)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Search your local files")
                            .font(.title2.weight(.medium))
                            .foregroundStyle(
                                LinearGradient(
                                    colors: [
                                        Color(red: 0.54, green: 0.71, blue: 0.97),
                                        Color(red: 0.77, green: 0.54, blue: 0.98)
                                    ],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                        Text("Search inside files or file names like VS Code, powered on-device.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                // Clean Google / Gemini Pill Search Bar
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .foregroundStyle(.secondary)

                    TextField(searchPlaceholder, text: $viewModel.query)
                        .focused($isSearchFocused)
                        .submitLabel(.search)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onSubmit {
                            viewModel.submitSearch()
                        }

                    if !viewModel.query.isEmpty {
                        Button {
                            viewModel.query = ""
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                    }

                    Button {
                        viewModel.submitSearch()
                    } label: {
                        Image(systemName: "arrow.up")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(Color(red: 0.04, green: 0.10, blue: 0.18))
                            .frame(width: 34, height: 34)
                            .background(Color(red: 0.54, green: 0.71, blue: 0.97))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.leading, 16)
                .padding(.trailing, 8)
                .padding(.vertical, 8)
                .background(Color(.secondarySystemBackground))
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(Color.white.opacity(0.12), lineWidth: 1)
                )

                // Single horizontal row of scope & VS Code match pills
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(SearchScope.allCases) { scope in
                            MinimalPillButton(
                                title: scope.label,
                                isSelected: viewModel.options.scope == scope
                            ) {
                                viewModel.selectScope(scope)
                            }
                        }

                        Divider()
                            .frame(height: 18)

                        MinimalPillButton(
                            title: "Aa",
                            isMonospace: true,
                            isSelected: viewModel.options.caseSensitive,
                            action: viewModel.toggleCaseSensitive
                        )
                        MinimalPillButton(
                            title: "ab",
                            isMonospace: true,
                            isSelected: viewModel.options.wholeWord,
                            action: viewModel.toggleWholeWord
                        )
                        MinimalPillButton(
                            title: ".*",
                            isMonospace: true,
                            isSelected: viewModel.options.useRegex,
                            action: viewModel.toggleRegex
                        )
                    }
                }

                if let notice = viewModel.statusNotice {
                    Text(notice)
                        .font(.caption)
                        .foregroundStyle(Color(red: 0.54, green: 0.71, blue: 0.97))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                if userResponses.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            Button("Find *.swift files") {
                                viewModel.runWidgetPreset(query: "*.swift", scope: .fileNames)
                            }
                            .buttonStyle(.bordered)
                            .clipShape(Capsule())
                            .font(.caption)

                            Button("Search \"TODO\" inside files") {
                                viewModel.runWidgetPreset(query: "TODO", scope: .insideFiles)
                            }
                            .buttonStyle(.bordered)
                            .clipShape(Capsule())
                            .font(.caption)
                        }
                    }
                    Spacer()
                } else {
                    HStack {
                        Text("Responses")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.secondary)
                        Spacer()
                        Button("Clear") {
                            viewModel.clearResponses()
                        }
                        .font(.caption)
                    }

                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 14) {
                            ForEach(userResponses) { response in
                                GeminiResponseRow(response: response)
                            }
                        }
                    }
                }

                Text("LocalMind · Offline-first on-device LLM")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            .padding()
            .onChange(of: viewModel.focusRequestCounter) { _, newValue in
                if newValue > 0 {
                    isSearchFocused = true
                }
            }
            .sheet(isPresented: $showWidgetGuide) {
                WidgetGuideSheet()
            }
        }
    }
}

private struct MinimalPillButton: View {
    let title: String
    var isMonospace: Bool = false
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(isMonospace ? .system(.caption, design: .monospaced) : .caption)
                .fontWeight(isSelected ? .semibold : .regular)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? Color(red: 0.16, green: 0.21, blue: 0.28) : Color(.secondarySystemBackground))
                .foregroundStyle(isSelected ? Color(red: 0.82, green: 0.89, blue: 0.99) : .secondary)
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(isSelected ? Color(red: 0.54, green: 0.71, blue: 0.97) : Color.white.opacity(0.10), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}

private struct GeminiResponseRow: View {
    let response: SmallLlmResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !response.query.isEmpty {
                let flags = response.options.activeFlagLabels
                let flagSuffix = flags.isEmpty ? "" : " · \(flags.joined(separator: " · "))"
                HStack {
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(response.query)
                            .font(.body)
                        Text("\(response.options.scope.label)\(flagSuffix)")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 9)
                    .background(Color(.tertiarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                }
            }

            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "sparkles")
                    .font(.caption)
                    .foregroundStyle(Color(red: 0.54, green: 0.71, blue: 0.97))
                    .frame(width: 26, height: 26)
                    .background(Color(red: 0.13, green: 0.17, blue: 0.23))
                    .clipShape(Circle())

                VStack(alignment: .leading, spacing: 4) {
                    Text(response.modelName)
                        .font(.caption2)
                        .foregroundStyle(Color(red: 0.54, green: 0.71, blue: 0.97))
                    Text(response.answer)
                        .font(.body)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }
}

private struct WidgetGuideSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("Add LocalMind Search Widget")
                    .font(.headline)
                Text("1. Touch and hold an empty area on your Home Screen.")
                Text("2. Tap Edit > Add Widget and search for LocalMind.")
                Text("3. Pick the Small or Medium search bar widget.")
                Spacer()
            }
            .padding()
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
