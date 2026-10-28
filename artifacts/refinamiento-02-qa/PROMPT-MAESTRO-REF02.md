# PROMPT MAESTRO — KPKN · Sesión en vivo · Refinamiento 02

Trabaja en `C:\Users\valen\Documents\KPKNFit`, rama `master`, sin worktrees. Tu misión es **implementar** el Refinamiento 02 con **paridad 1:1 con los prototipos aprobados**, en toda la app en vivo. No es una revisión, no es un informe y no es una maqueta: es implementación real, compilada, instalada y verificada en el Z Flip, con capturas nativas comparables a las referencias.

Si el código actual no permite la paridad, **cámbialo**. No adaptes el prototipo al código existente. Si una decisión no está cubierta por el plan o por los prototipos, **pregúntame antes de asumir**. No hay decisiones autónomas de diseño.

---

## 0. Regla de oro

El usuario exige textualmente:

> «Quiero una implementación que respete a raja tabla los prototipos y todas las decisiones. Si tienes una duda de implementación, pregúntamela y no asumas nada, a menos que yo te pida expresamente que tomes decisiones autónomas por mí. Trabajarás en la rama Master principal, sin worktrees.»

Y ahora añade:

> «Que si hay que modificar aspectos de la app en vivo para que calce al 100%, hay que hacerlo.»

Por tanto: **ninguna pantalla se declara terminada si no coincide visual y funcionalmente con su referencia**, y **no se resuelve un desbordamiento ocultándolo** (`clip`, `maxLines`, scroll interno, campos fuera del marco). Si algo no cabe en el marco fijo, se rediseña la composición, no se recorta.

---

## 1. Fuentes de verdad (leer en este orden)

1. **Plan**: `artifacts/refinamiento-02-qa/plan.txt` (copia del original adjunto). Léelo completo antes de tocar código.
2. **Prototipos**: `C:\Users\valen\Desktop\KPKN-sesion-iteracion-02\`
   - `prototipos.html` — vistas `cards` y `list`, selector **Estado** con 62 situaciones. URLs del tipo:
     `prototipos.html?view=cards&scene=strength`, `...scene=rest-card`, `...scene=rest`, `...scene=rest-feedback`, `...view=list&scene=strength`, `...view=list&scene=superset`, `...view=list&scene=mobility`, `...view=list&scene=warmup`, `...view=cards&scene=video-detail`, `...view=cards&scene=framing`, `...view=cards&scene=voice-picker`, `...view=cards&scene=voice-conversation`, `...view=compare`.
   - `GALERIA.html`, `VERIFICACION.md`, `README.md`, `refinements.css`, `refinements.js`, `styles.css`, `app.js`.
   - `capturas-v2/` — 24 capturas canónicas (01…23 + manifest). Son la referencia visual congelada; úsalas para comparar.
3. **Contratos ya existentes**: `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/ANDROID_ARCHITECTURE_MAP.md`, `docs/ANDROID_UI_SCREENS_MAP.md`, `.opencode/kpkn-map.md`.
4. **Artefactos de QA actuales**: `artifacts/refinamiento-02-qa/` (`ui.py`, `shots-qa/`, XMLs de test, plan).

Precedencia si hay conflicto: **decisiones del usuario > plan > prototipo > código existente**. Si el prototipo contradice el plan, detente y pregunta.

---

## 2. Decisiones ya cerradas por el usuario (no reabrir)

1. **Lista sin roadmap**: el registro pertenece a cada serie. El roadmap inferior se retira de Lista. El inspector puede conservar la variante con roadmap sólo para comparación, no como predeterminada.
2. **Retirar la franja permanente del Relator**. Se conservan sus acciones y datos útiles; desaparece como banda fija.
3. **Guía hablada**: política inicial = guía **antes** de grabar + mensajes **urgentes** durante la grabación. Es independiente de Modo Voz y funciona con Modo Voz apagado.
4. **Selector de vista en el menú ⋯**: **Tarjetas** es la vista inicial; **Lista** se abre desde el menú de sesión (⋯). Ambas comparten el mismo borrador por ejecución/ejercicio/serie/lado; cambiar de vista no duplica ni pierde estado.
5. **Historial de voz persistido en Room** (el usuario rechazó memoria volátil y aplazamiento). El contrato de retención y el esquema ya están esbozados en el árbol de trabajo: `workout_voice_history`, Room v37, `37.json`, migración `WORKOUT_VOICE_HISTORY_36_37`. Consérvalos y complétalos; no borres historiales publicados.
6. **El flavor `health` ya no se usa**. No lo compiles, no lo arregles, no toques AUGE. Usa exclusivamente `assembleBaseDebug`.

---

## 3. Estado real de partida (verifícalo; no confíes en resúmenes ni en tareas marcadas)

- Rama `master`, HEAD actual `afb6dc7525b70b29b5e023673739f34dc854ae0a`. **Hay mucho trabajo sin commitear** (propio de esta tarea + WIP concurrente de otros agentes). No hagas `reset`, `clean`, `checkout --`, `restore`, ni descartes nada. No hay que preservar worktrees: se trabaja aquí.
- **Ya existe y debe conservarse**:
  - Marco fijo por viewport en `LiveSessionCardFrame.kt` (312×394 / 345×470 / 364×488) y su uso en `WorkoutV2Body.kt`.
  - Cápsula glass del header en `WorkoutHeaderBar.kt` (pero la composición aún no es la del prototipo).
  - Vista Lista nueva en `LiveSessionListView.kt` (**untracked**), cableada en `WorkoutV2Body.kt`. Audítala contra el prototipo; no asumas que está bien.
  - Descanso/feedback/juegos con cambios en `RestLiveCard.kt`, `RestControls.kt`, `WorkoutRestTimerOrchestrator.kt`, `RestFeedbackCard.kt`, `WorkoutRestOverlay.kt`.
  - Voz: `VoiceConversationSurface.kt`, historial Room v37, `WorkoutVoiceCommandHandler.kt`, `WorkoutVoiceController.kt`, condiciones de audio.
  - Encuadre: `FramingCheckController.kt`, `TechniqueRecorderStage.kt`, `FramingGuideOverlay.kt`, `FramingChips.kt`, `FramingCheckStatus.kt`, `SpokenFramingGuidePolicy.kt`.
  - Persistencia durable: `commitLiveMutation`, `awaitPendingLiveMutation`, `mergeCommittedLiveMutation`, `WorkoutStructuralPersistenceController`.
  - Correcciones de undo/reordenar/omitir y haze del readiness.
- **Parcial o claramente desalineado (lo que el usuario vio y por lo que se queja)**:
  - **Tarjeta de Tarjetas**: `SetExecutionCard.kt` sigue con el diseño anterior (`CARGA (kg)`, chips `Anterior/Sugerida/+2,5`, `REPS +`, `ESFUERZO`), sin `SERIE x/y`, sin carga grande con −/+, sin `REPETICIONES`, sin `Parciales, ayuda y ROM`, y con el registro fuera del marco. La referencia exige la composición aprobada con `Registrar serie` en el marco/roadmap aprobado.
  - **Header**: la cápsula existe, pero el prototipo pide contexto de sesión dentro (programa · semana, progreso de ejercicios/series, cronómetro) y el título/contexto del ejercicio debajo, no el título dentro de la cápsula con el cronómetro en una píldora separada.
  - **Lista**: existe archivo nuevo, pero no está verificada en dispositivo ni contra las capturas; puede no coincidir en filas, acordeón, superserie, movilidad y aproximaciones.
  - **Analizador, encuadre, selector de voz, readiness/cierre**: conservan composición anterior en varios puntos; audita cada pestaña/estado contra `capturas-v2/13`–`20` y el plan.
- **Baseline de tests**: las 4 suites de sesión en vivo están verdes (63 tests) en `testBaseDebugUnitTest`: `LiveOptionsVmTest` (13), `LiveReorderVmTest` (9), `LiveExerciseOptionsAuditTest` (23), `LiveLayerDismissTest` (18). No las rompas.
- **Estado del dispositivo**: KPKN Dev `com.example.kpkn.dbg` instalada con datos preservados; sesión «Cardio y potencia» en curso. La app personal `com.example.kpkn` **no se toca**.
- **El flavor health está roto en HEAD** (`AugeHealthIntegration` sin resolver). Ignóralo. No lo compiles.

**No confíes en los estados de tareas ni en resúmenes previos: el agente anterior sobredeclaró paridad visual. Audita archivo por archivo y pantalla por pantalla.**

---

## 4. Contrato visual y de comportamiento (implementación, no interpretación)

### 4.1 Marco y densidad (todas las tarjetas)
- Mismo ancho y alto para todos los estados de un mismo viewport. Sin scroll interno, sin campos ocultos bajo el pie, sin recorte.
- Medidas del plan: 312×394 dp en 360×740; 345×470 dp en 393×852; ~363×488 dp en 412×1007. En el Z Flip (360×880) manda el ancho 360 → 312×394.
- El alto sale del viewport, nunca del contenido ni del estado; el teclado no lo expande. Operaciones largas van a una superficie externa (hoja), no dentro del marco.
- Aproximaciones adicionales se paginan dentro del marco; movilidad/notas/detalle largo se abren fuera.
- La tarjeta debe incluir la composición aprobada: `SERIE x / y`, carga grande con −/+, sublínea `peso × reps · RIR`, `REPETICIONES`, `ESFUERZO` (con «Sin marcar» cuando no hay marca real), `Parciales, ayuda y ROM`, `Avanzado / Modo visual / historial` y el registro según la referencia (`Registrar serie` en el pie del marco/roadmap aprobado). Si algo no cabe, rediseña la densidad; no ocultes.

### 4.2 Header
- Cápsula gris translúcida, blur real, reflejo de borde y sombra. Contexto de sesión dentro (programa · semana, progreso, cronómetro) y el contexto del ejercicio separado debajo. La Lista se desplaza por debajo del header sin tapar controles.
- Identidad KPKN: negro, carbón, crema y grises; Syne + Inter reales; detalles de color contenidos; sin textos amarillos ni apariencia Material por defecto.

### 4.3 Tarjetas (vista Tarjetas)
- Una serie por tarjeta; estados: registro, técnicas, movilidad, aproximaciones, visual, cardio, calibración, error, descanso y feedback.
- El esfuerzo planificado **nunca** se convierte en esfuerzo realizado. Si no hay marca, «Sin marcar»; registrar exige marca.
- El registro y la corrección usan el mismo borrador compartido; no duplican series.

### 4.4 Lista
- Una fila por serie; acordeón activo; registro desde la propia fila; sin roadmap inferior.
- Conserva datos y posición al abrir detalles/registrar/corregir; navegación y restauración sin perder borradores.
- Preparación compacta: movilidad en tres filas (orden, nombre, completado); aproximaciones A1/A2 con porcentaje, carga × reps y completado, con encabezado pequeño de preparación y avance.

### 4.5 Superseries
- Agrupadas por **ronda → A1/A2** en un contenedor continuo, no dos ejercicios desconectados.
- Conserva identificadores reales de grupo y ejecución; añadir/omitir/reemplazar no reconstruye identidades ni altera lo registrado.
- Descanso correcto en cada transición: entre ejercicios vs. fin de ronda; configuración propia (rondas y ambos tiempos).

### 4.6 Descanso, feedback y juegos
- Dos presentaciones del mismo estado: tarjeta estándar y pantalla completa. Minimizar/expandir conserva tiempo restante, pausa, sugerencia y borrador de feedback.
- Anillo con borde iluminado que progresa, punto de luz y corona tenue (eclipse); movimiento reducido admitido. Tiempo principal; pausa y ±15 s visibles; al terminar, tiempo extra y continuar.
- Sugerencia: tiempo planificado, sugerido y transcurrido; aplicarla conserva lo consumido. La duración del ejemplo es sintética.
- Feedback: calidad técnica 1–10, molestias y seguimiento de molestia previa; zonas fuera de la tarjeta; guardar/después explícitos; si acaba el tiempo, el formulario y el borrador permanecen.
- Juegos: Salta las mancuernas, Entre las pesas, Escuadrón retro, con estado propio; reiniciar partida no reinicia descanso; pausar descanso congela el juego.
- El estado sobrevive a cambio de presentación, navegación y restauración.

### 4.7 Etiquetas, configuración, reemplazos y avisos
- Etiquetas por ejercicio con contexto previo; editar nombre, equipo/marca, carga base y notas; los registros ya hechos conservan su contexto; reiniciar referencia con confirmación específica.
- Configurar serie por pestañas (objetivo, carga, detalle); alcance esta serie vs. pendientes; guardar objetivos no cambia realizado; salir con cambios decide conservar/descartar.
- Reemplazar: elegir y luego revisar; conserva plan permitido (series, objetivos, intensidad, propósito, descanso); no hereda a ciegas cargas, historial/PR, etiquetas, técnicas, lados ni ROM; lo registrado permanece en el original.
- Avisos: falta de esfuerzo junto al campo; molestia histórica con contexto; error de guardado con reintento y borrador; sesión en curso con reanudar/revisar descarte. Sin alarmas por información orientativa.

### 4.8 Analizador / Modo visual
- Captura → revisión completa: reproductor persistente y línea de tiempo; pestañas Trayectoria, Métricas, Repeticiones; jugador visible mientras se consulta cada panel.
- Unidades y confianza reales: dato ausente «—», estimación «≈», sin m/s sin escala validada.
- Corrección por repetición conserva la observación original y su revisión; el resultado revisado entra al borrador, pero **no registra automáticamente al cerrar**.
- Pestañas en lugar de columna interminable; no inventar datos.

### 4.9 Encuadre y captura
- Guía basada en FramingSpec, no vista lateral para todo; una instrucción prioritaria cada vez; subtítulo equivalente y botón repetir.
- Estados explícitos: persona ausente, más de una persona, demasiado cerca, pies/cabeza fuera, atleta pequeño, vista incorrecta, cara oculta cuando aplique, móvil inclinado, disco ausente cuando el perfil lo requiera, «comprobando» y «no se pudo comprobar». Nunca inventar una aprobación.
- Veredicto estable antes de grabar; una evaluación favorable no basta.
- Guía hablada independiente de Modo Voz; deduplicar, limitar frecuencia y priorizar problemas; no interrumpir la serie por oscilaciones; conservar la captura para revisión.
- Inicio/parada como estados explícitos: preparado, iniciando, grabando, deteniendo, guardando, error. No mostrar confirmación antes de que el grabador confirme. Parar siempre disponible manualmente. Repetir una orden no duplica vídeos.

### 4.10 Modo Voz
- Selector compacto Música / Manos libres con prueba «Listo» antes de activar; verificación de ruta, permisos, timeout y liberación del micrófono.
- Vista de conversación: escucha/pausa, frase original, interpretación estructurada, ejercicio/serie vinculados, estado del registro; separar «registrada» de «por revisar»; una captura ambigua no se registra como segura.
- Editar abre borrador; cancelar no modifica el original; confirmar actualiza esa identidad y muestra el cambio anterior, sin crear otra serie.
- Mantener identificadores de evento, comando y ejecución con versión, origen y hora; no guardar audio crudo por defecto.
- Pausar/desactivar Modo Voz cancela su escucha; no borra la conversación ni cambia la guía hablada.

### 4.11 Readiness y cierre
- Respetar el trabajo previo de `b3a057e13` sobre `WorkoutReadinessSheet` y `RecordedSessionFinishSheet`: estructura, campos y contratos. No mezclar esfuerzo de serie con esfuerzo opcional de sesión.

### 4.12 Relator
- Sin franja permanente. Se conservan acciones/datos útiles; asistencia contextual y conversación cuando aporten.

---

## 5. Criterios de aceptación (literales del plan)

- Mismo marco por viewport para todas las tarjetas, sin recorte de información ni desplazamiento interno; no solucionar overflow simplemente ocultándolo.
- Lista conserva datos y posición al abrir detalles; registrar nunca usa RIR/RPE planificado como realizado.
- Superserie conserva grupo, orden y ronda, con descanso correcto en cada transición.
- Descanso y feedback sobreviven al cambio de presentación, navegación y restauración.
- Etiquetas y reemplazos no reescriben registros históricos ni importan cargas incompatibles.
- Analizador muestra confianza/unidades reales, permite corregir y no registra automáticamente al cerrar.
- Captura requiere encuadre válido o un tratamiento explícito del error según la política acordada; órdenes repetidas no crean vídeos duplicados.
- Guía hablada disponible con Modo Voz apagado; instrucciones y subtítulos coinciden; no se activan comandos a partir de TTS.
- Permisos denegados, desconexión de auriculares, interrupciones, retorno del fondo y muerte del proceso tienen salidas recuperables.
- TalkBack, áreas táctiles, texto aumentado, movimiento reducido, contraste y coste del blur comprobados nativamente.
- Readiness/cierre mantienen campos, semántica y trabajo del agente anterior.

---

## 6. Condiciones de voz, audio y persistencia (literales)

> «La prueba de voz debe liberar su captura antes de entregar el micrófono al controlador de sesión. Definir un único dueño de la grabación y arbitrar reconocimiento, audio del vídeo, música y TTS. Una frase de la propia guía no puede disparar un comando; la estrategia debe validarse con altavoz, auriculares y Bluetooth. Evitar que silenciar el reconocimiento durante TTS deje al usuario sin poder detener la captura. La interfaz conserva un control manual de detener siempre disponible.»

> «El inicio/parada debe modelarse como estados explícitos: preparado, iniciando, grabando, deteniendo, guardando, error. No mostrar una confirmación antes de que el grabador confirme el cambio. Pausar/desactivar Modo Voz cancela su escucha; no borra la conversación ni modifica la preferencia independiente de guía hablada.»

> «Si el historial de voz necesita persistencia nueva, primero se define el contrato y su retención; cualquier cambio de esquema sigue las reglas actuales del repositorio y conserva las historias publicadas.»

Room: mantener `MFP31` público y los historiales privados `POST-RINGS31/32/33/34`; el esquema exportado es autoritativo. El árbol ya trae v37 y `37.json`; verifica la migración `WORKOUT_VOICE_HISTORY_36_37` y sus tests.

---

## 7. Arquitectura y reglas de código

- Clean Architecture + MVVM, flujo unidireccional. `domain/` puro Kotlin sin `android.*`. Room/red/archivos/plataforma en `data/`, `services/` o límites de presentación.
- ViewModels exponen `StateFlow` de sólo lectura (`asStateFlow()`); nunca `MutableStateFlow`.
- Trabajo bloqueante en `Dispatchers.IO`; UI en `Dispatchers.Main`. Inyección manual por constructor; layout por feature.
- Las vistas emiten eventos; no escriben Room directamente.
- No introduzcas una migración por el rediseño. Si hace falta persistencia nueva, define contrato y retención primero.
- Compose nativo; no adoptes Material por defecto ni estilos genéricos. Usa los tokens KPKN existentes (Syne/Inter, colores, formas) y añade tokens sólo si faltan.
- Tests: unitarios + Compose Robolectric (`GraphicsMode.NATIVE`), contratos `sharedTest`, y los render sheets existentes para 360×740, 393×852 y 412×1007.
- No hay `health`: no compiles `assembleDebug` completo; usa `assembleBaseDebug`.

---

## 8. Método obligatorio de trabajo

1. **Auditoría de paridad primero.** Abre las 24 capturas de `capturas-v2/` y las escenas del HTML. Construye una matriz:
   `área | estado | referencia | archivo nativo | PASS/PARCIAL/FALTA | captura nativa | nota`.
   Guárdala en `artifacts/refinamiento-02-qa/parity/matriz.md` (y capturas en `parity/shots/`).
   Incluye las 62 situaciones del selector y, como mínimo, las 9 áreas: header+tarjetas, lista, preparación, superseries, descanso+feedback+juegos, etiquetas/config/reemplazo/avisos, analizador, encuadre+captura, voz, readiness/cierre.
2. **Implementa por áreas, en este orden**: Tarjetas (header + tarjeta + registro) → Lista (filas/acordeón/superserie/preparación) → Descanso/feedback/juegos → Etiquetas/config/reemplazo/avisos → Analizador → Encuadre/voz → Readiness/cierre → accesibilidad.
3. **Para cada pantalla/estado**:
   - Ajusta o crea tests que fijen el contrato (identidad, estado compartido, tamaños, textos, accesibilidad).
   - Compila `assembleBaseDebug`.
   - Instala en KPKN Dev y captura el estado nativo.
   - Compara lado a lado con la captura del prototipo; corrige hasta que coincida.
   - Guarda la captura y anota PASS sólo entonces.
4. **No avances** a la siguiente área con una captura que no coincida. No hay PASS por tests verdes: los tests no demuestran paridad visual.
5. **Conserva lo que ya funciona**: persistencia durable, undo/reordenar/omitir, historial de voz v37, guía de encuadre, haze del readiness, 63 tests verdes. Añade regresiones para cada cambio.
6. **No cambies el modelo de datos ni el esquema** para resolver un problema de layout.
7. **Si una referencia es ambigua o el plan contradice el prototipo**, detente y pregunta con captura y archivo concretos.

---

## 9. Compilación, tests y dispositivo (protocolo exacto)

### Gradle
- Wrapper obligatorio:
  `powershell -NoProfile -File C:/Users/valen/Documents/KPKNFit/.opencode/scripts/run-gradle.ps1 -Tasks "<tareas>" -TimeoutSec 1800`
  con `workdir: android-native`.
- Nunca llames `gradlew.bat` sin `--no-daemon --console=plain`; nunca mates builds de otros agentes.
- Build: `assembleBaseDebug`.
- Tests focalizados: `testBaseDebugUnitTest --tests '*.LiveOptionsVmTest'` etc.
- APK: `android-native/app/build/outputs/apk/base/debug/app-base-debug.apk`.
- **Prohibido** compilar o arreglar `health`/AUGE.

### Z Flip
- Solo app de pruebas **KPKN Dev** `com.example.kpkn.dbg`, identidad estable y firma existente. La app personal `com.example.kpkn` no se modifica.
- Usa `C:/kw/tools/phone_run.py` y el lock `C:\kw\phone.lock`. Antes de actualizar, verifica certificado y compatibilidad Room/datos. Instala con `adb install -r` (preserva datos). **Nunca** `uninstall`, `pm clear`, borres datos ni crees variantes.
- El usuario autorizó usar el teléfono aunque esté ocupado; usa `--supervised` cuando el lock lo requiera. Aun así, no borres ni desinstales nada.
- El teléfono se bloquea rápido: encadena `wake` + swipe de desbloqueo (540,2200→540,600) antes de cada orden. `uiautomator dump` puede devolver estados obsoletos; **la captura es la fuente de verdad**.
- No uses la pantalla principal del Flip plegado. No cambies ajustes del sistema.
- Helper disponible: `artifacts/refinamiento-02-qa/ui.py` (`launch|wake|wait|tap|tapdesc|tapxy|swipe|key|shot|dump`). Para textos con espacios, pasa la orden entre comillas (`"tapdesc:Registrar serie"`).

### Evidencia
- XML de tests a `artifacts/refinamiento-02-qa/test-results-*/`.
- Log de build, salida de instalación y capturas en `artifacts/refinamiento-02-qa/parity/`.
- Reporte final separado en: **tests / build / instalación / runtime / paridad visual**, con HEAD exacto, comandos, códigos de salida y XML.
- Sin captura nativa que coincida con la referencia, el estado es FALTA, no PASS.

---

## 10. Prohibiciones

- No worktrees, no cambio de rama, no `reset --hard`, `clean -f`, `checkout --`, `restore` ni descarte de WIP ajeno.
- No `git add -A`; si el usuario pide commit, prepara sólo tus rutas y revisa el diff.
- No publicar, integrar, crear variantes ni enviar entrega sin autorización explícita.
- No leer/imprimir/commitear `.env`, credenciales, keystores ni tokens MCP.
- No invocar `scripts/telegramBot.js` ni contactar otros chats sin autorización específica para esta tarea.
- No regenerar assets grandes (comida, ejercicios, Vosk, datasets) a mano.
- No usar el flavor health ni tocar AUGE.
- No resolver overflow con `clip`/`maxLines`/scroll interno.
- No inventar estados de aprobación de encuadre, confianza/unidades del analizador ni esfuerzo realizado.
- No declarar paridad sin comparación visual nativa.

---

## 11. Primer entregable (antes de escribir código de UI)

Entrega la matriz de paridad con:
1. Qué áreas ya coinciden (con captura nativa + referencia).
2. Qué áreas están parciales (qué falta exactamente, archivo y línea).
3. Qué áreas faltan por completo.
4. El orden de implementación propuesto y los riesgos.

Después empieza por **Tarjetas** sin esperar otra instrucción: la orden de implementar ya está dada. No preguntes «¿procedo?». Pregunta sólo si una decisión de diseño no está cubierta por el plan o los prototipos.

---

## 12. Criterio de terminado

El trabajo termina cuando:
- Cada escena/estado de los prototipos tiene una captura nativa que coincide en composición, jerarquía, tamaños, tipografía, color y comportamiento.
- El marco fijo se cumple en los tres viewports sin scroll interno ni recorte.
- Todos los criterios de aceptación del punto 5 están verificados, con tests y evidencia nativa.
- Los 63 tests baseline siguen verdes y los nuevos tests cubren lo implementado.
- El reporte separado (tests/build/instalación/runtime/visual) está completo y sin afirmaciones sin evidencia.
