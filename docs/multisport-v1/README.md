# Multideporte v1: carta operativa

**Actualización del usuario, 18 de octubre de 2026:** también se rechaza la galería v2. La UI se definirá después. Ninguno de esos HTML es referencia de implementación. Continúa el trabajo de motores, contratos y persistencia, conservando los flujos y la sesión en vivo existentes. El wizard no debe mostrar la tabbar; cualquier ampliación futura deberá respetar su contexto de navegación.

Este directorio fija el alcance, la secuencia y las puertas de promoción del roadmap multideporte. Es documentación de ejecución; no afirma que KPKN ya soporte las disciplinas listadas.

## Alcance y estado

La primera entrega comprende prototipos nuevos y un candidato del núcleo multideporte, empezando por el híbrido fuerza + running. El wizard HTML también demuestra fuerza + MMA y MMA solo con una clase vinculada expresamente por el usuario. Android nativo es el producto personal local y offline. Los modelos de voz y visión locales forman parte de la dirección aprobada; las funciones complejas de cloud y coaching quedan diferidas. No se debe presentar compatibilidad con Huawei Watch: no hay un modelo ni compatibilidad verificada.

El núcleo está en implementación. Las **galerías v1 y v2 fueron rechazadas por el usuario** y quedan excluidas como referencia para Kotlin. Se conservan como historial, junto con sus comprobaciones técnicas, que no acreditan aceptación visual. Las reconstrucciones de pantallas existentes son controles internos.

La revisión técnica histórica comprobó contratos, interacciones y distribución móvil del HTML. La valoración del usuario prevalece sobre esas comprobaciones: ese paquete no se promueve a diseño de producto, ni sus vivos especializados definen la arquitectura de sesiones nativa.

El worktree aislado ya existe en `C:\Users\valen\.codex\worktrees\multisport-foundation\KPKNFit`, rama `codex/multisport-foundation`, desde `f174a5020`. Las rutas nuevas y modificaciones propias se revisan y copian explícitamente al candidato; los cambios ajenos están protegidos por hashes.

El siguiente corte continúa sin UI: propuestas recuperables sobre sesiones reales, guardado durable y consulta de actividad completada sin doble conteo. Pasó **117 pruebas dirigidas**, `assembleBaseDebug`, instalación y arranque observado en el AVD propio. La primera espera de lanzamiento agotó su timeout; el seguimiento confirmó la bienvenida real sin reinstalar ni reiniciar. Su alcance y límites están en [`INTEGRATION-CONTRACT.md`](INTEGRATION-CONTRACT.md), y la evidencia en el [handoff del corte](../../artifacts/multisport-integration-20261018/HANDOFF.md). Los resultados están separados de la primera base en el estado de ejecución.

## Regla visual del usuario

**Todo lo nuevo debe apostar por lo visual, animado y amigable sin perder potencia.** La identidad, jerarquía y componentes de KPKN son un requisito verificable. El movimiento explica cambios de estado y conserva continuidad; se respeta movimiento reducido. La profundidad avanzada permanece accesible mediante edición directa y detalle progresivo. Ninguna maqueta se promueve a referencia para Kotlin por un chequeo de sintaxis: requiere comparación visual con capturas nativas y aprobación humana.

Los criterios por pantalla están en [`VISUAL-CONTRACT.md`](VISUAL-CONTRACT.md).

La repetición nativa pasó las **87 pruebas dirigidas** y `assembleBaseDebug` terminó correctamente. El APK se instaló en un AVD nuevo de esta tarea, `emulator-5580`: se comprobó `com.example.kpkn/.MainActivity` en primer plano, una pantalla real de bienvenida, árbol UI de 38 nodos y registro de fallos vacío durante la observación. La primera captura había salido vacía y el lanzamiento agotó su espera; el seguimiento sin reiniciar confirmó contenido. Esto acredita arranque observado, sin medir ni aprobar su latencia, y no acredita soporte deportivo ni aceptación de la nueva UI. Los intentos anteriores fallidos y la corrección del fixture se conservan. Cada clase de evidencia se registra por separado en [`execution-state.json`](execution-state.json).

## Regla de promoción

Cada etapa requiere un contrato de dominio; persistencia compatible con datos previos; editor y sesión en vivo o captura externa; historial y progreso; carga propia de la disciplina y reglas explícitas de calidad de datos; HTML de UI aprobado antes de implementar esa UI; pruebas y capturas de los estados acordados. Si una etapa necesita sensores o hardware, la compatibilidad y sus límites se verifican por separado. Ninguna inferencia de benchmark, maqueta o test unitario sustituye la aceptación visual o de dispositivo que corresponda.

## Fuentes y límites

- La galería multideporte v2 está rechazada y archivada. La referencia visual de Cardio v2 se conserva en [`index.html`](../design/multisport-v1/references/cardio-v2/index.html) y su [`PROVENANCE.md`](../design/multisport-v1/references/cardio-v2/PROVENANCE.md); su estado y autorización son independientes y se comprueban en su worktree.
- El benchmark cardio está en el worktree U5: `C:\kw\u5\docs\audits\2026-10-cardio-benchmark\README.md`. Es un análisis estático de TrainingPeaks, Garmin Connect y FITIV; runtime figura como `NOT_RUN`. Las versiones y hashes de los APK analizados están en ese informe. Son precedentes de diseño, no pruebas de KPKN ni evidencia actual de compatibilidad.
- El informe local de [evidencia WLAnalysis](../../artifacts/wl-analysis-re-20261017/INFORME.md) enlaza los artefactos de análisis. El documento de síntesis está en `C:\kw\vt-a\docs\video-technique\WL_ANALYSIS_RE.md`. Se refiere a WLAnalysis 2.13.9 y a una inspección estática del XAPK suministrado; no se ejecutó la aplicación ni se usó el teléfono. Sus hallazgos sirven para formular una especificación propia, no para afirmar precisión, rendimiento, autenticidad o soporte de KPKN, ni para copiar código, recursos o modelos.
- La [revisión de continuidad de técnica en vídeo](../../artifacts/video-technique-continuation-20261016/RESUME_REVIEW.md) recoge evidencia histórica de esa línea. Sus pruebas/builds/capturas pertenecen a candidatos anteriores; deben vincularse a un candidato y repetirse solo cuando un cambio o un fallo lo justifique.

## Proceso de investigación

Para cada referente: registrar app, versión, hash del artefacto y preguntas concretas → inspección estática → runtime solo si la pregunta sigue abierta y es necesario → especificación propia de KPKN → pruebas y capturas sobre ese contrato. Anotar qué se observó, qué se infiere y qué no se pudo comprobar. Reutilizar los informes cardio y WLAnalysis con sus límites históricos; no convertir hallazgos estáticos en funciones aprobadas.

La secuencia completa, las dependencias y los criterios por etapa están en [`ROADMAP.md`](ROADMAP.md).
