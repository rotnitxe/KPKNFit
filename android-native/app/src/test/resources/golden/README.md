# Fixtures derivadas de VT10

Universo congelado: 97 clips compatibles. Reparto por fuente: 59 ajuste / 38 reserva. Las etiquetas y atribuciones están completas; `manifest.json` indica qué extracciones están disponibles y sus hashes. Ausencia de una traza no es una aprobación.

Solo se incluyen etiquetas JSON pequeñas, pose2/bar comprimidos y atribución. Los vídeos permanecen en el banco local y en el emulador. Kaggle y UCF101 se evalúan desde una carpeta externa con `KPKN_GOLDEN_CORPUS_DIR`.

Los hashes de pose/disco verifican los bytes del sidecar; los lectores usados son `PoseTrackCodec` y `BarTrackCodec`. No se deserializa el wrapper `analyze_file` como si fuera un sidecar de producción. Los tiempos mantienen PTS absolutos del clip; el motor recibe el recorte de `activeWindowS` sin desplazar el origen temporal.

El humo nuevo usa g25-pexels-8456990, r14-pexels-4921641 y wger_0002. x14-commons-squat ya se había observado en el humo histórico: se conserva como regresión y se excluye de aceptación ciega.

Las métricas y el procedimiento de ejecución se describen en `GoldenReport.kt`. Los informes finales están en `docs/video-technique/GOLDEN_REPORT.md` y `docs/video-technique/GOLDEN_TIMING_HOLDOUT.md`, desde la raíz del repositorio.

La reserva ciega de 37 clips ya se evaluó una vez y ambos gates fallaron: conteo y tiempos. Sus controles conservan esos fallos en la ejecución normal; aprobar regresiones funcionales seleccionadas no aprueba el banco completo. El resultado final se conserva como histórico: repetir esa reserva no constituye una evaluación ciega nueva. Una mejora posterior necesita datos de ajuste/sintéticos y una evaluación independiente nueva. Las pruebas de diagnóstico de ajuste no leen la reserva.
