# Ola VT · registro de decisiones

## Del usuario (2026-10-13, AskUserQuestion)
- **U1. Alcance de la 1.ª entrega:** el núcleo, más la barra calibrada con disco (m/s reales), el análisis de vídeos de la galería y las tendencias en el historial (incluido comparar dos vídeos).
  - El núcleo es el análisis al terminar la serie: reps, TUT/tempo, ROM, trayectoria, pérdida de velocidad y «Usar/Corregir N reps».
  - El contador en vivo con voz queda para la fase 2.
- **U2.** Escriben los subagentes **Sonnet 5.5**. El orquestador orienta, revisa, prueba e integra.
- **U3. Verdad de campo:** vídeos públicos con licencia, con permiso explícito para CADA descarga (archivo, origen y tamaño).
- **U4. Modelos de MediaPipe:** autoriza descargar `pose_landmarker_full.task` (9 398 198 B) y `pose_landmarker_heavy.task` (30 664 242 B) desde storage.googleapis.com (`float16/1`). Ver `MODELS.md`.
- **U5.** Plan aprobado: `C:\Users\valen\.claude\plans\iridescent-enchanting-dragonfly.md`.

## Del orquestador (con su razón)
| # | Decisión | Por qué |
|---|---|---|
| D1 | MediaPipe Pose Landmarker full (GPU, con CPU de respaldo) en lugar de ML Kit Pose. ML Kit pasa a `debugImplementation`, solo para el banco | ML Kit está en beta, sin cambios en Android desde ago-2024 y con términos propios. MediaPipe es Apache-2.0, tiene modo VIDEO, puntos «world» y es más preciso (PCK@0,2: 96,6 % frente a ~93,8 %) |
| D2 | Análisis offline, al terminar la serie | Lo pidió el usuario (U1); en el móvil es lo robusto |
| D3 | Las reps detectadas nunca se aplican solas: siempre «Usar» o «Corregir» | Son una medición de lo que hizo la persona, no una prescripción, y el análisis llega casi siempre con la serie ya registrada |
| D4 | Con barra (barra, smith, hexagonal, safety, T), la señal principal es el disco, aunque no esté calibrado | De lado, el disco tapa la mano en banca, press, el arranque del peso muerto y el hip thrust |
| D5 | m/s solo con disco calibrado y con «≈» hasta validarlos en el Z Flip5 con un desplazamiento conocido. Sin disco, solo pérdida de velocidad en % | Los vídeos públicos no validan m/s; honestidad de datos |
| D6 | 1RM por velocidad solo en tendencias, como «orientativo · beta»; no alimenta la sugerencia de carga | El 1RM por perfil carga-velocidad es poco fiable en peso libre (Banyard 2017, Hughes 2019) |
| D7 | ROM % para `trackRom` solo con referencia personal (≥ 2 sesiones analizadas); si no, grados | El % necesita una referencia de la propia persona |
| D8 | CameraX 1.5.3 (pila Camera2), con Media3 en 1.5.1 | La 1.6.x arrastra Media3 1.9 y estrena CameraPipe; sin teléfono para probar, es el menor riesgo |
| D9 | `<id>.analysis.json` es la fuente de verdad y la tabla `video_analysis` es caché (CACHE en las copias) | La restauración de copias borra `workout_media`, y la cascada borraría los análisis |
| D10 | Comparar en v1 = deslizador sincronizado en la rep 1 + trayectorias superpuestas; la reproducción doble va «lo mejor posible» | Valor máximo con poco riesgo |
| D11 | Los vídeos anteriores solo se analizan si se pide desde el hub | Batería y respeto |
| D12 | Ejercicios propios: el perfil se elige con un toque; el nombre nunca decide | `ExercisePatternDetector` falla con varios nombres reales |
| D13 | Grabador nuevo a pantalla completa (`TechniqueRecorderScreen`) con cambios mínimos en `SetCardExerciseMediaBack` | Evita choques con LS2b/LS7, que montan el reverso tal cual en la hoja «Técnica» |
| D14 | Modelo heavy fuera del repo: se empuja al dispositivo solo para el banco | 30 MB de historia de git para algo de depuración no compensan |
| D15 | Un solo agente a la vez mientras el disco tenga < 20 GB libres, con un único worktree `C:\kw\vt-a` reutilizado | Hoy hay 16 GB y otra ola usa las ranuras de Gradle |
| D16 | Room v29 en un archivo propio (`VideoAnalysisMigration.kt`), renumerable | La ola LS confirmó que no usa migraciones, pero se deja preparado |
| D17 | ROM absoluto solo en las vistas de métrica del perfil (RAISE en cualquiera). En oblicuo no hay ROM en v1, sin el respaldo con puntos «world» | Los puntos «world» no tienen profundidad métrica fiable; se reevalúa con el banco dorado (VT10) |
| D18 | `ENGINE_VERSION` sigue en 1 hasta la primera entrega con la ola | Aún no hay análisis publicados que recalcular |
| D19 | VT1, VT2 y VT3 se parten en a/b | Cada paquete queda dentro del tope de 3 iteraciones; la cola es secuencial de todos modos (un agente a la vez) |
| D20 | Arnés JVM `C:\kw\vt-jvm` (fuera del repo, sin red) para iterar los paquetes de dominio. `domain/biomechanics` queda aislado: no importa otros paquetes `domain` ni `data` | La cola de Gradle compartida tarda decenas de minutos por corrida, y el dominio puro se prueba en ~1 min. Se mantiene una verificación oficial por paquete |
| D21 | El resolver no llama a `ExercisePatternDetector`: recibe `guessedMovementPatternId` en `ExerciseRef` desde el data layer | Aislamiento del módulo de biomecánica (D20) |
| D22 | Los ajustes de técnica en vídeo viven en el propio hub («Grabación y análisis»), no en una pantalla de ajustes nueva | No existe pantalla de ajustes de Entreno (`SettingsTraining` está declarada pero sin registrar) y todo lo de vídeo queda en un solo sitio |
| D23 | Orden del bloque 9: primero tendencias y comparar (VT9a) y después el hub, las rutas y las entradas (VT9b) | Así el cableado final encuentra todas las pantallas hechas, sin pantallas a medias en producción |
| D24 | Banco dorado v1 con conteos y tiempos de giro a ~0,25 s, etiquetados con hojas de contacto y una segunda medida (flujo óptico) | Etiquetar fotograma a fotograma 48 vídeos no compensa (memoria «evitar sumideros»); la precisión de 50 ms se valida en el teléfono |
