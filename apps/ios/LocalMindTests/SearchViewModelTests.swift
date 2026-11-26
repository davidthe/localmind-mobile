import XCTest
@testable import LocalMind

final class SearchViewModelTests: XCTestCase {
    func testTinyLlmReportsSearchNotYetSupported() {
        let responder = TinyLocalLlmResponder()
        let response = responder.respondToSearch(
            query: "ContentView.swift",
            options: SearchOptions(scope: .fileNames, caseSensitive: true),
            triggeredFromWidget: true
        )

        XCTAssertFalse(response.searchSupported)
        XCTAssertTrue(response.triggeredFromWidget)
        XCTAssertTrue(response.answer.localizedCaseInsensitiveContains("not yet supported"))
        XCTAssertTrue(response.answer.localizedCaseInsensitiveContains("file names"))
        XCTAssertTrue(response.answer.contains("Match Case (Aa)"))
    }

    func testDeepLinkRoundTrip() throws {
        let url = try XCTUnwrap(
            SearchDeepLink.buildURL(
                scope: .insideFiles,
                focus: true,
                query: "TODO",
                autoSubmit: true,
                fromWidget: true
            )
        )
        let parsed = try XCTUnwrap(SearchDeepLink.parse(url))
        XCTAssertEqual(parsed.scope, .insideFiles)
        XCTAssertEqual(parsed.query, "TODO")
        XCTAssertTrue(parsed.focusSearchLine)
        XCTAssertTrue(parsed.autoSubmit)
        XCTAssertTrue(parsed.fromWidget)
    }

    @MainActor
    func testViewModelWidgetPresetAddsUnsupportedAnswer() {
        let vm = SearchViewModel()
        vm.toggleCaseSensitive()
        vm.runWidgetPreset(query: "TODO", scope: .insideFiles)

        XCTAssertEqual(vm.query, "TODO")
        XCTAssertEqual(vm.options.scope, .insideFiles)
        XCTAssertTrue(vm.options.caseSensitive)
        XCTAssertEqual(vm.focusRequestCounter, 1)
        XCTAssertEqual(vm.responses.first?.searchSupported, false)
        XCTAssertTrue(vm.responses.first?.answer.localizedCaseInsensitiveContains("not yet supported") == true)
    }
}
