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

## Límite de instalación

- El APK final no se instaló en el AVD. El proceso del AVD seguía activo, pero el serial autorizado `emulator-5580` no apareció ni en el ADB aislado `15045` recién reiniciado ni en el servidor ya activo `5037`. No se ejecutaron comandos de paquete/datos ni se borró o reinició el AVD.
- Las pruebas instrumentadas seleccionadas corrieron en `com.example.kpkn.dbg`; no se atribuyen al `baseRelease`. Detalle y límites en [avd-validation-receipt.md](avd-validation-receipt.md).
- Suite JVM Base completa y aceptación integral de 13 recorridos: siguen abiertas; estados por requisito en [acceptance-matrix.md](acceptance-matrix.md).
