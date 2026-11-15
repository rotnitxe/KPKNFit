# Handoff para auditoría — KPKNFit Android

## Entrega

- Commit de código: `2ad50d96fdfb28cb828281a89d104e881435edaa` (`fix: close Android UI remediation regressions`), hijo de `41edea0eb` en `master`.
- APK: [app-baseRelease-2ad50d96f.apk](../app-baseRelease-2ad50d96f.apk), SHA-256 `0DFA793AAB19D43B632B3BBB6C78A2DF104FACFD7737EF73643D737B8B83EDB8`.
- Certificado v2/v3 válido: `9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1`; paquete `com.example.kpkn`, versión 55 / `KPKN Pre Release 21`, Room 38.
- Build Debug y Release exitoso en el commit de código; resultado, firma, mapping y límite de instalación en [release-build-receipt.md](release-build-receipt.md).
- iOS queda fuera del alcance por instrucción del usuario.

## Fase y aceptación

- Fase alcanzada: correcciones Android implementadas y pruebas focalizadas ejecutadas; build/firma/copia canónica completados. La aceptación del plan sigue parcial.
- `acceptance-matrix.md` contiene las 13 filas y las regresiones adicionales: requisito 3 aprobado; otros 12 parciales. No presentar 121 pruebas de componentes como 121 recorridos de producto.
- Suites JVM dirigidas: 264 tests aprobados en 32 suites únicas; cierre adicional `EntrenoPlanStepTest` 20/20 y `ProgramStructureSheetsTest` 8/8. Suite Base completa: timeout histórico de 45 minutos, no aprobada.
- AVD: 121/121 en 30 suites seleccionadas, fijadas al serial autorizado `emulator-5580` y al paquete de pruebas `com.example.kpkn.dbg`. Los logs y la reconciliación están en [avd-validation-receipt.md](avd-validation-receipt.md).
- El APK Release final está instalado con `-r` en `emulator-5580`; su SHA remoto coincide con el artefacto y `MainActivity` quedó reanudada. Se hizo un reboot normal del AVD para recuperar Package Manager, sin `-wipe-data`; no se ejecutó `pm clear` ni se inspeccionó la base de datos. Z Flip físico no se usó.

## Cambios finales del commit fuente

- `EntrenoPlanStep`: el perfil general presenta las alternativas a medida en carrusel, conserva recomendación/selección y permite solicitar otra versión; el catálogo queda expandible.
- Hojas de ciclos: etiquetas visibles traducidas a español; se añadió `ProgramCyclesSheetDeviceTest`.
- `HomeRetiredSessionCueUiTest` y la cobertura de hojas live se ajustaron a recorridos/componentes actuales; correcciones de semántica de sesión y observaciones se cubren en las suites existentes.
- Los cambios de código y sus pruebas están en `2ad50d96f`; los recibos y el log reproducible acompañan la publicación. El APK binario queda como artefacto local canónico y no se añade al historial Git.

## Verificaciones y pendientes

- Verificar el SHA final de `origin/master` tras la publicación; la respuesta de entrega da ese SHA exacto. El commit fuente del APK es `2ad50d96f`.
- Ejecutar los 12 recorridos Android todavía parciales de la matriz, incluidos cargas/derivados, reanudación/recurrencia, edición permanente en vivo, cierre, progreso, editor, portadas, menús, recetas, onboarding, check-in nutricional e importador/modo visual.
- AVD QA10 pendiente: Owner estaba en `0`; no se creó otro usuario. La conexión ADB final no expuso el serial autorizado.
- No se tocó la app personal del Z Flip, no se accedió al teléfono y no se inspeccionaron datos personales.
- El checkout conserva cambios y archivos ajenos existentes. El stage de esta entrega se limita a matriz, recibos y log; no incluir `android-native/output`, APKs, worktrees ni otros WIP.
