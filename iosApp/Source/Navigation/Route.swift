//
//  Route.swift
//  FeedFlow
//
//  Created by Marco Gomiero on 27/03/23.
//  Copyright © 2023 FeedFlow. All rights reserved.
//

import Foundation

enum CommonRoute: Hashable {
    case aboutScreen
    case importExportScreen
}

enum CompactViewRoute: Hashable {
    case feed
}

// TODO: new stuff

public enum SheetDestination: Identifiable {
    case settings

    public var id: String {
        switch self {
        case .settings:
            return "settings"
        }
    }
}

// class RouterPath: ObservableObject {
//
//    @Published
//    var path: [RouterDestination] = []
//
//    @Published
//    var presentedSheet: SheetDestination?
//
//    public init() {}
//
//    func navigate(to: RouterDestination) {
//        path.append(to)
//    }
// }
