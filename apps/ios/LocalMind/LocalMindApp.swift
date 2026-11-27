import SwiftUI

@main
struct LocalMindApp: App {
    @StateObject private var searchViewModel = SearchViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: searchViewModel)
                .preferredColorScheme(.dark)
                .onOpenURL { url in
                    if let request = SearchDeepLink.parse(url) {
                        searchViewModel.applyLaunchRequest(request)
                    }
                }
        }
    }
}
