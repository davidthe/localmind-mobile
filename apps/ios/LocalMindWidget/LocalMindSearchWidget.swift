import SwiftUI
import WidgetKit

struct LocalMindSearchEntry: TimelineEntry {
    let date: Date
}

struct LocalMindSearchProvider: TimelineProvider {
    func placeholder(in context: Context) -> LocalMindSearchEntry {
        LocalMindSearchEntry(date: Date())
    }

    func getSnapshot(in context: Context, completion: @escaping (LocalMindSearchEntry) -> Void) {
        completion(LocalMindSearchEntry(date: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LocalMindSearchEntry>) -> Void) {
        completion(Timeline(entries: [LocalMindSearchEntry(date: Date())], policy: .never))
    }
}

struct LocalMindSearchWidgetView: View {
    @Environment(\.widgetFamily) private var family
    var entry: LocalMindSearchProvider.Entry

    private let allSearchURL = URL(string: "localmind://search?scope=all&focus=true&fromWidget=true")
    private let fileNamesURL = URL(string: "localmind://search?scope=filenames&focus=true&fromWidget=true")!
    private let insideFilesURL = URL(string: "localmind://search?scope=contents&focus=true&fromWidget=true")!

    var body: some View {
        Group {
            switch family {
            case .systemSmall:
                smallView
            case .accessoryRectangular:
                lockScreenView
            default:
                mediumView
            }
        }
        .containerBackground(for: .widget) {
            Color(red: 0.118, green: 0.122, blue: 0.125)
        }
        .widgetURL(allSearchURL)
    }

    private var smallView: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "sparkles")
                    .foregroundStyle(Color(red: 0.54, green: 0.71, blue: 0.97))
                Text("LocalMind")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white)
            }
            Spacer()
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Text("Search files…")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white.opacity(0.08))
            .clipShape(Capsule())
        }
    }

    private var mediumView: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "sparkles")
                    .foregroundStyle(Color(red: 0.54, green: 0.71, blue: 0.97))
                Text("Search file names & inside files…")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Spacer()
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color.white.opacity(0.08))
            .clipShape(Capsule())

            HStack(spacing: 8) {
                Link(destination: fileNamesURL) {
                    Label("File Names", systemImage: "doc.text.magnifyingglass")
                        .font(.caption.weight(.medium))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 7)
                        .background(Color(red: 0.16, green: 0.21, blue: 0.28))
                        .foregroundStyle(Color(red: 0.82, green: 0.89, blue: 0.99))
                        .clipShape(Capsule())
                }

                Link(destination: insideFilesURL) {
                    Label("Inside Files", systemImage: "text.magnifyingglass")
                        .font(.caption.weight(.medium))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 7)
                        .background(Color(red: 0.16, green: 0.21, blue: 0.28))
                        .foregroundStyle(Color(red: 0.82, green: 0.89, blue: 0.99))
                        .clipShape(Capsule())
                }
            }
        }
    }

    private var lockScreenView: some View {
        HStack(spacing: 6) {
            Image(systemName: "magnifyingglass")
            VStack(alignment: .leading) {
                Text("LocalMind")
                    .font(.caption.weight(.bold))
                Text("Search files")
                    .font(.caption2)
            }
        }
    }
}

@main
struct LocalMindSearchWidget: Widget {
    let kind: String = "LocalMindSearchWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: LocalMindSearchProvider()) { entry in
            LocalMindSearchWidgetView(entry: entry)
        }
        .configurationDisplayName("LocalMind Search")
        .description("Clean search bar widget for file names and inside files.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular])
    }
}
