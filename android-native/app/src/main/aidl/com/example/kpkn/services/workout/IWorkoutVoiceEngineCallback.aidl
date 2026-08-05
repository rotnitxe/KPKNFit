package com.example.kpkn.services.workout;

interface IWorkoutVoiceEngineCallback {
    void onPartial(long generation, String text);
    void onFinal(long generation, String hypothesesJson);
    void onError(long generation, String code, String message);
    void onStatus(long generation, String message);
    void onCaptureState(long generation, int state);
    void onRms(long generation, float rms);
    void onHeartbeat(long generation);
    void onOnDevice(long generation, boolean onDevice);
    void onRoute(long generation, String route);
    void onNativeFallback(long generation, boolean active);
    void onFallbackPaused(long generation, boolean paused);
    void onPrompt(long generation, long requestId, String text);
    void onStopped(long generation, boolean userRequested);
}
