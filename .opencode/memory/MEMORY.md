KPKN is local-first and private; Room is the source of truth on-device.
§
The Android product is under android-native/ and uses Kotlin, Compose, Clean Architecture, MVVM, manual DI, and StateFlow.
§
AUGE changes must preserve behavior across Android, iOS, and backend implementations.
§
The voice subsystem under android-native/app/src/main/java/com/example/kpkn/services/workout/ is large and active; use focused tests and diagnostics before broad refactors.
