import Foundation

internal final class AugeRepository {
    static let shared = AugeRepository()

    var postSessionFeedbacks: [PostSessionFeedback] = []

    private init() {}

    static func getInstance() -> AugeRepository {
        return shared
    }

    func getPostSessionFeedbacks() -> [PostSessionFeedback] {
        return postSessionFeedbacks
    }
}
