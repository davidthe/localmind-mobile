import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @State private var mode = 0
    @State private var prompt = ""
    @State private var search = ""
    @State private var messages = ["LocalMind is on-device. Connect a local model runtime to enable chat."]
    @State private var showPicker = false
    var body: some View {
        NavigationStack {
            VStack(spacing: 14) {
                Picker("Mode", selection: $mode) { Text("Chat").tag(0); Text("Search").tag(1) }.pickerStyle(.segmented)
                if mode == 0 {
                    ScrollView { VStack(alignment: .leading, spacing: 10) { ForEach(messages, id: \.self) { Text($0).padding().frame(maxWidth: .infinity, alignment: .leading).background(.thinMaterial).clipShape(RoundedRectangle(cornerRadius: 14)) } } }
                    HStack { TextField("Ask your local AI…", text: $prompt).textFieldStyle(.roundedBorder); Button("Send") { guard !prompt.isEmpty else { return }; messages += ["You: \(prompt)", "AI: Model runtime is not connected yet."]; prompt = "" } }
                } else {
                    TextField("Search local files", text: $search).textFieldStyle(.roundedBorder)
                    Button("Choose files") { showPicker = true }.buttonStyle(.borderedProminent)
                    Text("Search index and Rust core are scaffolded; native file access and FFI still need integration.").font(.footnote).foregroundStyle(.secondary)
                }
                Text("Private · Offline-first").font(.caption).foregroundStyle(.secondary)
            }.padding().navigationTitle("LocalMind")
            .fileImporter(isPresented: $showPicker, allowedContentTypes: [.item], allowsMultipleSelection: true) { _ in }
        }
    }
}
