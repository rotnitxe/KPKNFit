# ED-E · Hojas del editor D1–D8

Rama `sv2/motores` (worktree `C:\kw\u12`), encima de MOT (`735dd49d2`). Sin push, sin teléfono, sin adb. El trabajo lo empezó Codex (sin commitear, cortado por límite de uso) y lo terminó Claude: se revisó, se completó y se validó, sin descartar nada.

## Qué cambió por hoja

| Hoja | Cambios |
|---|---|
| **D1 · Acciones rápidas** | Cabecera como el tablero: ficha de 52 dp con el patrón (o la máquina de cardio), nombre y línea «4 × 6–8 · RIR 2 · 3:00» (misma dosis que la fila de la sesión). Seis accesos en cuadrícula de celdas de 80 dp (3 columnas; 2 con letra > 115 %, para que «+ Aproximación» no se corte) y «Eliminar» aparte, de 48 dp. Se conservan etiquetas, descripción e involucramiento. Etiquetas de prueba `quick-action-*` intactas. |
| **D2 · Impacto** | Los valores del motor de MOT se mantienen («<1», 0, «Sin estimación», nota de carga axial). Duración y Calorías pasan a un solo módulo lado a lado, con la barra segmentada del reparto (preparación, ejecución, descansos, calentamiento) y su desglose; una sola nota de calorías. Con el cardio ya dentro de los totales (MOT) no se toca nada más. |
| **D3 · Sugerencias** | Techo de 3 avisos aplicado también en la vista (tarjetas + notas), con la etiqueta de MOT. El diseño ya seguía el tablero (ER1e): casillas y «Aplicar selección». |
| **D4 · Plantillas** | Etiqueta «Buscar plantillas» y buscador en primer plano. «Mis plantillas · N» es un botón que abre su lista (no se despliega); con la lista abierta el buscador se oculta y el botón pasa a «Volver al catálogo». «Guardar sesión» se conserva. Los dos botones van uno por fila: lado a lado cortaban el texto a 360 dp. |
| **D5 · Reglas** | Filas plegadas en un solo módulo con separadores: Plantillas, **Límites** (RPE máximo, ejercicios por músculo, series por músculo sesión/semana, ejercicios del mismo patrón, límites rígidos) y **Modificar intensidad** (modo RPE/RIR/Fallo/% RM, valor, alcance por músculo y «Aplicar intensidad»). Antes esos tres callbacks existían en la pantalla pero la hoja los ignoraba. |
| **D6 · Tiempo** | Se conserva todo el reloj. Se añade la barra del reparto del presupuesto por grupo con su leyenda («Principal 25 min · Accesorios 15 min») y el tramo libre, como el tablero. |
| **D7 · Versiones** | Estructura ya conforme (Actual, confirmación si hay cambios sin guardar). «Restaurar» gana el icono de historial. |
| **D8 · Transferir** | «Copiar a otros días» / «Traer de otra sesión» y «Añadir» / «Reemplazar» en filas propias de 48 dp; tarjetas de destino con indicador de selección; «Transferencia parcial» con interruptor KPKN (antes ON/OFF); aviso de reemplazo sobre el botón fuera del desplazamiento; tipografía KPKN. Solo «Copiar ahora» / «Traer ahora» (sin mover). |

## Pruebas

Comando base: `bash /c/kw/tools/gradle_slot.sh /c/kw/u12/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" :app:testBaseDebugUnitTest --tests …` con `KPKN_RENDER_DIR=C:/kw/u12/android-native/app/build/ede-renders`.

| Corrida | Resultado |
|---|---|
| `ede-1` | Falló la compilación: llave perdida en `AssistantImpactTab.kt` (mi edición). Corregido. |
| `ede-2` | Compiló el código; fallaron las pruebas por importaciones inexistentes (`assertExists`/`assertDoesNotExist` como funciones sueltas) en tres archivos. Corregido. |
| `ede-3` | 603 pruebas, 4 fallos: 2 de entorno, 1 render (el toque de «Mis plantillas» repetido al 130 % de letra, estado conservado) y 1 mía (`performScrollTo` sin contenedor). Corregidos. |
| **`ede-4` (final)** | Filtros `screens.sessioneditor.*`, `render.SessionAssistantRulesRenderSheetTest`, `render.SessionSupersetQuickActionsRenderSheetTest`: **603 pruebas, 2 fallos** (`ExerciseCatalogImageCoverageTest` ×2: faltan los manifiestos de imágenes del worktree, ya documentado en MOT y AUD2; no tocan estos archivos). Todo lo demás en verde, incluidas las pruebas de esta pista. |

Pruebas nuevas o adaptadas: `EditorSheetsEdeTest` (D2 ×2, D5 aplicar intensidad con Fallo, D6 reparto, D8 aviso de reemplazo y «Añadir»), `EditorSheetsD5D8Test` (Codex: Reglas plegadas, Tiempo, Versiones, Transferir), `AssistantSheetComposeTest` (3 avisos, Mis plantillas como botón, «<1»/0/«Sin estimación»), `SessionEditorSheetWiringTest` (D1: seis celdas ≥ 48 dp y «Eliminar» separado). Las aserciones no se rebajaron; los renders pasan a 360 × 880 dp (ventana de teléfono, antes 1800 dp) y se añaden los casos «Mis plantillas» y «Transferir».

No se ejecutó `compileBaseDebugAndroidTestKotlin` (la compilación de pruebas unitarias sí pasó; no hay cambios de firmas públicas usadas por instrumentadas).

## Renders (360 × 880, letra 100 % y 130 %)

Seleccionados en `C:\kw\u12\docs\screens-v2\renders\ED-E\` (D1…D8, 100 % y D1 al 130 %). El conjunto completo, también al 130 %, quedó en `C:\kw\u12\android-native\app\build\ede-renders\` (no versionado).

## Riesgos de integración

1. `TemplateCatalogBrowser` gana parámetros opcionales `mineOpen`/`onMineOpenChange` (sin romper llamadores) y `AssistantSheet.kt` los usa; `SessionEditorSheets.kt` y `SessionEditorScreen.kt` (zona de u14) **no se tocaron**.
2. `RulesSheet` ahora usa `onRuleLimitsChange`, `onAdvancedRuleLimitsChange` y `onApplyGlobalIntensityAdjustment`; `SessionEditorScreen` muestra un aviso «Límites guardados» en cada confirmación de campo y «Modificación global aplicada»: comprobar que no se duplique con el marco de u14.
3. «Agregar» pasa a «Añadir» solo en Transferir; Home y el entreno en vivo conservan «Agregar».
4. Sin QA visual en teléfono: solo Robolectric. En Reglas las filas plegadas quedan bajo el pliegue en el render (se ven al desplazar).

## Pendiente

- Sustituir `KpknSheetTokens`/`KpknSheetGlassChip` del cloner por piezas KPKN completas (hoy solo tipografía y tamaños).
- D3: la personalización por historial sigue siendo mínima (pendiente de MOT).
