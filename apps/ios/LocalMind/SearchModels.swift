import Foundation

public enum SearchScope: String, CaseIterable, Codable, Sendable, Identifiable {
    case all
    case fileNames = "filenames"
    case insideFiles = "contents"

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .all: return "All"
        case .fileNames: return "File Names"
        case .insideFiles: return "Inside Files"
        }
    }

    public static func fromWire(_ value: String?) -> SearchScope {
        guard let value = value?.lowercased() else { return .all }
        return Self.allCases.first { $0.rawValue == value } ?? .all
    }
}

public struct SearchOptions: Equatable, Codable, Sendable {
    public var scope: SearchScope
    public var caseSensitive: Bool
    public var wholeWord: Bool
    public var useRegex: Bool

    public init(
        scope: SearchScope = .all,
        caseSensitive: Bool = false,
        wholeWord: Bool = false,
        useRegex: Bool = false
    ) {
        self.scope = scope
        self.caseSensitive = caseSensitive
        self.wholeWord = wholeWord
        self.useRegex = useRegex
    }

    public var activeFlagLabels: [String] {
        var labels: [String] = []
        if caseSensitive { labels.append("Match Case (Aa)") }
        if wholeWord { labels.append("Whole Word (ab)") }
        if useRegex { labels.append("Regex (.*)") }
        return labels
    }
}

public struct SmallLlmResponse: Identifiable, Equatable, Sendable {
    public let id: UUID
    public let modelName: String
    public let query: String
    public let options: SearchOptions
    public let answer: String
    public let searchSupported: Bool
    public let triggeredFromWidget: Bool

    public init(
        id: UUID = UUID(),
        modelName: String,
        query: String,
        options: SearchOptions,
        answer: String,
        searchSupported: Bool,
        triggeredFromWidget: Bool = false
    ) {
        self.id = id
        self.modelName = modelName
        self.query = query
        self.options = options
        self.answer = answer
        self.searchSupported = searchSupported
        self.triggeredFromWidget = triggeredFromWidget
    }
}

public protocol SmallLlmResponding: Sendable {
    var modelName: String { get }
    func respondToSearch(
        query: String,
        options: SearchOptions,
        triggeredFromWidget: Bool
    ) -> SmallLlmResponse
}

/// On-device small LLM stub responder for the initial iOS native app milestone.
/// Reports clearly that VS Code-style file-name and in-file searching is not yet supported.
public struct TinyLocalLlmResponder: SmallLlmResponding {
    public let modelName: String

    public init(modelName: String = "LocalMind-TinyLLM (on-device stub)") {
        self.modelName = modelName
    }

    public func respondToSearch(
        query: String,
        options: SearchOptions,
        triggeredFromWidget: Bool = false
    ) -> SmallLlmResponse {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let scopeDescription: String
        switch options.scope {
        case .all: scopeDescription = "file names and inside files"
        case .fileNames: scopeDescription = "file names"
        case .insideFiles: scopeDescription = "inside files"
        }
        let flags = options.activeFlagLabels
        let flagSuffix = flags.isEmpty ? "" : " with \(flags.joined(separator: ", "))"
        let sourcePrefix = triggeredFromWidget ? "[Search Widget] " : ""

        let answer: String
        if trimmed.isEmpty {
            answer = "\(sourcePrefix)Small LLM (\(modelName)): Searching is not yet supported. Type a file name or text snippet in the search line to preview \(scopeDescription) search."
        } else {
            answer = "\(sourcePrefix)Small LLM (\(modelName)): Searching for \"\(trimmed)\" in \(scopeDescription)\(flagSuffix) is not yet supported in this version. Local indexing and VS Code-style search execution will be added next."
        }

        return SmallLlmResponse(
            modelName: modelName,
            query: trimmed,
            options: options,
            answer: answer,
            searchSupported: false,
            triggeredFromWidget: triggeredFromWidget
        )
    }
}

public struct SearchLaunchRequest: Equatable, Sendable {
    public var query: String
    public var scope: SearchScope
    public var focusSearchLine: Bool
    public var autoSubmit: Bool
    public var fromWidget: Bool

    public init(
        query: String = "",
        scope: SearchScope = .all,
        focusSearchLine: Bool = false,
        autoSubmit: Bool = false,
        fromWidget: Bool = false
    ) {
        self.query = query
        self.scope = scope
        self.focusSearchLine = focusSearchLine
        self.autoSubmit = autoSubmit
        self.fromWidget = fromWidget
    }
}

public enum SearchDeepLink {
    public static let scheme = "localmind"
    public static let host = "search"

    public static func buildURL(
        scope: SearchScope = .all,
        focus: Bool = true,
        query: String = "",
        autoSubmit: Bool = false,
        fromWidget: Bool = true
    ) -> URL? {
        var components = URLComponents()
        components.scheme = scheme
        components.host = host
        var items = [
            URLQueryItem(name: "scope", value: scope.rawValue),
            URLQueryItem(name: "focus", value: focus ? "true" : "false"),
            URLQueryItem(name: "fromWidget", value: fromWidget ? "true" : "false")
        ]
        if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            items.append(URLQueryItem(name: "q", value: query))
        }
        if autoSubmit {
            items.append(URLQueryItem(name: "submit", value: "true"))
        }
        components.queryItems = items
        return components.url
    }

    public static func parse(_ url: URL) -> SearchLaunchRequest? {
        guard url.scheme?.lowercased() == scheme,
              url.host?.lowercased() == host else {
            return nil
        }
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let map = Dictionary(items.compactMap { item in
            item.value.map { (item.name, $0) }
        }, uniquingKeysWith: { _, latest in latest })

        return SearchLaunchRequest(
            query: map["q"] ?? "",
            scope: SearchScope.fromWire(map["scope"]),
            focusSearchLine: (map["focus"] as NSString?)?.boolValue ?? true,
            autoSubmit: (map["submit"] as NSString?)?.boolValue ?? false,
            fromWidget: (map["fromWidget"] as NSString?)?.boolValue ?? true
        )
    }
}
