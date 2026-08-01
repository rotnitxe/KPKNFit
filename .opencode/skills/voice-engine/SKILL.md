---
name: voice-engine
description: Debug KPKN offline Spanish workout voice flows
---

# Voice Engine

## When To Use

Use for Vosk, TTS, AIDL, foreground-service, microphone routing, command parsing, or session-gate changes.

## Procedure

1. Trace the flow through `services/workout/`, `data/voice/`, the `:voice` process, and AIDL interfaces.
2. Preserve Spanish intent matching, confirmation policy, fallback behavior, and diagnostic JSONL output.
3. Run focused voice unit tests before changing shared workout state.
4. Check process boundaries, lifecycle cancellation, audio focus, and permission behavior.

## Pitfalls

This subsystem is active and fragile. Avoid broad refactors and do not regenerate the Vosk model or large assets manually.
