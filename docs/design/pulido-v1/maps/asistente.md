# Paquete ASISTENTE · prefijo `as` · pestaña `asistente` (punto 4 del usuario)

Archivos: `src/scenes-as.js`, `src/as.css`. Compilar: `node build.cjs --out=shots/as`. 25 escenas interactivas + 1 documento (`as-resumen`, siempre la última).
Datos de ejemplo: la sesión «Legs II» de la captura h04 (6 ejercicios, 18 series, 59 min), límite de tiempo 50 min, límites rígidos de series por sesión 10.
Los pesos de cada ejercicio por músculo son inventados pero coherentes (el desglose suma exactamente la cifra).

## 1. Código real que corresponde a cada pieza (`android-native/.../com/example/kpkn/`)
- Hoja y pestañas: `screens/sessioneditor/components/sheets/AssistantSheet.kt` (`AssistantGlassOverlay`, `AssistantSheet`, `KpknSheet` 82 %).
- Métricas: `.../sheets/AssistantContentsTab.kt` (hoy «Contenido»); datos de `uiState.assistantReport.volumenPorMusculo`
  (`domain/sessionassistant/SessionAssistantEngine.calcularVolumenPorMusculo` → `VolumeCalculator.calculateUnifiedMuscleVolume`),
  `SessionEditorAnalytics.buildMuscleVolumeRows` (directas/secundarias/estabilizadoras e intensidad Media-Alta-Muy alta-Máxima),
  `uiState.sessionTimeBreakdown` y `SessionEditorDuration.sessionTimeBreakdownCaption` (preparación/ejecución/descansos/calentamiento).
- Sugerencias: `.../sheets/AssistantSuggestionsTab.kt` (`AssistantSuggestionCard`); motor `domain/sessionassistant/SessionAdvisor.kt`
  (reglas `user_limits` y `time_overage`, máx. 3 avisos), `SessionAssistantActions` (apply/measure), modelos en `SessionAssistantModels.kt`;
  aplicar: `SessionEditorViewModelAugeActions.applyAssistantSuggestion`.
- Plantillas: `AssistantSheet.AssistantTemplatesTab`, `components/TemplateCatalogBrowser.kt` (filtros, secciones, Mis plantillas, guardar/editar),
  `components/TemplateCatalogCards.kt` (`CompactTemplateCard`, `TemplateExpandedDetails`), `sheets/TemplateApplyDialog.kt`,
  `data/sessions/SessionTemplateModels.kt` y `SessionTemplates.kt` (datos), `domain/templates/TemplateCatalogFilterLogic.kt`.
- Reglas: `.../sheets/RulesSheet.kt` (valores, alcance, descansos, plantillas de reglas, límites, intensidad, `StickyActionBar`),
  `.../sheets/RulesTimeTab.kt` (reloj, presupuesto, `RulesTimeCoachModule`), `SessionEditorRulesEngine.kt` (`applyDefaults`, `evaluateApply`,
  `validateBeforeSave`), `RuleTemplateStore.kt` (4 plantillas de fábrica), `domain/sessionassistant/TimeCoachEngine.kt`.
- Entradas a Reglas hoy: botón «Reglas» y anillo de tiempo de la barra inferior (`SessionEditorScreen.kt` → `openRulesSheet(initialTab 0/1)`).

## 2. Escena → pieza
| Escena | Pieza de la app |
|---|---|
| as-entrada | `KpknSheet` (entrada 220/420 ms, contenido a 160 ms) + las 4 pestañas |
| as-met-media / completa / todos | `AssistantContentsTab` rediseñada; detalle = per-ejercicio × contribución; «Todos» = `standardVolumeMuscles` (17) |
| as-met-cardio / vacia | `AssistantContentsTab` con cardio y sin ejercicios |
| as-sug-lista / aplicada | `AssistantSuggestionCard` ×2 (`user_limits`, `time_overage`) |
| as-sug-vacio / nota / carga | `KpknEmptyState` real, `notas` del informe, `SheetLoadingSkeleton` |
| as-pla-catalogo / filtros / vacio | `TemplateCatalogBrowser` + `CompactTemplateCard` + `CatalogEmptyState` |
| as-pla-vista / aplicar / aplicando / aplicada | `TemplateExpandedDetails`, `TemplateApplyDialog`, estado nuevo |
| as-pla-mias / guardar | `UserTemplateManagementRow`, `SaveUserTemplateDialog` |
| as-reg-valores / compuestos | `RulesSheet` pestaña «Reglas» (alcance Sesión / Grupo / Comp. y aisl.) |
| as-reg-limites / limites-vacio | fila plegada «Límites» de `RulesSheet` (cinco límites + rígidos) |
| as-reg-tiempo | `RulesTimeTab` completa |
| as-resumen | documento: mapa, lo que no existe, decisiones |

## 3. Propuestas y decisiones (todo lo marcado `new` o `ask` en las notas)
- **Pestañas en línea fina**, no píldora: cuatro etiquetas en una píldora segmentada no caben bien en 360 px. Indicador en tinta; contador en Sugerencias y punto en Reglas. Letra frenada en 1,08 para que quepa a 130 %.
- **Título y pestañas fijos, contenido con scroll**; dos alturas (media 62 %, completa 93 %), el asa alterna y se conserva al cambiar de pestaña. Hoy es fija al 82 %.
- **Métricas**: orden = cifras → volumen por músculo → tiempo. Cifras en un módulo; volumen con cifra Inter tabular alineada (nunca «10.\n5», coma decimal), barra de una sola tinta
  (la paleta canónica de músculos ya es neutra a propósito), el exceso sobre el límite del usuario en ámbar, marca de «tu límite por sesión». Sin MEV/MAV/MRV: el informe tiene el tipo `VolumeThreshold` pero devuelve `umbralesPorMusculo` vacío.
  Detalle al tocar: aporte de cada ejercicio, intensidad (ya calculada) y semana frente al límite semanal (`new`: usa las sesiones de la semana que ya recibe el asistente).
  Tiempo con la barra desglosada y el límite (`move`: hoy solo está como línea en Reglas › Tiempo). Cardio: tarjeta propia (`new`).
- **Sugerencias**: se conservan las dos reglas y su texto; el ahorro de cada casilla sale en su columna (`effect` ya existe) y la caja «Duración» se recalcula al marcar (`new`, suma de efectos).
  **Descartar** y **Deshacer** son `new` (guardar la sesión previa a aplicar). Vacío: texto real + «lo que el asistente revisa» (solo existen dos reglas).
- **Plantillas**: tarjeta con portada pequeña, línea propia y datos reales; la «Dosis compacta» (sin descripción corta) muestra su dato de equipo en vez del párrafo genérico repetido.
  Filtros reales (Nivel Fácil/Medio/Exigente; Duración ≤45 / 46–75 / ≥76; Agrupar Grupo/Rutina/Nivel/Duración) en un panel segmentado, chips de tipo, chips de filtros activos.
  Ficha de vista previa dentro de la hoja (`new`). Decisión Reemplazar/Añadir con el resultado de cada una (sumas simples; el tipo `TemplatePreview` ya está en el modelo pero el motor no lo rellena).
  «Mis plantillas»: ⋯ con Editar/Archivar/Borrar; «Guardar sesión» pasa allí (`move`); la plantilla ilegible en cuarentena se conserva.
- **Reglas**: tres apartados (Valores · Límites · Tiempo). «Aplicar a la sesión» fijo solo en Valores (`move`: hoy está también en Tiempo, donde no hace nada visible). Series/reps/RPE con − y + (`new`; el teclado numérico se conserva al tocar la cifra).
  Los 4 descansos abren el selector de descanso que ya existe (no se redibuja). Límites muestran cómo está la sesión frente a cada uno y avisan arriba si choca (textos de `validateBeforeSave`).
  Explicación real de «rígidos»: bloquea guardar y activa los ajustes del asistente; sin rígidos solo avisa. Etiquetas acortadas (el módulo dice «por sesión»/«por semana»).
  Al aplicar, la hoja se cierra, como hoy; `NoChanges` se muestra como aviso «Sin cambios» (el texto es mío).
- No inventado: veredicto, puntuación, equilibrio push/pull, frecuencia, fatiga y zonas de volumen no se dibujan (informe vacío).

## 4. Qué exigiría implementarlo
- Ningún cambio de motor para Métricas, Plantillas ni Reglas (todo sale de datos y funciones existentes). Hacen falta: lista de contribuciones por ejercicio a un músculo (recorrido igual al de `calculateSubMuscleBreakdown`, hoy solo para 3 músculos) y el volumen de la semana por músculo (`VolumeCalculator.calculateMuscleVolume` ya se usa en `validateBeforeSave`).
- Sugerencias: instantánea de la sesión previa a aplicar (Deshacer), «descartada» en memoria por sesión, suma de `effect` al marcar casillas. Rellenar `TemplatePreview` (volumen, duración, aviso de tiempo) para la vista previa y la decisión.
- Reglas: quitar el botón «Reglas» de la barra inferior (paquete Editor) y abrir el asistente en la pestaña Reglas / apartado Tiempo desde el anillo de tiempo.
- Ficha de plantilla: facetas reales (`SessionTemplateFacets`) para duración y músculos; el catálogo de ejemplo usa los metadatos de `SessionTemplates.kt`.
- Corregir el preset «Tiempo corto» (guarda RPE 8,0 con tipo RIR).

## 5. Dudas para el usuario
1. Dos motores de tiempo (Sugerencias › «Ajustar al tiempo…» y Reglas › Tiempo › «Para entrar en tu límite»): ¿los unimos en uno? Recomiendo unirlos más adelante; hoy quedan donde están.
2. Volumen por músculo: la cifra de siempre pondera por el papel mayor de cada ejercicio; el cálculo de roles (directo + secundario + estabilizador) da otros números. ¿Cuál queremos como fuente única?
3. ¿Quieres Deshacer y Descartar en Sugerencias (no existen hoy)?
4. ¿«Aplicar» solo en Valores (propuesto) o en las tres pantallas como hoy?
5. ¿Quieres que construyamos equilibrio, frecuencia o fatiga para Métricas? Hoy no hay motor.
6. Pestañas en línea fina (propuesto) o en píldora segmentada como el resto de hojas.
