import Combine
import Foundation

@MainActor
final class SearchViewModel: ObservableObject {
    @Published var query: String = ""
    @Published var options: SearchOptions = SearchOptions()
    @Published private(set) var responses: [SmallLlmResponse]
    @Published var focusRequestCounter: Int = 0
    @Published var statusNotice: String?

    private let responder: SmallLlmResponding

    init(responder: SmallLlmResponding = TinyLocalLlmResponder()) {
        self.responder = responder
        self.responses = [
            SmallLlmResponse(
                modelName: responder.modelName,
                query: "",
                options: SearchOptions(),
                answer: "LocalMind TinyLLM is ready on-device. Use the search line above or a search widget option (File Names / Inside Files). Live local search is not yet supported.",
                searchSupported: false
            )
        ]
    }

    func selectScope(_ scope: SearchScope) {
        options.scope = scope
    }

    func toggleCaseSensitive() {
        options.caseSensitive.toggle()
    }

    func toggleWholeWord() {
        options.wholeWord.toggle()
    }

    func toggleRegex() {
        options.useRegex.toggle()
    }

    func submitSearch(triggeredFromWidget: Bool = false) {
        let response = responder.respondToSearch(
            query: query,
            options: options,
            triggeredFromWidget: triggeredFromWidget
        )
        responses.insert(response, at: 0)
        statusNotice = nil
    }

    func runWidgetPreset(query presetQuery: String, scope: SearchScope) {
        query = presetQuery
        options.scope = scope
        focusRequestCounter += 1
        submitSearch(triggeredFromWidget: true)
    }

    func applyLaunchRequest(_ request: SearchLaunchRequest) {
        if !request.query.isEmpty {
            query = request.query
        }
        options.scope = request.scope
        if request.focusSearchLine {
            focusRequestCounter += 1
        }
        if request.fromWidget {
            statusNotice = "Opened from Home Search Widget (\(request.scope.label))"
        }
        if request.autoSubmit || !request.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            submitSearch(triggeredFromWidget: request.fromWidget)
        }
    }

    func clearResponses() {
        responses = []
        statusNotice = nil
    }
}
