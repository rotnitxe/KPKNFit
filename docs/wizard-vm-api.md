# Setup Wizard VM API

## Draft persistido

`SetupWizardDraft` mantiene en el borrador serializable del asistente:

- `volumeRecommendations` y `athleteProfileScore`, ambos vacíos o nulos hasta guardar una calibración real.
- `powerliftingProfile`, opcional y reservado para programas de fuente `PROTOCOL`.
- Los nuevos perfiles de equipo `GYM`, `SUPPORT`, `BALL` y `SMITH`; `NONE` conserva el identificador `bodyweight`.

No se guardan estos datos en Settings ni en Room fuera del payload de borrador y del programa materializado al confirmar.

## Materialización

- Los programas `NATIVE` usan `PersonalizerInput.volumeRecommendations`. La calibración es `CALIBRATED` únicamente cuando existen recomendaciones y puntuación; en otro caso el motor recibe calibración conservadora sin recomendaciones libres.
- Al materializar `NATIVE`, las recomendaciones y la puntuación se copian al `Program`.
- Los programas `PROTOCOL` respetan la receta original. Si existe un `powerliftingProfile`, se copia al programa base; no se inventan RM ni se ejecuta una segunda calibración de volumen.
- `TrainingMaxWizard` es opcional y permite dejar los RM para después.
- Las recetas fijas se validan contra la frecuencia real de sus sesiones. Una incompatibilidad produce error de preview y no modifica la receta para forzar los días solicitados.

## UI

En la semana, un plan `NATIVE` muestra `Ajustar mi volumen` y usa `VolumeCalibrationSheet`. Un plan `PROTOCOL` muestra el botón opcional `Definir Training Max` y explica que los RM pueden completarse posteriormente.
