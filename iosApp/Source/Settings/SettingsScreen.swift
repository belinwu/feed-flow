//
//  SettingsScreen.swift
//  FeedFlow
//
//  Created by Marco Gomiero on 05/01/24.
//  Copyright © 2024. All rights reserved.
//

import SwiftUI
import shared
import KMPNativeCoroutinesAsync

struct SettingsScreen: View {

    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var browserSelector: BrowserSelector

    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    @StateObject
    private var settingsViewModel: SettingsViewModel = KotlinDependencies.shared.getSettingsViewModel()

    @State private var isMarkReadWhenScrollingEnabled = true
    @State private var isShowReadItemEnabled = false

    var body: some View {
        settingsContent
            .task {
                do {
                    let stream = asyncSequence(for: settingsViewModel.settingsStateFlow)
                    for try await state in stream {
                        self.isMarkReadWhenScrollingEnabled = state.isMarkReadWhenScrollingEnabled
                        self.isShowReadItemEnabled = state.isShowReadItemsEnabled
                    }
                } catch {
                    self.appState.emitGenericError()
                }
            }
            .onChange(of: isMarkReadWhenScrollingEnabled) { newValue in
                settingsViewModel.updateMarkReadWhenScrolling(value: newValue)
            }
            .onChange(of: isShowReadItemEnabled) { newValue in
                settingsViewModel.updateShowReadItemsOnTimeline(value: newValue)
            }
    }

    private var settingsContent: some View {
        NavigationStack {
            Form {
                generalSection
                appSection
            }
            .scrollContentBackground(.hidden)
            .toolbar {
                Button {
                    dismiss()
                } label: {
                    Text(localizer.action_done.localized).bold()
                }
                .accessibilityIdentifier(TestingTag.shared.BACK_BUTTON)
            }
            .navigationTitle(
                Text(localizer.settings_title.localized)
            )
            .navigationBarTitleDisplayMode(.inline)
            .background(Color.secondaryBackgroundColor)
        }
    }

    @ViewBuilder
    private var generalSection: some View {
        Section(localizer.settings_general_title.localized) {
            NavigationLink(destination: FeedSourceListScreen()) {
                Label(localizer.feeds_title.localized, systemImage: "list.bullet.rectangle.portrait")
            }
            .accessibilityIdentifier(TestingTag.shared.SETTINGS_FEED_ITEM)

            NavigationLink(destination: AddFeedScreen()) {
                Label(localizer.add_feed.localized, systemImage: "plus.app")
            }

            NavigationLink(destination: ImportExportScreen()) {
                Label( localizer.import_export_opml.localized, systemImage: "arrow.up.arrow.down")
            }

            Picker(
                selection: $browserSelector.selectedBrowser,
                content: {
                    ForEach(browserSelector.browsers, id: \.self) { period in
                        Text(period.name).tag(period as Browser?)
                    }
                },
                label: {
                    Label(localizer.browser_selection_button.localized, systemImage: "globe")
                }
            )
            .accessibilityIdentifier(TestingTag.shared.BROWSER_SELECTOR)

            Toggle(isOn: $isMarkReadWhenScrollingEnabled) {
                Label(localizer.toggle_mark_read_when_scrolling.localized, systemImage: "envelope.open")
            }.onTapGesture {
                isMarkReadWhenScrollingEnabled.toggle()
            }
            .accessibilityIdentifier(TestingTag.shared.MARK_AS_READ_SCROLLING_SWITCH)

            Toggle(isOn: $isShowReadItemEnabled) {
                Label(localizer.settings_toggle_show_read_articles.localized, systemImage: "text.badge.checkmark")
            }.onTapGesture {
                isShowReadItemEnabled.toggle()
            }
        }
    }

    @ViewBuilder
    private var appSection: some View {
        Section(localizer.settings_app_title.localized) {
            Button(
                action: {
                    let subject = localizer.issue_content_title.localized
                    let content = localizer.issue_content_template.localized

                    if let url = URL(
                        string: UserFeedbackReporter.shared.getEmailUrl(subject: subject, content: content)
                    ) {
                        self.openURL(url)
                    }
                },
                label: {
                    Label(localizer.report_issue_button.localized, systemImage: "ladybug")
                }
            )

            NavigationLink(destination: AboutScreen()) {
                Label(localizer.about_button.localized, systemImage: "info.circle")
            }
            .accessibilityIdentifier(TestingTag.shared.ABOUT_SETTINGS_ITEM)
        }
    }
}

#Preview {
    SettingsScreen()
        .environmentObject(BrowserSelector())
}
