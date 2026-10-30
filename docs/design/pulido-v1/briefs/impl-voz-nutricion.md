# Arreglo K3 · el micrófono de «Registrar comida» (20): que dicte de verdad
Lee antes `briefs/impl-COMUN.md`.

Palabras del usuario: «20. Para registrar comida, hay un boton de voz que no funciona, está ahí de relleno, haz que ese modo para dictar funcione realmente.»
Captura de hoy: `docs/design/pulido-v1/hoy/h19-voz-comida.jpg`: en «Registrar comida → Describir», el micrófono (círculo bajo el campo «¿Qué comiste? Escríbelo como lo
contarías») y, tras pulsarlo, un aviso «No te entendí. Prueba otra vez o escríbelo.».
Código: `screens/nutrition/design/logger/LoggerDictation.kt`, `data/voice/VoiceNutritionRecognizer.kt`, el campo y la hoja del registro en `screens/nutrition/design/logger/`
(`LoggerSheetParts.kt` y vecinos), `screens/nutrition/components/FoodLoggerViewModel.kt`, y como referencia de reconocimiento que SÍ funciona: `services/workout/WorkoutNativeOneShotRecognizer.kt`,
`WorkoutNativeOnDevicePolicy.kt`, `WorkoutVoicePermissionHelper.kt` (la voz de la sesión en vivo), `screens/workout/WorkoutVoiceInput.kt`. Revisa también `AndroidManifest.xml`
(`RECORD_AUDIO`, `<queries>` para `android.speech.RecognitionService` — sin esa `<queries>` en Android 11+ `SpeechRecognizer.isRecognitionAvailable()` puede devolver false).

Diagnóstico obligatorio ANTES de editar (escríbelo en el informe): por qué sale «No te entendí» al pulsar — ¿permiso de micrófono no solicitado o denegado?, ¿`isRecognitionAvailable` falso?,
¿se crea el reconocedor fuera del hilo principal?, ¿el idioma?, ¿se descarta el resultado parcial y se marca error por `ERROR_NO_MATCH`/`ERROR_SPEECH_TIMEOUT` al instante?, ¿el callback no
está cableado al texto del campo?, ¿está la función detrás de una bandera apagada? Sigue el hilo desde el clic del botón hasta el campo.

Hecho esperado (comportamiento real, no maqueta):
1. Pulsar el micrófono: si falta el permiso `RECORD_AUDIO`, se pide (y si está denegado de forma permanente, se explica con una vía a los ajustes del sistema); si no hay servicio de
   reconocimiento, aviso honesto y salida.
2. Estado «escuchando» visible (el botón cambia, onda o pulso con el nivel de voz si el reconocedor lo da: `onRmsChanged`), **transcripción parcial en vivo** escrita en el campo, botón para parar.
3. Resultado final: el texto queda en el campo (se concatena a lo que ya hubiera) y se lanza el mismo análisis que al pulsar «Realiz./Interpretar» con el teclado — no una ruta nueva.
4. Errores con mensaje útil por tipo (sin voz detectada → «No te oí, prueba otra vez o escríbelo»; sin red/servicio; ocupado), sin quedarse colgado en «escuchando»; liberar el reconocedor en
   `onDispose`/al salir/al rotar; sin fugas.
5. Idioma: español del dispositivo (`es-ES`/`es-419` según `Locale`), preferir el reconocedor del dispositivo si está disponible, igual que hace la voz de entreno (sigue su política).
Ya existe `data/voice/VoiceNutritionRecognizer.kt` y `LoggerDictation.kt`: reutilízalos y corrígelos; no crees un tercer reconocedor.
Archivos permitidos: los listados, `AndroidManifest.xml` (solo `<uses-permission>`/`<queries>` si faltan), `strings`, y tests JVM del área. NO toques las pantallas de la sesión en vivo.
Un diseño completo de todos los estados visibles (escuchando, permiso, error, sin servicio) lo prepara otro agente en `docs/design/pulido-v1` para aprobarse después: tú deja los estados
mínimos funcionales con el estilo vecino.
