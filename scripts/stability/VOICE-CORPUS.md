# Entrada privada de corpus de campo

El gate de publicación requiere las dos pruebas reales de `WorkoutVoiceReplayTest` ejecutadas. Las pruebas sintéticas de validación de entrada no sustituyen el corpus ni una prueba de reconocimiento de audio.

Un corpus suministrado y autorizado debe contener `manifest.json` y archivos JSONL planos, sin enlaces. El manifiesto declara `schemaVersion: 1`, `kind: REAL_FIELD_SANITIZED`, `consentForCi: true` y una lista `files` con `path`, `bytes` y `sha256` de cada JSONL. La declaración de procedencia y consentimiento debe corresponder a evidencias reales del propietario; el validador solo comprueba estructura, límites e integridad.

Los registros compatibles incluyen `asr_final` con texto `sanitized` y los `command_parsed` correspondientes, con contexto `unitMode`/`isUnilateral`. Se deben retirar nombres, identificadores, cuentas y otro contenido personal antes de preparar la entrada. No usar diagnósticos de producción para recopilar nuevos dictados sin consentimiento.

Para ejecución local, `prepare_voice_corpus.py --source <carpeta-autorizada> --output .ci-private/voice-corpus` valida la entrada sin imprimir dictados. Desde `android-native/`, pasar `-PKPKN_VOICE_REPLAY_DIR=<ruta-absoluta-a-.ci-private/voice-corpus>` a `testDebugUnitTest`. El directorio privado está excluido de Git; los informes de fallos de replay identifican muestras por hash.

En CI manual, la entrada `voice_corpus_run_id` identifica un run del mismo repositorio con un artifact privado llamado `kpkn-voice-field-corpus`. El workflow lo lee con permiso `actions: read`, lo valida y entrega la ruta a Gradle. No lo vuelve a publicar en los artifacts de resultados. No se ha cargado ningún corpus ni ejecutado esa CI remota en esta entrega. Sin entrada real, el gate queda incompleto y bloquea release.
