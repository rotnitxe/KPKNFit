# Editor de sesiones · R1: el ORIGINAL refinado (2026-10-15)

**Origen.** El usuario rechazó el rediseño SE0–SE8 (archivado en `archive/session-editor-redesign-v1`) y eligió «original + refinar».
ER0 devuelve el editor de la PR11 (`8f0afc9dc`). Este documento dice cómo se refina. El mapa del original, con cada pantalla (E0–E27),
archivo y línea, está en `C:\kw\analysis\maps\session_editor_original_map.md`.

## 0. Reglas (del usuario, literales donde importa)
- **Refinar es** mejorar colores, diseño, fuentes y simetrías. **No es** quitar funciones ni esconderlas: «la UI original se hizo para
  dar las opciones completas al usuario de la forma más amigable posible».
- **Cambio de fondo SOLO donde haga falta**: «si hay algo que necesite un cambio radical, será ese punto en concreto. No todo».
  Puntos con libertad para buscar «mejores formas»:
  - las opciones de serie y sus modos;
  - convertir en superserie;
  - añadir aproximación y movilidad a un ejercicio;
  - el cardio, que está «mucho menos trabajado» y tiene que quedar totalmente profesional.
- **Asistente y Reglas** «no pueden quedar tan en segundo plano».
- **Portada:** «ya no será tan importante, ni se escogerán colores ni nada». Fuera la elección de portada y la de colores de grupo.
  Los datos existentes NO se borran; solo deja de pedirse.
- **Cambio entre días:** «fluido e intuitivo».
- **Guardado:** «las sesiones SIEMPRE se guardan automáticamente». Sin «Sin guardar» ni diálogo al salir.
- **Arriba:** la flecha atrás a la izquierda, el botón de día y sesión en el centro y los tres puntos a la derecha, en la misma fila.
- **Abajo:** una barra con tres botones bien integrados: Añadir ejercicios · Asistente · Reglas. Fuera el icono pequeño y la píldora
  de duración.
- **Impacto de la sesión:** va en el asistente. En el original ya era así (pestaña MÉTRICAS), y se queda así.

## 1. Piel (todo el editor)
Lenguaje KPKN UI v2 (`docs/design/KPKN_UI_V2.md` y `docs/screens-v1/briefs/LR1_COMUN.md`):
- **Superficies:** lienzo #000, módulo #111111 (radio 24, borde blanco al 7 %), `inset` #1A1A1A. Un chip sobre `inset` lleva filete
  blanco al 12 %.
- **Color:**
  - la selección y la acción principal, en tinta #F2EEE6 con texto negro;
  - el acento #F49A6E, solo para progreso;
  - los colores de los RINGS (Músculos #F49A6E, Energía #F7CF73, Columna #8FB2FF) solo donde hablan de eso;
  - `danger` para borrar;
  - `ok` #43D18C para «hecho» y «dentro del límite».
- **Tipografía:** Syne para títulos y cifras grandes; Inter para el texto. Cifras tabulares, espacio duro antes de unidades y «%»,
  coma decimal y «×» con espacios. Formatos con `KpknSetFormat` (descanso «1:30», «45 s», «4 × 6–8»).
- **Piezas de `ui/design` en lugar de Material:**
  - botones: `KpknPrimaryPill`, `KpknSecondaryPill`, `KpknTextAction`, `KpknIconButton`, `KpknDangerPill`;
  - selección: `KpknChip`, `KpknSwitch`, `KpknCheckRow`, `KpknSegmentedControl`;
  - números: `KpknTextField`, `KpknNumberCell`;
  - contenedores: `KpknModule`;
  - diálogos y hojas: `KpknAlertDialog` y `KpknSheet`, que no se tocan.
- **Fuera los colores sueltos del original:** #2A2A31, #FBBF24, #10B981, #34D399, #2196F3, #FF5252, #FFB300, el verde degradado del
  cardio, `DarkEditor*`. Todo pasa a tokens.
- **Simetría:** margen lateral de 16 dp en todo; los módulos, a 12 dp entre sí; las alturas de fila, iguales entre hermanos; los
  objetivos táctiles, ≥ 48 dp.

## 2. Marco (ER1a)

### 2.1 Fila superior, fija (56 dp)
- **Izquierda:** `KpknIconButton(Back)`. Guarda lo pendiente y sale (no hay diálogo).
- **Centro:** el selector de día y sesión, una píldora `inset` «‹  Jue · Pierna A  ›»:
  - las flechas pasan al día anterior o siguiente que tenga sesión;
  - tocar el texto abre la hoja «Semana», con TODO el navegador del original (E3):
    - los círculos de días desde el inicio de semana, con su punto y la marca de competición;
    - los chips de bloque;
    - las variantes A/B/C/D, por pulsación larga como hoy, o desde la hoja;
    - las sesiones de un día con varias (con la principal);
    - el roadmap;
    - crear una sesión en un día vacío.
- **Derecha:** `KpknIconButton(More)` con lo que había bajo la portada, salvo Reglas (va abajo) y la paleta (fuera): Transferir y
  Versiones, más lo que ya estaba en ese menú.
- Sin «Guardado» ni «Sin guardar».

### 2.2 Cabecera (dentro de la lista, sin portada)
- La etiqueta del protocolo como ceja, si existe.
- El nombre, editable en sitio, en Syne.
- La descripción, editable en sitio, en Inter `textMuted`.
- Una línea de resumen: «7 ejercicios · 22 series · ≈ 54 min». La duración sale de la única fuente que dejó SE12.
- **Cambio de día fluido:** deslizar la cabecera en horizontal (> 80 px, como el gesto del original) o tocar una flecha cambia de día.
  El contenido entra deslizándose desde el lado correspondiente (220 ms; con «reducir movimiento», un fundido).

### 2.3 Barra inferior, fija (isla de vidrio v2, como la de Inicio)
- **Tres botones iguales**, con icono y rótulo:
  - **Añadir:** abre el catálogo, como hoy;
  - **Asistente:** abre la hoja del asistente. Lleva un punto con el número de sugerencias si las hay;
  - **Reglas:** abre «Reglas y tiempo». Su icono lleva un anillo fino con lo estimado frente al límite (el del FAB de tiempo del
    original: `ok` por debajo del 85 %, Energía hasta el 108 % y `danger` por encima) y debajo, en pequeño, «54'».
- Sustituye al grupo de FAB arrastrable (`DraggableHeroFabGroup`), que desaparece. Lo que hacían sus dos botones lo hacen ahora
  Asistente y Reglas.
- La lista deja sitio debajo para que la barra no tape el último elemento.

### 2.4 Guardado automático
- Cada cambio se guarda solo en el programa (Room), al quedar quieto 1,2 s, por el mismo camino que «Guardar»: normalizar,
  reconciliar, cortafuegos del catálogo, validar y escribir.
- Al salir o al cambiar de sesión, se guarda lo pendiente antes.
- El borrador local del original se queda como red de seguridad.
- Si el cortafuegos o la validación bloquean:
  - sale un aviso fijo arriba con el mensaje humano de SE10 («No se pudo guardar: «X» no tiene su ficha… Cámbialo… o quítalo») y un
    acceso al ejercicio;
  - lo escrito NO se pierde;
  - se reintenta con el siguiente cambio.
- Fuera el diálogo «Salir del editor» y `hasUnsavedChanges` como texto.
- Las transferencias (Transferir) se aplican al confirmarlas, no «al guardar».

## 3. La lista (ER1b)

### 3.1 Espacios
Fuerza y cardio siguen siendo espacios, con su orden (`toggleCardioPlacement`), pero sin divisores de color: una ceja «FUERZA» o
«CARDIO» con «Mover al inicio» / «Mover al final».

### 3.2 Grupos (partes): visibles SIEMPRE, como en el original
- Cada grupo es un módulo.
- **Cabecera:**
  - asa (`KpknLineIcons.Grip`): pulsación larga para arrastrar el grupo;
  - el nombre, editable en sitio (Inter SemiBold 15, en tipo frase);
  - un resumen «3 ejercicios · 9 series»;
  - la flecha de plegar.
  - SIN punto de color ni panel de colores.
- Dentro van los ejercicios. Al pie, `KpknTextAction("+ Añadir ejercicio a <grupo>")`.
- **Borrar:** deslizando la cabecera, como en el original, con el mismo diálogo de dos opciones en `KpknAlertDialog`.
- **Crear:** «Nuevo grupo» al final del espacio de fuerza, como `KpknSecondaryPill`.
- Los grupos de movilidad del generador se ven igual, con su ceja «MOVILIDAD».

### 3.3 Ejercicio plegado
- Asa a la izquierda.
- Miniatura de 40 dp: la imagen del catálogo o el glifo del patrón.
- El nombre, en Inter SemiBold 15.
- Una línea de dosis con `KpknSetFormat`: «4 × 4–6 · 80 % 1RM · RIR 2 · 3:15». Si es unilateral, «por lado».
- La estrella de Meta en la fila, como en el original, en Energía si está activa.
- La flecha.
- **Deslizar para borrar**, como en el original, pero con «Deshacer» en el aviso en lugar de un diálogo.
- La pulsación larga en el nombre abre la ficha (y las acciones rápidas, ver 4.4).

### 3.4 Superseries
- Un bloque con un raíl vertical de 3 dp en tinta al 30 % que une a los miembros.
- **Cabecera:** «Superserie · 3 rondas · 1:00 / 2:00» y su «⋯» (rondas y descansos, «Convertir en opcional», «Disolver»,
  «Eliminar»).
- Los miembros, a la vista. Es el único cambio frente al original, que los escondía hasta desplegar.
- Se arrastra como bloque, como en el original.

## 4. El ejercicio desplegado (ER1c): aquí sí hay cambio de fondo

### 4.1 Preparación del ejercicio, a la vista
- Arriba del desplegado, en el mismo orden del original: Movilidad, Aproximación, Series.
- **Si no hay ni movilidad ni aproximación:** una fila con dos píldoras de borde punteado, «+ Movilidad» y «+ Aproximación». Ocupan
  una línea en vez de dos bloques vacíos.
- **Al añadir,** se convierten en su bloque:
  - Movilidad: el carrusel del original, con «Tiempo global» y «+ Agregar otra»;
  - Aproximación: sus filas, con «+ Añadir aproximación» (sube el 10 % y baja 2 reps, como el original), la carga sugerida y el
    descanso.
- Los bloques se pliegan con su ceja y llevan un contador: «APROXIMACIÓN · 3».

### 4.2 La barra de opciones del ejercicio
Una fila de píldoras con rótulo, todas a la vista:
- «Modo: Reps ▾»: abre una hoja con los 7 modos del original, cada uno con una línea que explica qué es;
- «Descanso 1:30» (en unilateral, «Series 1:30 · Lados 0:15»): el mismo selector encadenado del original;
- «Bilateral» / «Unilateral»: un `KpknSegmentedControl` pequeño. Antes estaba escondido en «⋮»;
- «⋯»: Medir ROM, Relacionar, Superserie, Unidad personalizada, Carga inteligente y Meta (también en la estrella).

Debajo, en el orden del original:
- «Carga inteligente», como módulo de una línea;
- en unilateral, «Lados»: orden I/D, iguales/aparte y descanso entre lados, en un solo módulo;
- el panel de relación, si existe.

### 4.3 Las series: un solo editor, completo
Sustituye al carrusel horizontal: con él no se veían las series a la vez.
- **Lista vertical de series.** Cada una es una fila compacta (56 dp) con:
  - su número;
  - el tipo de carga si no es «Carga externa»;
  - la dosis en Syne tabular: «80 kg × 6», «8–10», «45 s»;
  - la intensidad («RIR 2», «RPE 8», «Fallo», «80 %»);
  - la técnica si la hay («Drop × 2», «RP × 3»).
- **Tocar una fila** la despliega en sitio con TODOS los controles de la tarjeta del original:
  - el tipo de carga (Carga externa, Peso corporal, Lastre, Asistido);
  - las reps o el rango, o el tiempo, la distancia o la unidad, según el modo;
  - la intensidad por serie (RPE / RIR / Fallo, y su valor), o el %RM con su deslizador en modo RM;
  - AMRAP y AMRAP calibrador;
  - la carga calculada («X kg · N % RM») o «Usa carga inteligente»;
  - las técnicas: Drop-set con sus drops y Rest-pause con sus mini-series, con las notas del original.
  Solo una fila abierta a la vez.
- **Acciones de la fila abierta:**
  - «Aplicar a todas las series» (nuevo: copia la configuración de esta serie al resto);
  - «Duplicar»;
  - «Subir» y «Bajar» (en el original existían sin pintarse);
  - «Eliminar» (deja al menos una, como el original).
- **Unilateral:** cada serie muestra I y D en dos sub-filas con su etiqueta. Si falta un lado, «Añadir I/D». Sin rojo ni azul: el
  lado se dice con la letra.
- Al pie, «+ Añadir serie» (copia la última, como el original).
- **La sesión en vivo** («Editar series») usa la misma pieza (`ExerciseSetsCarousel`) y recibe el mismo editor, con la misma firma.

### 4.4 Acciones rápidas (pulsación larga en el nombre)
La hoja ya recibía calentamiento, movilidad, borrar y superserie, pero solo pintaba «Cambiar». Ahora pinta todas:
- Ficha;
- Cambiar;
- + Aproximación;
- + Movilidad;
- Crear superserie;
- Duplicar ejercicio (nuevo);
- Eliminar (en `danger`, con «Deshacer»).

### 4.5 Convertir en superserie
- Desde «⋯ → Superserie» o desde las acciones rápidas se abre «Crear superserie»: la lista de los ejercicios de la sesión con
  casillas. El actual va marcado.
- Se eligen de 2 a 4 y «Crear superserie con 3». Antes se añadía de uno en uno.
- Los descansos de partida son los del original (60 / 120 s), y se cambian en su cabecera.
- Sigue existiendo crearla desde el catálogo con la selección múltiple.

## 5. Cardio (ER1d): profesional
- **Espacio de cardio:** un módulo con su ceja y la fila de cada cardio con:
  - el icono de la máquina (`KpknLineIcons`, uno por tipo si existe);
  - el nombre;
  - la dosis en Syne: «20 min · Z2», «8 × 30 s / 30 s · RPE 9», «5 km · 5:30/km»;
  - la mini gráfica.
  Sin el degradado verde.
- **El editor de un cardio** (`CardioEditorCard`), refinado en el orden del original:
  - arriba, la frase de prescripción y la gráfica a todo el ancho (`CardioIntervalChart`: trabajo en el acento y recuperación en
    Columna, como en la sesión en vivo de LR1c1);
  - la forma, como `KpknSegmentedControl`: Continuo · Series · Intervalos;
    - Continuo: Tiempo, Distancia y GPS;
    - Series: calentamiento, N ×, esfuerzo, pausa, enfriamiento y HIIT/SIT;
    - Intervalos: el editor de bloques;
  - las magnitudes en celdas `inset` con cifras Syne y ruedas o reglas propias, sin los diálogos de Material;
  - la intensidad como deslizador KPKN (pista blanca al 8 %, progreso y pulgar en tinta), con la etiqueta de zona;
  - el historial y las plantillas destacadas como módulos con su gráfica;
  - «Avanzado», plegado.
- **El editor de intervalos** (`CardioIntervalsEditor`): bloques como filas de módulo, con tipo, duración, intensidad y repeticiones,
  «Duplicar bloque» y el arrastre si existe. Los pasos de ronda con `KpknStepperMetric`.
- Las plantillas HIIT, en una hoja con su gráfica cada una.

## 6. Asistente y Reglas (ER1e)
- **Asistente:** hoja alta con `KpknSegmentedControl`: Impacto · Sugerencias · Plantillas.
  - Impacto: la torre de RINGS (`KpknTowerRings`) con los tres valores; la duración; el volumen por músculo, plegable; las calorías.
  - Sugerencias: tarjetas con casilla, qué cambia y por qué, y «Aplicar selección» fija abajo.
  - Plantillas: el buscador y las tarjetas, con «Reemplazar» o «Añadir al final».
- **Reglas y tiempo:** `KpknSegmentedControl`: Reglas · Tiempo.
  - Reglas: el alcance, los valores de serie, los descansos, «Aplicar a nuevos elementos», las plantillas de reglas y la barra fija
    «Aplicar».
  - Tiempo: el reloj de sesión (Estimado, Límite y Gap) con el anillo de la barra inferior en grande, las sugerencias para entrar
    en el límite y los tiempos por grupo y por ejercicio.
- **El resto de hojas y diálogos**, solo con la piel: Meta, Unidad personalizada, Carga inteligente, ROM, Relacionar, Transferir,
  Versiones, Plantillas de sesión y la sesión vacía.
- **La hoja de portada (`CoverSheet`)** deja de abrirse. Su código se queda.

## 7. Paquetes y orden
| Paquete | Qué | Archivos (principales) |
|---|---|---|
| ER0 | Restaura la PR11 y porta la lógica | ver `briefs/ER0.md` |
| ER1a | Marco: fila superior, cabecera, cambio de día, barra inferior y guardado automático | `SessionEditorScreen.kt`, `SessionHero*.kt`, `SessionContextNavigator.kt`, `DraggableHeroFabGroup.kt`, VM de navegación y borradores |
| ER1b | Lista: espacios, grupos, ejercicio plegado y superseries (vista) | `SessionEditorScrollRenderer.kt`, `SessionListItems.kt`, `GroupEditorCard.kt`, el plegado de `ExerciseEditorCard.kt`, `SupersetGroupEditorCard.kt`, `SessionEditorVisuals.kt`, `SessionEditorEmptyState.kt` |
| ER1c | Ejercicio desplegado: preparación, barra de opciones, editor de series, acciones rápidas y crear superserie | el desplegado de `ExerciseEditorCard.kt`, `SessionEditorControls.kt`, `InlineSetRow*.kt`, `PreparationCarouselCards.kt`, `SupersetRoundsAndConfig.kt`, `SupersetCreatorAndManagerSheets.kt`, `TemplatesAndQuickActionSheets.kt` |
| ER1d | Cardio | `Cardio*.kt`, `HiitTemplatePickerDialog.kt` |
| ER1e | Asistente, Reglas y el resto de hojas y diálogos | `sheets/AssistantSheet.kt`, `sheets/RulesSheet.kt`, `sheets/ClonerSaveWarmupSheets.kt`, `sheets/HistorySheet.kt`, `sheets/RelationshipPickerSheet.kt`, `ExerciseEditorDialogs.kt` |

- Orden: ER0 → ER1a → **prueba en el teléfono y visto bueno del usuario** → ER1b, ER1c, ER1d y ER1e.
- ER1b y ER1c tocan el mismo `ExerciseEditorCard.kt`: ER1c va después de ER1b, o lo primero que hace ER1b es separar el plegado y el
  desplegado en dos archivos.
- Cada paquete trae su hoja de render (360 dp, al 100 % y al 130 %) y su `.dbg` lo compilo yo integrado.
