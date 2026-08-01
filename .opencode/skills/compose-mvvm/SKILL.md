---
name: compose-mvvm
description: Implement KPKN Compose screens with MVVM and UDF
---

# Compose MVVM

## Procedure

1. Put feature files under `screens/<feature>/` and keep reusable UI in the established components folders.
2. Route user events through the ViewModel into immutable state.
3. Expose `StateFlow` via `asStateFlow()` and keep composables stateless where practical.
4. Keep I/O and heavy work off the main thread and update navigation/deep links together when needed.

## Verification

Run the feature's unit tests, then `gradlew.bat assembleDebug` when build wiring or resources changed.
