# Cobertura funcional del contrato JSONL v2

| Área | Eventos mínimos cubiertos | Productor principal |
|---|---|---|
| `voice` | ASR, fases, fallback, reportes, `user_comment` | `WorkoutVoiceDiagnosticLogger` / `WorkoutVoiceController` |
| `workout` | `session_started`, `set_recorded`, `set_record_failed`, `session_finished`, `session_abandoned` | `WorkoutViewModel` / `WorkoutFinishController` |
| `nutrition` | sesiones, trazas de análisis, divergencias y crashes | `NutritionTelemetry` |
| `performance` | trazas, estado de proceso y `logs_health_check` | `KpknTelemetry` / `KpknDiagnosticLogger` |
| `auge` | recomputación de baterías y estado derivado | `AugeViewModel` |
| `reports` | creación, contexto, pendiente, enriquecimiento y fallo | `KpknReportManager` |

## Gaps que requieren aceptación en hardware

- El contrato y las máquinas de estado tienen pruebas JVM.
- La secuencia de gesto debe verificarse 10/10 en las tres pantallas objetivo.
- La ruta de voz `CAUPOLICÁN` necesita replay y prueba en teléfono físico,
  incluyendo pantalla bloqueada y la ruta OEM de `SpeechRecognizer`.
- La detección de selectores en el entrenamiento se valida por `adb` observando
  que no aparezca una Activity de documentos; el selector manual de Ajustes sí
  es intencional.
