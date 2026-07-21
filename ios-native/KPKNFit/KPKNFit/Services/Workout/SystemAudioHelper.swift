import Foundation
import AVFoundation
import UIKit

public struct TransientDuckHandle {
    let audioSession: AVAudioSession
    init(audioSession: AVAudioSession = .sharedInstance()) {
        self.audioSession = audioSession
    }
}

public enum SystemAudioHelper {

    public static func getRingerModeVolume() -> Float {
        switch getRingerMode() {
        case .silent: return 0
        case .vibrate: return 0
        case .normal: return 0.6
        }
    }

    public enum RingerMode { case silent, vibrate, normal }

    public static func getRingerMode() -> RingerMode {
        let session = AVAudioSession.sharedInstance()
        if session.secondaryAudioShouldBeSilencedHint { return .silent }
        if session.outputVolume == 0 { return .silent }
        return .normal
    }

    public static func isNormalRinger() -> Bool {
        getRingerMode() == .normal
    }

    public static func shouldPlaySound(soundsEnabled: Bool) -> Bool {
        soundsEnabled && isNormalRinger()
    }

    public static func shouldVibrate(hapticEnabled: Bool) -> Bool {
        hapticEnabled && getRingerMode() != .silent
    }

    @discardableResult
    public static func requestTransientDuckFocus() -> TransientDuckHandle? {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default, options: [.duckOthers, .mixWithOthers])
            try session.setActive(true)
            return TransientDuckHandle(audioSession: session)
        } catch {
            return nil
        }
    }

    public static func abandonTransientDuckFocus(_ handle: TransientDuckHandle?) {
        guard let h = handle else { return }
        do {
            try h.audioSession.setActive(false, options: .notifyOthersOnDeactivation)
        } catch {}
    }

    @discardableResult
    public static func requestTransientDuckForVoice() -> TransientDuckHandle? {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .voiceChat, options: [.duckOthers, .mixWithOthers])
            try session.setActive(true)
            return TransientDuckHandle(audioSession: session)
        } catch {
            return nil
        }
    }
}
