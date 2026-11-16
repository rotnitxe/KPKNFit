# Recibo de build y entrega — KPKNFit Android

## Revisión fuente

- Commit de código compilado: `2ad50d96fdfb28cb828281a89d104e881435edaa` (`fix: close Android UI remediation regressions`), sobre `master` local y basado en `41edea0eb`.
- Alcance de esta entrega: Android. iOS queda excluido por instrucción del usuario.
- Room de fuente: v38; `KpknDatabase.kt` declara `version = 38` y la migración `37 → 38`.

## APK final

- Archivo canónico: [app-baseRelease-2ad50d96f.apk](../app-baseRelease-2ad50d96f.apk).
- SHA-256: `0DFA793AAB19D43B632B3BBB6C78A2DF104FACFD7737EF73643D737B8B83EDB8`.
- Tamaño: `286087404` bytes.
- Paquete y versión, leídos del APK: `com.example.kpkn`, `versionCode 55`, `KPKN Pre Release 21`.
- `apksigner verify --verbose --print-certs`: firmas v2 y v3 válidas; certificado SHA-256 `9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1`.
- El output de Gradle y la copia canónica tienen el mismo SHA-256; la firma también se verificó sobre la copia.
- APK `baseDebug`: SHA-256 `0417EE65C0592A9C05233849DAFD893841EB1C307E9CFB69A7F2D2D912D256D5`, tamaño `536577469` bytes.

## Build y mapping

- Comando exitoso con el wrapper del repositorio y un worker: `assembleBaseDebug assembleBaseRelease --max-workers=1`.
- Build completo del commit fuente: `BUILD SUCCESSFUL` en `17m 39s`; 95 tareas, 13 ejecutadas y 82 up-to-date. Se hizo serialmente después de que la corrida paralela agotara heap Java.
- Log preservado de la repetición reproducible: [assemble-base-debug-release-2ad50d96f-success-20261115.log](assemble-base-debug-release-2ad50d96f-success-20261115.log), SHA-256 `2B19663296E67F3E29775298AC90E8EF661BA84BCEF62B556E250044B518B9C2`. Esa repetición terminó en 34 s con los outputs up-to-date (95 tareas, 1 ejecutada, 94 up-to-date).
- Log del primer intento paralelo fallido por `Java heap space`: [assemble-base-debug-release-2ad50d96f-20261115.log](assemble-base-debug-release-2ad50d96f-20261115.log), SHA-256 `8734ECE3BBB64AF108120ACE55B444458017A130DFF99B44B6635E2BDA0C1CB4`. El daemon propio que quedó reteniendo el lock se identificó y cerró por PID; no se terminaron otros procesos Java.
- Mapping R8, sin duplicarlo: `android-native/app/build/outputs/mapping/baseRelease/mapping.txt`, `526045845` bytes, SHA-256 `1EB93D19BA800476754543BDF4DFF74AF827723D75A5BD12A67941E75293A4B9`.

## Instalación en AVD

- El APK final se instaló con `adb install -r --no-streaming` en `emulator-5580` tras verificar que el APK anterior tenía el mismo certificado release y que ambos usan Room 38. `-r` conserva los datos; no se ejecutó `pm clear`.
- El SHA-256 instalado remoto coincide exactamente con el APK canónico; `versionCode 55`, `KPKN Pre Release 21`. `MainActivity` quedó reanudada en smoke launch.
- La recuperación ADB y los dos intentos fallidos anteriores están descritos en [avd-validation-receipt.md](avd-validation-receipt.md). No se tocó el Z Flip.
- Las suites instrumentadas seleccionadas corrieron en `com.example.kpkn.dbg`; no se atribuyen al `baseRelease`.
- Suite JVM Base completa y aceptación integral de 13 recorridos: siguen abiertas; estados por requisito en [acceptance-matrix.md](acceptance-matrix.md).

## Vigencia tras la continuación — 2026-11-16 (candidato anterior superado)

La entrega anterior `2ad50d96f` (`app-baseRelease-2ad50d96f.apk`) quedó superada por el commit final de código y su nuevo APK release firmado descritos a continuación.

## Entrega final vigente — `6bf2f5792`

- Commit final de código compilado en `master`: `6bf2f5792a72147850e919e7606c0be0bd94067e` (`fix: complete Android remediation delivery and acceptance suite`).
- Alcance de esta entrega: Android (`baseRelease` firmado). iOS queda excluido por instrucción del usuario.
- Esquema Room de fuente: v38 (`KpknDatabase.kt` declara `version = 38` y migración `37 → 38`), compatibilidad verificada en `Room37To38UpgradeDeviceTest` (`1/1`) y `RecurrenceNutrition37RecipeDeviceTest` (`3/3`).

### APK `baseRelease` final

- Archivo canónico: [app-baseRelease-6bf2f5792.apk](../app-baseRelease-6bf2f5792.apk) (mismo binario que `android-native/app/build/outputs/apk/base/release/app-base-release.apk`).
- SHA-256 (idéntico en origen, copia canónica y `base.apk` instalado en `emulator-5580`): `F10DDA01AA833D83C465236DB3F7B825B35B37520ED33D7B25CD32331BBFBD8F`.
- Tamaño: `286087403` bytes.
- Paquete y versión (`aapt2 dump badging` / `dumpsys package`): `com.example.kpkn`, `versionCode 55`, `KPKN Pre Release 21` (`minSdk 24`, `targetSdk 35`, `compileSdkVersion 36`).
- `apksigner verify --verbose --print-certs`: `Verifies`; esquemas v2 y v3 válidos; certificado SHA-256 `9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1` (`CN=KPKN Fit, OU=Mobile, O=KPKN`).
- Build con candado compartido (`run-gradle-locked.ps1`): `:app:assembleBaseRelease --max-workers=1`, **BUILD SUCCESSFUL** en `14m 16s` (`54` tareas: `10` ejecutadas, `44` up-to-date). Log: [assemble-baseRelease-6bf2f5792.log](gradle-logs/assemble-baseRelease-6bf2f5792.log), SHA-256 `563835F1F557086DECB10C47992FA0C8D3A2BCE0F828AEC16C4F16B8482F881A`.
- Mapping R8: `android-native/app/build/outputs/mapping/baseRelease/mapping.txt`, `526583695` bytes, SHA-256 `3B9EFA2888AA6CBE3FCBDA9D899B3E176E0879D1A674B8EE654C8532A60D4949`.

### Smoke launch e instalación en AVD

- Destino único autorizado: `emulator-5580` (`-P 5045 -s emulator-5580`). Antes de actualizar, la instalación previa `com.example.kpkn` (`0DFA793A…`) compartió exactamente el mismo certificado SHA-256 `9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1`.
- Actualización conservando datos mediante `adb -P 5045 -s emulator-5580 install -r --no-streaming releases/app-baseRelease-6bf2f5792.apk` → `Success`; sin `pm clear`, sin tocar Z Flip físico ni la app personal.
- Verificación remota tras instalar (`pm path com.example.kpkn` → `sha256sum`): `F10DDA01AA833D83C465236DB3F7B825B35B37520ED33D7B25CD32331BBFBD8F` (`SHA_MATCH=True`).
- Arranque en frío tras `am force-stop`: `am start -W -n com.example.kpkn/.MainActivity` → `Status: ok`, `LaunchState: COLD`, `TotalTime: 1724 ms`, `PID: 6966`, `topResumedActivity`: `com.example.kpkn/.MainActivity`, `CRASH_LINES=0` en logcat. Recibo: [avd-release-smoke-6bf2f5792.log](avd-release-smoke-6bf2f5792.log).
- `baseDebug` empleado durante las corridas instrumentadas `r135`–`r141` (`com.example.kpkn.dbg`, v55): SHA-256 `8B70B6387682D5247F45C3FFBBA45F121698D34B35F764029BAFBC7C448D7DDD` (no es artefacto de entrega).
