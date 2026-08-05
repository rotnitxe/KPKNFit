Corpus de replay de voz (opcional).

Copiar aquí archivos `*.jsonl` de `KPKN/voice` del teléfono (o pasar `-Dvoice.replay.dir=...`)
para que `WorkoutVoiceReplayTest` re-corra los dictados reales que sí funcionaron en el
campo contra el parser actual y detecte regresiones de vocabulario/gramática.

Formato: JSONL de WorkoutVoiceDiagnosticLogger (eventos `asr_final`, `command_parsed`).
Los datos son privados del usuario: no commitear corpus reales aquí.
