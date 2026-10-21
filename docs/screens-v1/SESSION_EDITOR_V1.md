# Editor de sesiones v1 · rediseño con el lenguaje KPKN v2 (2026-10-13)

> **2026-10-15: el usuario rechazó el rediseño del editor y pidió volver al de la PR11 y refinarlo.** Esta especificación queda
> archivada con el código en la etiqueta `archive/session-editor-redesign-v1`. El refinado irá en `SESSION_EDITOR_R1.md`.

## Encargo y fuentes
- **Encargo del usuario:**
  - «Más adelante: rediseño del editor de sesiones y sesión en vivo».
  - «Nivel top mundial, que supere en diseño a Lyfta, MacroFactor Workouts, Hevy». Mientras no esté, decido yo.
- **Lenguaje común:** `docs/design/KPKN_UI_V2.md`.
- **Mapa del código actual:** `C:\kw\analysis\maps\sessioneditor.md`.
- **Sesión en vivo:** comparte piezas con este editor; está en `docs/screens-v1/LIVE_SESSION_V1.md`.

El editor es donde se diseña cada sesión de un programa. Va bastante más allá que la competencia: tiene partes, superseries,
bloques de cardio, preparación, reglas, plantillas, variantes y el asistente de impacto en los RINGS. Hoy esa potencia se ve
como ruido. **El objetivo es conservar TODA la potencia y que se lea en calma.**

## 0. Por qué así, frente a la competencia
- **Hevy y Lyfta:** una lista de ejercicios, cada uno con una tabla de series (serie, kg y repeticiones). Es rápido y claro, pero no
  tiene partes, ni impacto, ni reglas.
- **MacroFactor Workouts:** cada ejercicio se resume en una línea («4 × 6–8 @ RIR 2») y se despliega para editarlo. Su tipografía y
  su orden son excelentes, pero visualmente es frío.
- **KPKN hará:**
  - la línea de dosis de MacroFactor;
  - la tabla de series de Hevy, más cuidada: cifras Syne tabulares y celdas propias;
  - los módulos por parte;
  - el impacto de la sesión con la torre de los RINGS (nadie lo tiene);
  - una isla inferior con lo que se hace siempre: añadir, asistente y tiempo.
- **Hoy, en cifras:**
  - 0 iconos KPKN;
  - 181 iconos de Material;
  - 310 colores fijos;
  - 118 `Surface`;
  - 12 familias de chips;
  - 9 familias de campos;
  - un grupo de botones flotantes que se arrastra.

## 1. Qué falla hoy
| Zona | Problema | Gravedad |
|---|---|---|
| Identidad | Dos sistemas oscuros (`DarkEditor*` y `KpknSheetTokens`), 310 colores fijos, `FontWeight.Black` de Material y ninguna Syne | 🔴 |
| Jerarquía | Cada ejercicio es una tarjeta grande con carruseles. No hay un resumen de una línea: para ver la sesión hay que hacer mucho scroll | 🔴 |
| Acciones | El grupo de botones flotantes arrastrable (asistente y tiempo) es un patrón raro, tapa contenido y no dice qué hace | 🟡 |
| Series | Peso, repeticiones y RPE en `OutlinedTextField` sueltos dentro de carruseles; no hay una tabla clara | 🔴 |
| Catálogo | 3.500 líneas con 9 `FilterChip` y 13 botones; no hay miniatura del ejercicio en la fila | 🟡 |
| Hojas | Reglas, plantillas, portada, historial y clonar: mezcla de Material y piezas propias | 🟡 |
| Textos | «Defaults», «RINGS DE SESIÓN», unas 40 MAYÚSCULAS literales, emojis en HIIT, «...» en ASCII | 🟡 |
| Movimiento | No respeta el movimiento reducido | 🟢 |
| Deuda | 73 funciones muertas, dos selectores de catálogo y familias de chips y campos repetidas | 🟢 |

## 2. La pantalla, de arriba abajo
1. **Fila superior** sobre el negro, con `KpknTopRow`:
   - atrás (ChevronLeft) con el diálogo de salida sin guardar de hoy;
   - a la derecha, el estado del guardado en `caption` («Guardado», «Guardando…» o «Sin guardar»);
   - `KpknIconButton(More)`, que abre un menú de vidrio con:
     - «Portada»;
     - «Historial de versiones»;
     - «Copiar a otras sesiones»;
     - «Guardar como plantilla»;
     - «Reglas de la sesión».
2. **Cabecera de la sesión:**
   - el nombre en `display` (2 líneas como máximo). Al tocarlo se edita en sitio, con un `KpknTextField` del mismo tamaño;
   - la descripción en `body` `textMuted`, o «Añadir una descripción» en `textFaint`, también editable en sitio;
   - la línea meta en `caption` tabular: «6 ejercicios · 22 series · ≈ 58 min». La duración sale del estimador real;
   - **el navegador de sesiones** (lo de `SessionContextNavigator`): un `KpknChip` con el día y la sesión («Lun · Torso A») y
     `ChevronDown`. Abre una `KpknSheet` con las sesiones de la semana, las variantes y «Nueva sesión»;
   - la portada de la sesión no ocupa la cabecera: va como franja de color de 4 dp bajo la fila superior y en la portada de la hoja.
   - Al hacer scroll aparece la **isla superior** con el nombre, «≈ 58 min» y `Plus` (añadir ejercicio).
3. **Módulo «Impacto de la sesión»:**
   - la torre de los RINGS (`KpknTowerRings`) de 72 dp con lo que gastará la sesión;
   - a la derecha, tres cifras: «Músculos −32», «Energía −18» y «Columna −12», en `metricM` con su color;
   - si el motor no tiene datos, el módulo se oculta (sin cifras inventadas);
   - tocarlo abre el asistente (§5). Sustituye a `EstimatedRingsRow`.
4. **Preparación** (si la sesión tiene calentamiento o movilidad): un módulo con eyebrow «PREPARACIÓN» y una fila por elemento
   (nombre y «2 min» o «2 × 10»), con `KpknTextAction("Editar")`.
5. **El espacio de cardio y el de fuerza:** etiquetas de grupo sobre el negro («CARDIO», «FUERZA»), sin caja. El orden y la colocación
   del cardio (al inicio o al final) siguen la lógica de hoy.
6. **Una parte = un módulo** (`KpknModule`):
   - **cabecera:**
     - un punto de 10 dp con el color de la parte;
     - el nombre en `bodyStrong`;
     - «3 ejercicios · 10 series» en `caption`;
     - `KpknIconButton(More)` con «Renombrar», «Color», «Plegar» y «Eliminar parte» (en `danger`);
     - la pulsación larga en la cabecera arrastra la parte;
   - **una fila por ejercicio** (64 dp plegada):
     - a la izquierda, la miniatura del ejercicio: la imagen del catálogo si existe (círculo de 44 dp) o el glifo de su patrón;
     - el nombre en `bodyStrong`;
     - la dosis en `caption` tabular: «4 × 6–8 · RIR 2 · 2:30», con las variantes de RPE, %, tiempo o AMRAP;
     - `ChevronDown`;
     - la pulsación larga arrastra el ejercicio;
   - **desplegada:** la tabla de series (§3) y la fila de opciones del ejercicio (§3);
   - **superserie:** sus ejercicios van unidos por una barra vertical de 3 dp con el color de la parte, y una línea
     «Superserie · 3 rondas · 1:30 entre rondas» con su acción;
   - al pie del módulo, `KpknTextAction("Añadir ejercicio a <parte>")`.
   - Los ejercicios sueltos (fuera de una parte) van en un módulo sin cabecera de parte.
7. **Bloque de cardio = un módulo:**
   - el icono del tipo, el nombre y la dosis («30 min · zona 2» o «8 × 30 s / 90 s»);
   - en intervalos, una mini línea de tiempo de barras;
   - desplegado, los editores de hoy (intervalos, HIIT y magnitudes) con piel KPKN.
8. **Sesión vacía:** un `KpknEmptyState` con «¿Qué quieres entrenar en esta sesión?» y dos `KpknOptionRow` grandes:
   - «Fuerza · ejercicios, partes y superseries»;
   - «Cardio · al inicio o al final de la sesión».
9. **Isla inferior** (vidrio v2, sustituye al grupo de botones flotantes arrastrable):
   - `KpknPrimaryPill("Añadir ejercicio")` con `Plus`;
   - `KpknIconButton(Tower, "Asistente de la sesión")`;
   - un chip con «≈ 58 min», que abre el control de tiempo;
   - se esconde mientras hay una hoja abierta (`KpknModalTracker`) y al arrastrar.

## 3. Series y opciones de un ejercicio
- **La tabla de series** (pieza nueva compartida, `ui/design/KpknSetTable` o en `screens/sessioneditor/design/`):
  - **cabecera:** «Serie · kg · Reps · RIR · Descanso», en eyebrow `textFaint`. Las columnas cambian con el modo: RPE, % del 1RM,
    tiempo o distancia;
  - **cada fila** (48 dp):
    - el número de serie (o «A» de aproximación, «D» de drop);
    - celdas numéricas propias (`KpknNumberCell`, nueva): relleno `inset`, radio 12 dp, cifra en `bodyStrong` tabular y
      centrada. Al tocarlas se edita con teclado numérico, y ±1 con un deslizamiento corto en horizontal;
    - los rangos se escriben «6–8»;
  - `KpknTextAction("Añadir serie")` al pie. La pulsación larga en el número de serie abre un menú con «Tipo de serie»,
    «Duplicar» y «Eliminar»;
  - sin carruseles: las series se ven todas a la vez.
- **Opciones del ejercicio** (una fila de `KpknChip` debajo de la tabla):
  - **«Unilateral»:** abre un segmentado con «Ambos lados · Alterno · Por lados»;
  - **intensidad:** el modo en un segmentado («RIR · RPE · %»);
  - **«Carga inteligente»:** un `KpknSwitch` en su hoja;
  - **«Técnica»:** drop set o rest-pause, con su hoja;
  - **«Nota»;**
  - **«Cambiar ejercicio».**
- **Descanso:** la celda de descanso de la tabla abre el selector de descanso propio (`KpknRestPickerChain` con piel KPKN).

## 4. Catálogo de ejercicios (página completa)
- **Cabecera:**
  - `KpknTopRow` y el título «Añadir ejercicios» en `display`;
  - el buscador `KpknTextField` con `Search`;
  - una fila de `KpknChip` con filtros: músculo, material, patrón y «Mis ejercicios».
- **Una fila por ejercicio** (64 dp):
  - la miniatura de 48 dp (radio 12) con la imagen del ejercicio o su glifo;
  - el nombre en `bodyStrong`;
  - «Pecho · Barra» en `caption`;
  - a la derecha, un círculo de selección (multi-selección).
  - Tocar la fila abre su ficha (la de hoy, con piel KPKN).
- **Isla inferior de selección:** «Añadir 3» como principal, más el acceso al asistente de variantes cuando aplique.
- **Variantes y aspectos técnicos** (`CatalogSelectionWizard`): hojas con pasos numerados y opciones en `KpknOptionRow`.
- **Selectores:** queda UNO solo, el de página completa. La hoja antigua `ExercisePickerSheet` se retira cuando no la use nadie.

## 5. Asistente de la sesión (AUGE)
- **Formato:** una hoja casi a pantalla completa, «Asistente de la sesión», con `KpknSegmentedControl`: «Impacto · Sugerencias ·
  Plantillas».
- **Impacto:**
  - la torre de los RINGS grande;
  - el gasto por canal;
  - una fila por músculo con la barra del P4 (zonas y marca).
- **Sugerencias:** una fila por sugerencia con su porqué en `caption` y «Aplicar».
- **Plantillas:**
  - buscador;
  - módulos de plantilla con su fatiga;
  - «Aplicar» con confirmación.

## 6. Hojas
Todas usan `KpknSheet` (vidrio v2) y piezas KPKN:
- **Reglas:** módulos «Ajustes por defecto», «Límites» y «Tiempo». Los interruptores son `KpknSwitch` y las cifras, `KpknNumberCell`.
- **Portada:** rejilla de degradados y sólidos con vista previa.
- **Historial de versiones:** filas con la fecha y «Restaurar».
- **Copiar a otras sesiones:** `KpknCheckRow` por sesión.
- **Guardar como plantilla:** nombre y «Guardar».
- **Calentamiento y movilidad:** filas con su tiempo.
- **Catálogo de cardio:** filas con el icono del tipo.
- **Superseries:** crear, miembros y gestión.
- **Relación entre ejercicios.**
- **Diálogos:** `KpknAlertDialog` (los 3 `AlertDialog` de Material desaparecen).

## 7. Arrastre
- **Lógica:** el controlador (`SessionEditorDragController`) y su geometría NO cambian.
- **Al levantar:** escala 1,02, sombra suave y filete en tinta al 40 %.
- **Indicador de destino:** línea de 2 dp en tinta con extremos redondos.
- **Zona no válida:** un aviso compacto en `danger` («Aquí no se puede soltar»). Conserva el tag `session_editor_invalid_drop_banner`
  y el texto «Zona no válida» si la prueba lo exige.
- **Con movimiento reducido:** sin escala ni sombra animada.

## 8. Datos, honestidad y textos
- **Cifras:** la duración siempre «≈» y del estimador. El impacto en los RINGS solo con datos del motor.
- **Textos:**
  - «Ajustes por defecto», no «Defaults»;
  - «RINGS de la sesión»;
  - tipo frase, salvo los eyebrows;
  - sin emojis (HIIT, navegador, reglas);
  - «…» de un carácter;
  - «vínculo» con tilde.
- **Errores en lenguaje llano:** nunca `error.message`.

## 9. Piezas compartidas con la sesión en vivo (cuidado)
- **Las 29 piezas del editor que importa la sesión en vivo:**
  - `SessionLivePalette`;
  - `CardioMagnitudePickers` (`CardioValuePill` y las ruedas);
  - `CompactModeSelector`, `UnilateralModeSelector`, `SideOrderChip` y `ExerciseSetsCarousel`;
  - los valores por defecto de las técnicas;
  - `CatalogSelectionDraftBridge`.
- **Regla:** se reviste por dentro sin cambiar su firma, o se crea la pieza nueva y se migran los dos lados en el mismo paquete.
  Nunca se rompe la sesión en vivo.

## 10. Paquetes (ola SE, subagentes Haiku 5.5)
| Paquete | Qué | Depende de |
|---|---|---|
| SE0 | Limpieza: las 73 funciones muertas, `AddActions`, los imports duplicados y los textos en inglés y en MAYÚSCULAS literales. Cada borrado confirmado con Grep | — |
| SE1 | Piezas nuevas: `KpknNumberCell` y la tabla de series (`KpknSetTable`), con su hoja de render | D2 |
| SE2 | Fila superior, cabecera editable en sitio, navegador como chip y hoja, isla superior e isla inferior (fuera el grupo de botones flotantes) | SE0 |
| SE3 | Módulos de parte, filas de ejercicio con dosis y desplegado con la tabla y las opciones, superseries y visuales del arrastre | SE1, SE2 |
| SE4 | Impacto de la sesión con la torre y el asistente (Impacto, Sugerencias y Plantillas) | SE2 |
| SE5 | Cardio (bloque, intervalos, HIIT y magnitudes) y preparación, con piel KPKN y la firma intacta para la sesión en vivo | SE3 |
| SE6 | Catálogo de página completa (filas con miniatura, filtros, isla de selección y ficha) y el asistente de variantes; se retira el selector en hoja | SE1 |
| SE7 | Hojas: reglas, portada, historial, copiar, guardar plantilla y superseries; los `AlertDialog` de Material pasan a KPKN | SE2 |
| SE8 | Cierre: hojas de render de la pantalla entera (sesión de fuerza, con cardio, vacía, con superseries; 360 dp, 100 % y 130 %), accesibilidad, movimiento reducido y docs | todos |

## Estado (SE8)

SE8 cierra el editor: los pendientes que dejaron SE2 a SE7, la accesibilidad y el movimiento de la pantalla entera, las hojas de render y esta sección. La lógica del ViewModel y de los motores no cambia.

### Por punto de la especificación

| Punto | Estado en SE8 |
|---|---|
| §2.1 Fila superior, §2.2 cabecera, §2.4 preparación | Sin cambios de comportamiento. La cabecera y los módulos comparten el margen de 16 dp. |
| §2.3 Impacto de la sesión | Sin cambios (SE4). |
| §2.5 Espacio de cardio y de fuerza | El cardio y los ejercicios sueltos van en un módulo sin cabecera de parte. |
| §2.6 Partes y ejercicios | La fila no repite el nombre de la parte. Cada fila se lee entera para TalkBack y su miniatura tiene zona táctil de 48 dp. La superserie, ver «No se hizo». |
| §2.7 Cardio | «1 ronda» / «2 rondas». «Calent.» y «Enfr.» se abrevian y TalkBack lee el nombre entero. Los tramos de la mini línea miden 4 dp como mínimo. El RPE se elige con la regla KPKN. Las pausas de la gráfica, ver «No se hizo». |
| §2.8 Sesión vacía | Sin cambios (SE3a). |
| §2.9 Isla inferior | Sin cambios. A 130 % se apila en dos filas: diseño intencionado. |
| §3 Series y opciones | Tabla sin cambios (SE1, SE3b). El desplegado queda con cuatro chips; «Nota» y «Cambiar ejercicio» pasan al menú «⋯» de la fila de intensidad. |
| §4 Catálogo | Sin cambios en SE8. SE6a integrado; SE6b sigue en marcha. |
| §5 Asistente | Sin cambios (SE4, SE4b). En las plantillas, la leyenda de fatiga va una vez arriba de la lista. |
| §6 Hojas | «Meta del ejercicio» y «Unidad personalizada» pasan a `KpknSheet` con `KpknTextField`, `KpknSwitch` y píldoras. No quedan diálogos de Material en el editor. |
| §7 Arrastre | Sin cambios: el controlador y su geometría no se tocan. |
| §8 Datos y textos | «1 ronda» en plural. «Sesión origen» sin la fase. `KpknModuleHeader` baja la acción a su propia línea al 130 %. |
| §9 Piezas compartidas | `KpknLineIcons` gana `Flame`, `Joint` y `Link`. Nuevas: `KpknRuler` (regla de valores) y `SessionMotion` (movimiento del editor). `KpknSegmentedControl` admite `descriptions`. |

### Lo que no se hizo y por qué

- **«Editar» de la superserie (§2.6).** El panel en línea de `SupersetGroupEditorCard` hace más que `SupersetManagerSheet`: rondas y series de cada ronda, orden de los miembros, superserie opcional, configuración por ejercicio, añadir miembros y borrar la superserie con sus ejercicios. La hoja solo deshace o quita. Se queda el panel. La hoja se abre desde las acciones rápidas («Gestionar superserie»). Decisión pendiente: si la hoja absorbe el panel.
- **Pausas de la gráfica de intervalos en blanco al 12 %.** La gráfica (`CardioIntervalChart`) es de `screens/workout/` y no se toca. Anotado para LS5b.
- **«Calent.» y «Enfr.» escritos enteros.** A 13 sp no caben en cuatro segmentos y el control reduce la letra. Se quedan abreviados, con el nombre entero para TalkBack.
- **Movimiento de los selectores de catálogo.** `ExercisePickerCards`, `ExercisePickerSheet` y la ficha de SE6b quedan fuera del paquete.
- **Pantalla montada con el ViewModel.** La hoja de render monta las piezas del editor con datos de ejemplo. El ViewModel no se puede montar en Robolectric sin repositorios.
- **Miembros de una superserie fuera de una parte.** Siguen sin módulo propio. Solo el ejercicio suelto y el cardio van ahora en módulo.
- **`SessionEditorFocusUiTest`.** Monta su propio contenido: se actualizaron los textos y el tag del nombre a los de hoy («Asistente de la sesión», «Cerrar») y se mantiene el tag `assistant-sheet`. No prueba la pantalla real.

### Decisiones de los paquetes que cambian lo especificado

- Las acciones rápidas tienen seis filas: ficha, cambiar ejercicio, calentamiento, movilidad, superserie y, aparte y en peligro, eliminar con confirmación.
- El desplegado lleva cuatro chips. «Nota» y «Cambiar ejercicio» van al menú «⋯» de la fila de intensidad.
- El RPE se elige con una regla KPKN de diez marcas, por toque o arrastre, en lugar de un deslizador.
- El cardio y los ejercicios sueltos van en un módulo sin cabecera. La fila de la parte no repite el nombre de la parte.
- La leyenda de fatiga de las plantillas va una vez arriba de la lista, y las filas muestran solo las barras y sus cifras.

## QA en el teléfono (SE9)

Una ronda de teléfono sobre el `.dbg` de la fase A (`C:\kw\shots\se9\app-se9-dbg-1.apk`), con datos sembrados y borrados al final (`fin --clear-dbg`). La segunda ronda no se hizo: el teléfono se desconectó y la comprueba el coordinador con la rama integrada. Las capturas están en `C:\kw\shots\screens\se9-qa\`.

| Paso | Recorrido | Capturas |
|---|---|---|
| 0 | Siembra y arranque del arnés | `s1_activado.png`, `s2_a.png` |
| 1 | Permisos, avisos y calibración | `s3_b_notif.png`, `s5_d_back.png`, `s6_e_ahoranno.png`, `s7_f_entreno.png`, `s8_g_mastarde.png` |
| 2 | Programa → sesión → editor en reposo | `s9_h_scroll.png` … `s12_k_editor_abre.png`, `s13_l_editor_scroll1.png`, `s14_m_editor_scroll2.png` |
| 3 | Ejercicio: modo, RPE, reps, series, menú «⋯» | `s15_n_ex1_abierto.png` … `s22_t_menu_mas.png` |
| 4 | Catálogo, ficha y ejercicio añadido | `s28_x_catalogo.png`, `s29_y_ficha.png`, `s30_y2_ver_ficha.png`, `s32_y4_anadido_1.png`, `s33_y5_lista_tras_add.png` |
| 5 | Superserie | `s23_u_superserie_crear.png` … `s27_w2_superserie_final.png` |
| 6 | Cardio: bloque, plantillas 30/30, gráfica y mini línea | `s34_z_cardio_espacio.png` … `s41_z8_grafica_2.png` |
| 7 | Asistente de la sesión | `s42_aa_asistente.png`, `s43_ab_sugerencias.png`, `s44_ac_plantillas.png` |
| 8 | Meta del ejercicio y unidad personalizada | `s50_ai_menu_zancada.png` … `s62_av_unidad_guardada.png` |
| 9 | Reordenar arrastrando | `s63_aw_zancada_plegada.png`, `s64_ax_arrastre_plancha.png` |
| 10 | Sesión vacía | `s65_ay_selector_sesion.png`, `s66_az_sesion_nueva.png`, `s67_ba_sesion_vacia.png` |
| 11 | Guardar, volver, reabrir y salir sin guardar | `s68_bb_volver.png`, `s69_bc_semana_tras_volver.png`, `s71_be_editar_otra_vez.png`, `s74_bi_salir_sin_guardar.png`, `s75_bj_guardar_y_salir.png`, `s76_bk_cerrar_sin_guardar.png` |
| 12 | Vidrio y cierres: reglas, catálogo, plan de Nutrición | `s73_bg_reglas_sesion.png`, `s28_x_catalogo.png`, `s78_bm_nutricion_plan.png`, `s79_bn_nutricion_ajustes.png` |

**Arreglado** (commits sobre `b96b07b86`):
- `cbf6ac33c`: la hoja «Modo del ejercicio» lleva margen de 16 dp; «72,5 % 1RM» con espacio inseparable; sin ejercicios, la isla y la cabecera no muestran «≈ 1 min»; «Disolver» va en tinta KPKN y no en el amarillo de Material. Pruebas: `SessionModeSheetBodyTest` (nueva) y `SessionExerciseDoseTest` (actualizada).
- `d6c644a61`: asa visible al final de cada fila, con el mismo gesto de arrastre que la miniatura. Antes no había ninguna señal visible de que la fila se arrastra.
- `33a6df8b9`: corrige `d6c644a61`, que añadía un `Grip` duplicado y no compilaba. El set ya tenía el asa (`KpknLineIcons.Grip`, LS4).
- `4a251f829`: el aviso de éxito lleva la marca KPKN (`KpknLineIcons.Tower`) en lugar de ✅. Pruebas: `KpknSnackbarTest` (nueva).
- `56c46e7c5`: «10 ×» y «15 s» con espacio inseparable en la descripción de cardio y en el aviso del asistente. Pruebas: `CardioPrescriptionFormatterTest` (actualizada) y `SessionAssistantLabelTest` (nueva).

**Anotado, sin tocar:**
- **Guardado de la sesión** (VM; lo lleva el paquete SE10): «Guardar y salir» falla con `incomplete_v2_identity (catalogRevision, catalogDefinitionId, …)` para un ejercicio añadido desde el catálogo, y el banner muestra ese mensaje técnico. Pasos: Pierna A → añadir «Aperturas Inclinadas» desde el catálogo, crear superserie y añadir cardio 30/30 → «⋯» → atrás → «Guardar y salir» (`s75`). Program Detail no refleja los cambios (`s69`, `s70`) y la barra sigue en «Sin guardar» (`s71`).
- **Program Detail ≈ 60 min frente a ≈ 75 min del editor:** probablemente consecuencia del fallo de guardado. No se toca.
- **«Meta del ejercicio» cierra la hoja al tocar el campo «Meta 1RM kg»:** `s51` → `s52` (el valor no queda) → `s53` (la hoja reabre vacía) → `s54` (tocar el campo la cierra). La hoja «Unidad personalizada» no hace lo mismo (`s60`). No reproducible en código sin el teléfono: la sospecha está en el arrastre y desplazamiento anidado de `KpknSheet` con el teclado, sin confirmar.
- **Reordenar:** un `swipe` lento de 1500 ms desde «Plancha Abdominal» desplaza la lista (`s64`); `phone_run` no puede pulsar largo y arrastrar. El asa nueva es la vía visible. «Mover al final» solo mueve secciones (FUERZA, CARDIO), no ejercicios.
- **Superserie:** el panel expandido sale atenuado y la tarjeta «Ronda 1» se corta por la derecha (`s27`). Requiere revisar la pieza compartida de la superserie.
- **Emojis en los demás tipos de aviso:** `KpknSnackbar` sigue con ❌, 🏆 y 💡 en DANGER, ACHIEVEMENT y SUGGESTION. Es una pieza compartida; el cambio se limitó al éxito.

**Pruebas de la corrida final** (`C:\kw\logs\screens-se9-5.log`): `:app:compileBaseDebugKotlin`, `:app:compileBaseDebugAndroidTestKotlin` y `:app:testBaseDebugUnitTest` con `sessioneditor.*`, `programdetail.*`, `ui.*`, `domain.cardio.*`, `domain.sessionassistant.*` y `*RegexPortability*`. BUILD SUCCESSFUL: 840 pruebas, 0 fallos, 0 errores.
