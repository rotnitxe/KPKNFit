package com.example.kpkn.services.workout;

import com.example.kpkn.services.workout.IWorkoutVoiceEngineCallback;

interface IWorkoutVoiceEngineService {
    void registerCallback(IWorkoutVoiceEngineCallback callback, long generation);
    void start(long generation, boolean holdMicRouteAcrossPause, String grammarJson, int stageOrdinal, int noiseProfileOrdinal, int captureModeOrdinal);
    void updateCaptureMode(long generation, int captureModeOrdinal);
    boolean pause(long generation, boolean releaseMic);
    void resume(long generation, long delayMs);
    void updateGrammar(long generation, String grammarJson, int stageOrdinal);
    boolean stop(long generation);
    boolean requestNativeFallback(long generation, String transcript);
    void completePrompt(long generation, long requestId);
}
