import Foundation
import Speech
import AVFoundation
import Combine

final class WorkoutContinuousVoiceEngine: NSObject {
    private var speechRecognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let audioEngine = AVAudioEngine()
    private var active = false
    private var restarting = false

    private let _partialResults = PassthroughSubject<String, Never>()
    let partialResults: AnyPublisher<String, Never>

    private let _finalResults = PassthroughSubject<String, Never>()
    let finalResults: AnyPublisher<String, Never>

    private let _errors = PassthroughSubject<String, Never>()
    let errors: AnyPublisher<String, Never>

    var isActive: Bool { active }

    override init() {
        let locale = Locale.current.language.languageCode?.identifier == "es"
            ? Locale.current
            : Locale(identifier: "es-ES")
        speechRecognizer = SFSpeechRecognizer(locale: locale)
        partialResults = _partialResults.eraseToAnyPublisher()
        finalResults = _finalResults.eraseToAnyPublisher()
        errors = _errors.eraseToAnyPublisher()
        super.init()
        speechRecognizer?.delegate = self
    }

    func start() {
        guard !active else { return }
        active = true
        startListening()
    }

    func pause() {
        active = false
        restarting = false
        stopAudioEngine()
        cancelRecognition()
        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        } catch {}
    }

    func stop() {
        pause()
    }

    private func startListening() {
        guard active, let recognizer = speechRecognizer, recognizer.isAvailable else {
            _errors.send("Reconocimiento no disponible en este dispositivo")
            return
        }

        cancelRecognition()

        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playAndRecord, mode: .default, options: [.duckOthers, .defaultToSpeaker])
            try audioSession.setActive(true)
        } catch {
            _errors.send("Error al iniciar sesión de audio")
            return
        }

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        request.taskHint = .dictation
        recognitionRequest = request

        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
            request.append(buffer)
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
        } catch {
            _errors.send("Error al iniciar audio: \(error.localizedDescription)")
            stopAudioEngine()
            restartListeningDelayed(1000)
            return
        }

        recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
            guard let self = self else { return }

            if let error = error {
                let nsError = error as NSError
                if nsError.domain == "SFSpeechErrorDomain" {
                    if self.active {
                        self.restartListeningDelayed(400)
                    }
                    return
                }
                if self.active {
                    self._errors.send("Error de reconocimiento: \(error.localizedDescription)")
                    self.restartListeningDelayed(1000)
                }
                return
            }

            guard let result = result, self.active else { return }

            let text = result.bestTranscription.formattedString.trimmingCharacters(in: .whitespaces)
            guard !text.isEmpty else { return }

            if result.isFinal {
                self._finalResults.send(text)
                self.restartListeningDelayed(300)
            } else {
                self._partialResults.send(text)
            }
        }
    }

    private func restartListeningDelayed(_ delayMs: Int) {
        guard active, !restarting else { return }
        restarting = true
        DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(delayMs)) { [weak self] in
            guard let self = self else { return }
            self.restarting = false
            if self.active {
                self.startListening()
            }
        }
    }

    private func stopAudioEngine() {
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
    }

    private func cancelRecognition() {
        recognitionTask?.cancel()
        recognitionTask = nil
        recognitionRequest?.endAudio()
        recognitionRequest = nil
    }
}

extension WorkoutContinuousVoiceEngine: SFSpeechRecognizerDelegate {
    func speechRecognizer(_ speechRecognizer: SFSpeechRecognizer, availabilityDidChange available: Bool) {
        if !available && active {
            _errors.send("Reconocimiento de voz no disponible temporalmente")
            pause()
        }
    }
}
