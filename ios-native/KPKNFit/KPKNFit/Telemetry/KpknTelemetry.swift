import Foundation

class KpknTelemetry {

    static let shared = KpknTelemetry()

    private init() {}

    func logEvent(eventName: String, params: [String: Any] = [:]) {}

    func setUserProperty(name: String, value: String?) {}

    func logException(_ exception: Error, fatal: Bool = false) {}

    func logError(message: String, throwable: Error? = nil) {}

    func startTrace(name: String) -> Trace {
        Trace(name: name)
    }

    func setCrashlyticsEnabled(_ enabled: Bool) {}

    func setUserId(_ userId: String?) {}

    class Trace {
        private let name: String
        private let startTime: TimeInterval

        init(name: String) {
            self.name = name
            self.startTime = ProcessInfo.processInfo.systemUptime
        }

        func stop() {
            let duration = ProcessInfo.processInfo.systemUptime - startTime
        }

        func start() {}
        func incrementMetric(_ metric: String, value: Int = 1) {}
        func putAttribute(key: String, value: String) {}
    }
}

func logKpknEvent(eventName: String, params: [String: Any] = [:]) {
    KpknTelemetry.shared.logEvent(eventName: eventName, params: params)
}

func logKpknError(message: String, throwable: Error? = nil) {
    KpknTelemetry.shared.logError(message: message, throwable: throwable)
}

func logKpknException(_ exception: Error, fatal: Bool = false) {
    KpknTelemetry.shared.logException(exception, fatal: fatal)
}
