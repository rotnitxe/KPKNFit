# ER1a · Editor original refinado: el marco (arriba, cabecera, cambio de día, abajo y guardado automático) (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con ER0 integrado: el editor de la PR11). Lee ENTEROS:
- `docs/screens-v1/SESSION_EDITOR_R1.md`, sobre todo §0, §1 y §2. Es tu contrato;
- el mapa del original, `C:\kw\analysis\maps\session_editor_original_map.md` (E1–E4, E21, E24, E25 y E27, y la sección 3:
  Guardado);
- `docs/screens-v1/briefs/LR1_COMUN.md` (la piel) y `docs/wizard-v3/BRIEF_COMUN.md`.

**Regla de oro.** Es un REFINADO del original. El usuario rechazó dos rediseños que quitaban cosas. Todo lo que hacía el marco del
original se sigue pudiendo hacer: el navegador de días con bloques, variantes, roadmap, varias sesiones por día y crear sesión;
Transferir; Versiones; editar el nombre y la descripción en sitio; el asistente y las reglas con el tiempo. Solo cambia dónde está y
cómo se ve. Lo único que el usuario quitó: elegir portada y colores.

## Qué haces

### 1. Fila superior, fija (R1 §2.1)
- Sustituye a la falta de barra del original (E1).
- **Izquierda:** `KpknIconButton(Back)`. Guarda lo pendiente (punto 5) y sale.
- **Centro:** la píldora `inset` «‹  Jue · Pierna A  ›»:
  - las flechas, `KpknIconButton` pequeños dentro de la píldora, pasan al día anterior o siguiente que tenga sesión, por el mismo
    camino que el gesto del original (`requestSessionSwitch`);
  - tocar el texto abre la hoja «Semana» (`KpknSheet`), con TODO el navegador del original (`SessionContextNavigator`, E3):
    - círculos de días con su punto y la marca de competición;
    - chips de bloque;
    - las sesiones de un día con varias, con la principal;
    - el menú del roadmap;
    - el menú de variantes (por pulsación larga como hoy, y también con una fila «Variantes» visible en la hoja);
    - el diálogo de crear sesión en un día vacío.
  - Su piel: los círculos de día como `KpknChip` redondos; el elegido, en tinta; el punto de sesión, en `textMuted`; la competición,
    con un borde en Energía y la «C».
- **Derecha:** `KpknIconButton(More)` → `KpknContextMenu` con Transferir y Versiones (lo que había bajo la portada, E2b), y lo que el
  original tenga en otros menús de marco. La paleta y la portada, FUERA.
- Sin «Guardado» ni «Sin guardar».

### 2. Cabecera sin portada (R1 §2.2)
- Fuera la píldora con el fondo de la sesión (`SessionHero` E2, `SessionBackgroundLayer`, el halo) y la cabecera compacta de vidrio
  (E4): la fila superior fija ya hace su papel.
- Dentro de la lista, arriba:
  - la etiqueta del protocolo como ceja (`KpknType.eyebrow`, `textMuted`), si existe;
  - el NOMBRE editable en sitio (Syne, el tamaño según la longitud como el original, con el marcador «Nueva sesión»);
  - la DESCRIPCIÓN editable en sitio (Inter, `textMuted`, marcador «Añadir descripción»), que se envía igual que en el original
    (150 ms o al perder el foco);
  - una línea «7 ejercicios · 22 series · ≈ 54 min», con la duración de la fuente única (SE12).
- La hoja de portada (`CoverSheet`) deja de abrirse desde ningún sitio. El código y los datos de portada se quedan.

### 3. Cambio de día fluido (R1 §2.2)
- **Deslizar la cabecera** en horizontal más de 80 px cambia de día, como el gesto del original (`SessionHero` 160-226, que
  reutilizas).
- **La transición:** el contenido nuevo entra deslizándose desde el lado del día (`AnimatedContent` sobre la clave de sesión; hacia
  la izquierda si es el siguiente), 220 ms con `FastOutSlowIn`. Con «reducir movimiento», un fundido.
- La píldora del centro anima su texto igual.

### 4. Barra inferior, fija (R1 §2.3)
- Una isla de vidrio v2 (la de Inicio: busca `KpknIsland` / `kpknGlassStyle(ISLAND)`) con TRES botones iguales, icono y rótulo, cada
  uno con ≥ 48 dp:
  - **Añadir** (`KpknLineIcons.Plus`): lo mismo que «Añadir ejercicio» del original (abre el catálogo a página completa);
  - **Asistente**: abre la hoja del asistente (lo que hacía el FAB del ojo, E21). Si hay sugerencias, un punto con su número;
  - **Reglas**: abre «Reglas y tiempo» (lo que hacía el FAB del reloj). Su icono lleva un anillo fino con lo estimado frente al
    límite, con los umbrales del original: `ok` por debajo del 85 %, Energía hasta el 108 % y `danger` por encima. Debajo, en
    pequeño, los minutos («54'»). Sin límite, el anillo es neutro.
- Fuera `DraggableHeroFabGroup` y sus FAB (el ojo y el reloj): su función pasa a la barra.
- La lista deja sitio abajo para que la barra no tape el último elemento.
- La barra de la app sigue oculta en esta ruta, como en el original.

### 5. Guardado automático (R1 §2.4)
- **Cada cambio se guarda solo en el programa**, al quedar quieto 1,2 s, por el MISMO camino que el «Guardar» del original
  (`saveSession`, Nav ~325-692): normaliza, reconcilia, pasa el cortafuegos, valida y escribe con `mutateProgramNow`.
- Sin salir de la pantalla y sin snackbar de éxito.
- **Al salir** (flecha o Atrás del sistema) y al cambiar de sesión o de día, se guarda lo pendiente antes.
- **El borrador local** del original (`scheduleAutoSave`, SharedPreferences) se queda como red de seguridad.
- **Si el cortafuegos o la validación bloquean:**
  - sale un aviso FIJO arriba de la lista (módulo con borde `danger`) con el mensaje humano de SE10 («No se pudo guardar: «X» no
    tiene su ficha… Cámbialo… o quítalo») y un toque que lleva al ejercicio;
  - lo escrito NO se pierde;
  - se reintenta con el siguiente cambio;
  - salir con un bloqueo pendiente sale igual: el borrador local lo conserva y al volver se ve el aviso.
- Fuera el diálogo «Salir del editor» (E27) y cualquier texto de «sin guardar».
- **Transferir:** se aplica al confirmar en su hoja, no «al guardar». Dilo en la hoja: «Se copia ahora».
- **Prueba de carrera:** una edición mientras un guardado está en curso no se pierde; se guarda en la siguiente pasada.

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.sessioneditor.*`, `com.example.kpkn.screens.programdetail.*`,
  `com.example.kpkn.screens.home.*` y `*CatalogDetail*`.
- **Nuevas:**
  - guardado automático: un cambio se escribe en el programa tras 1,2 s sin tocar nada; cinco cambios seguidos dan un guardado;
  - salir con cambios pendientes los guarda;
  - un bloqueo del cortafuegos muestra el aviso y conserva lo escrito;
  - la carrera de arriba;
  - la flecha › lleva al siguiente día con sesión y la ‹ al anterior;
  - la barra inferior: Añadir abre el catálogo, Asistente el asistente y Reglas las reglas;
  - el anillo cambia de color en los umbrales.
- **Hoja de render** `render/SessionEditorFrameRenderSheetTest`, a 360 dp, al 100 % y al 130 %. PNG en `C:/kw/shots/screens/er1a`:
  - la fila superior;
  - la cabecera con nombre largo y corto;
  - la barra inferior (sin sugerencias, con 3 y con el tiempo pasado del límite);
  - la hoja «Semana» con 4 días con sesión, uno de competición y uno con dos sesiones;
  - el aviso de bloqueo.

## Permitido y prohibido
- **Permitido:**
  - `SessionEditorScreen.kt`;
  - `components/SessionHero*.kt`, `components/SessionContextNavigator.kt` y `components/DraggableHeroFabGroup.kt` (para retirarlo);
  - `SessionEditorViewModel*.kt` y `SessionEditorDraftPersistence.kt` (para el guardado);
  - `sheets/ClonerSaveWarmupSheets.kt` (Transferir);
  - `sheets/SessionEditorSheets.kt`, solo para quitar la entrada a la portada.
- **Prohibido:**
  - la lista, los grupos, los ejercicios, las series, el cardio, el asistente y las reglas por dentro: son ER1b–ER1e;
  - el catálogo;
  - `ui/design` y `ui/components`, salvo usarlos;
  - `screens/workout/`;
  - push;
  - el teléfono y el emulador.

## Comando
`KPKN_SLOT_PRIORITY=1 KPKN_RENDER_DIR=C:/kw/shots/screens/er1a bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "com.example.kpkn.screens.programdetail.*" --tests "com.example.kpkn.screens.home.*" --tests "*CatalogDetail*" --tests "*SessionEditorFrame*" > /c/kw/logs/screens-er1a-<n>.log 2>&1`

- Mira los PNG antes de lanzar otra corrida. Tope: 5 corridas.
- `.dbg` al final, con prioridad: `KPKN_SLOT_PRIORITY=1 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native -I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug > /c/kw/logs/screens-er1a-dbg-<n>.log 2>&1`.
  Cópialo a `C:/kw/shots/er1a/app-er1a-dbg.apk` y avísame con SendMessage a "main".

## Commit
- `style(sessioneditor): fila superior con día y sesión al centro, y cabecera sin portada (ER1a)`.
- `feat(sessioneditor): cambio de día con flechas, deslizando y con la semana en una hoja (ER1a)`.
- `style(sessioneditor): barra inferior Añadir · Asistente · Reglas con el anillo de tiempo (ER1a)`.
- `feat(sessioneditor): la sesión se guarda sola; fuera el diálogo de salir (ER1a)`.
- `test(sessioneditor): hoja de render del marco (ER1a)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambió por punto y dónde.
- El guardado: cómo funciona y sus pruebas.
- Los PNG, con tu comparación frente al original.
- Pruebas: número y resultado.
- La ruta del `.dbg`.
