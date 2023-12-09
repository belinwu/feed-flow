//
//  HomeScreen.swift
//  FeedFlow
//
//  Created by Marco Gomiero on 27/03/23.
//  Copyright © 2023 FeedFlow. All rights reserved.
//

import SwiftUI
import KMPNativeCoroutinesAsync
import shared
import OrderedCollections

// swiftlint:disable file_length
struct HomeScreen: View {

    @EnvironmentObject
    private var appState: AppState

    @EnvironmentObject
    private var browserSelector: BrowserSelector

    @EnvironmentObject
    private var indexHolder: HomeListIndexHolder

    @Environment(\.scenePhase)
    private var scenePhase

    @State
    var loadingState: FeedUpdateStatus?

    @State
    var feedState: [FeedItem] = []

    @State
    var showLoading: Bool = true

    @State
    private var sheetToShow: HomeSheetToShow?

    @State
    var unreadCount = 0

    @Binding
    var toggleListScroll: Bool

    let homeViewModel: HomeViewModel

    var body: some View {

        HomeContent(
            loadingState: $loadingState,
            feedState: $feedState,
            showLoading: $showLoading,
            unreadCount: $unreadCount,
            sheetToShow: $sheetToShow,
            toggleListScroll: $toggleListScroll,
            onRefresh: {
                homeViewModel.getNewFeeds(isFirstLaunch: false)
            },
            updateReadStatus: { index in
                homeViewModel.updateReadStatus(lastVisibleIndex: index)
            },
            onMarkAllReadClick: {
                homeViewModel.markAllRead()
            },
            onDeleteOldFeedClick: {
                homeViewModel.deleteOldFeedItems()
            },
            onForceRefreshClick: {
                homeViewModel.forceFeedRefresh()
            },
            deleteAllFeeds: {
                homeViewModel.deleteAllFeeds()
            },
            requestNewPage: {
                homeViewModel.requestNewFeedsPage()
            }
        )
        .task {
            do {
                let stream = asyncSequence(for: homeViewModel.loadingStateFlow)
                for try await state in stream {
                    let isLoading = state.isLoading() && state.totalFeedCount != 0
                    withAnimation {
                        self.showLoading = isLoading
                    }
                    self.indexHolder.isLoading = isLoading
                    self.loadingState = state
                }
            } catch {
                self.appState.emitGenericError()
            }
        }
        .task {
            do {
                let stream = asyncSequence(for: homeViewModel.errorState)
                for try await state in stream {
                    if let message = state?.message {
                        self.appState.snackbarQueue.append(
                            SnackbarData(
                                title: message.localized(),
                                subtitle: nil,
                                showBanner: true
                            )
                        )
                    }
                }
            } catch {
                self.appState.emitGenericError()
            }
        }
        .task {
            do {
                let stream = asyncSequence(for: homeViewModel.feedStateFlow)
                for try await state in stream {
                    self.feedState = state
                }
            } catch {
                self.appState.emitGenericError()
            }
        }
        .task {
            do {
                let stream = asyncSequence(for: homeViewModel.unreadCountFlow)
                for try await state in stream {
                    self.unreadCount = Int(truncating: state)
                }
            } catch {
                self.appState.emitGenericError()
            }
        }
        .onChange(of: scenePhase) { newScenePhase in
            switch newScenePhase {
            case .background:
                homeViewModel.updateReadStatus(lastVisibleIndex: Int32(indexHolder.getLastReadIndex()))
            default:
                break
            }
        }
    }
}

struct HomeContent: View {

    @EnvironmentObject
    private var indexHolder: HomeListIndexHolder

    @EnvironmentObject
    private var browserSelector: BrowserSelector

    @EnvironmentObject
    private var appState: AppState

    @Environment(\.dismiss)
    private var dismiss

    @Environment(\.openURL)
    private var openURL

    @Binding
    var loadingState: FeedUpdateStatus?

    @Binding
    var feedState: [FeedItem]

    @Binding
    var showLoading: Bool

    @Binding
    var unreadCount: Int

    @Binding
    var sheetToShow: HomeSheetToShow?

    @Binding
    var toggleListScroll: Bool

    let onRefresh: () -> Void
    let updateReadStatus: (Int32) -> Void
    let onMarkAllReadClick: () -> Void
    let onDeleteOldFeedClick: () -> Void
    let onForceRefreshClick: () -> Void
    let deleteAllFeeds: () -> Void
    let requestNewPage: () -> Void

    var body: some View {
        ScrollViewReader { proxy in
            FeedListView(
                loadingState: loadingState,
                feedState: feedState,
                showLoading: showLoading,
                onReloadClick: {
                    onRefresh()
                },
                onAddFeedClick: {
                    self.sheetToShow = .feedList
                },
                requestNewPage: requestNewPage
            )
            .onChange(of: toggleListScroll) { _ in
                proxy.scrollTo(feedState.first?.id)
            }
            .if(appState.sizeClass == .compact) { view in
                view
                    .navigationBarBackButtonHidden(true)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(id: UUID().uuidString, placement: .navigationBarLeading, showsByDefault: true) {
                    HStack {
                        if appState.sizeClass == .compact {
                            Button(
                                action: {
                                    self.dismiss()
                                },
                                label: {
                                    Image(systemName: "sidebar.left")
                                }
                            )
                        }

                        Text("\(localizer.app_name.localized) (\(unreadCount))")
                            .font(.title2)
                            .padding(.vertical, Spacing.medium)
                            .onTapGesture(count: 2) {
                                onRefresh()
                                proxy.scrollTo(feedState.first?.id)
                                updateReadStatus(Int32(indexHolder.getLastReadIndex()))
                                self.indexHolder.refresh()
                            }
                    }
                }

                ToolbarItem(id: UUID().uuidString, placement: .primaryAction, showsByDefault: true) {
                    Menu {
                        Button(
                            action: {
                                onMarkAllReadClick()
                            },
                            label: {
                                Label(
                                    localizer.mark_all_read_button.localized,
                                    systemImage: "checkmark"
                                )
                            }
                        )

                        Button(
                            action: {
                                onDeleteOldFeedClick()
                            },
                            label: {
                                Label(
                                    localizer.clear_old_articles_button.localized,
                                    systemImage: "trash"
                                )
                            }
                        )

                        Button(
                            action: {
                                proxy.scrollTo(feedState.first?.id)
                                onForceRefreshClick()
                            },
                            label: {
                                Label(
                                    localizer.force_feed_refresh.localized,
                                    systemImage: "arrow.clockwise"
                                )
                            }
                        )

                        Button(
                            action: {
                                self.sheetToShow = .feedList
                            },
                            label: {
                                Label(
                                    localizer.feeds_title.localized,
                                    systemImage: "list.bullet.rectangle.portrait"
                                )
                            }
                        )

                        Menu {
                            Picker(
                                selection: $browserSelector.selectedBrowser,
                                label: Text(localizer.browser_selection_button.localized)
                            ) {
                                ForEach(browserSelector.browsers, id: \.self) { period in
                                    Text(period.name).tag(period as Browser?)
                                }
                            }
                        }
                    label: {
                        Label(
                            localizer.browser_selection_button.localized,
                            systemImage: "globe"
                        )
                    }

                        NavigationLink(value: CommonRoute.importExportScreen) {
                            Label(
                                localizer.import_export_opml.localized,
                                systemImage: "arrow.up.arrow.down"
                            )
                        }

                        Button(
                            action: {
                                let subject = localizer.issue_content_title.localized
                                let content = localizer.issue_content_template.localized

                                if let url = URL(
                                    string: UserFeedbackReporter.shared.getEmailUrl(
                                        subject: subject,
                                        content: content
                                    )
                                ) {
                                    self.openURL(url)
                                }
                            },
                            label: {
                                Label(
                                    localizer.report_issue_button.localized,
                                    systemImage: "ladybug"
                                )
                            }
                        )

                        #if DEBUG
                        Button(
                            action: {
                                deleteAllFeeds()
                            },
                            label: {
                                Label(
                                    "Delete Database",
                                    systemImage: "trash"
                                )
                            }
                        )
                        #endif

                        NavigationLink(value: CommonRoute.aboutScreen) {
                            Label(
                                localizer.about_button.localized,
                                systemImage: "info.circle"
                            )
                        }
                    } label: {
                        Image(systemName: "gear")
                    }
                }

            }

        }
        .sheet(item: $sheetToShow) { item in
            switch item {
            case .feedList:
                FeedSourceListScreen()
            }
        }
    }
}

struct HomeContentLoading_Previews: PreviewProvider {
    static var previews: some View {
        HomeContent(
            loadingState: .constant(
                InProgressFeedUpdateStatus(
                    refreshedFeedCount: Int32(10),
                    totalFeedCount: Int32(42)
                )
            ),
            feedState: .constant(PreviewItemsKt.feedItemsForPreview),
            showLoading: .constant(true),
            unreadCount: .constant(42),
            sheetToShow: .constant(nil),
            toggleListScroll: .constant(false),
            onRefresh: { },
            updateReadStatus: { _ in },
            onMarkAllReadClick: { },
            onDeleteOldFeedClick: { },
            onForceRefreshClick: {},
            deleteAllFeeds: {},
            requestNewPage: {}
        )
        .environmentObject(HomeListIndexHolder())
        .environmentObject(AppState())
        .environmentObject(BrowserSelector())
    }
}

struct HomeContentLoaded_Previews: PreviewProvider {
    static var previews: some View {
        HomeContent(
            loadingState: .constant(
                FinishedFeedUpdateStatus()
            ),
            feedState: .constant(PreviewItemsKt.feedItemsForPreview),
            showLoading: .constant(false),
            unreadCount: .constant(42),
            sheetToShow: .constant(nil),
            toggleListScroll: .constant(false),
            onRefresh: { },
            updateReadStatus: { _ in },
            onMarkAllReadClick: { },
            onDeleteOldFeedClick: { },
            onForceRefreshClick: {},
            deleteAllFeeds: {},
            requestNewPage: {}
        )
        .environmentObject(HomeListIndexHolder())
        .environmentObject(AppState())
        .environmentObject(BrowserSelector())
    }
}

struct HomeContentSettings_Previews: PreviewProvider {
    static var previews: some View {
        HomeContent(
            loadingState: .constant(
                FinishedFeedUpdateStatus()
            ),
            feedState: .constant(PreviewItemsKt.feedItemsForPreview),
            showLoading: .constant(false),
            unreadCount: .constant(42),
            sheetToShow: .constant(HomeSheetToShow.feedList),
            toggleListScroll: .constant(false),
            onRefresh: { },
            updateReadStatus: { _ in },
            onMarkAllReadClick: { },
            onDeleteOldFeedClick: { },
            onForceRefreshClick: {},
            deleteAllFeeds: {},
            requestNewPage: {}
        )
        .environmentObject(HomeListIndexHolder())
        .environmentObject(AppState())
        .environmentObject(BrowserSelector())
    }
}
// swiftlint:enable file_length
