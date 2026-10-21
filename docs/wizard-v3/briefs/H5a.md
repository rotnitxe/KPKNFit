# H5a · Importar desde la app: lo que salió en la revisión visual del teléfono (2026-10-12)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3`, versión 40). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`,
`docs/IMPORTADOR_PROGRAMAS.md` (§5) y `docs/wizard-v3/briefs/H2a.md`, `H2b.md` y `H2c.md`. Tareas Gradle `...Base...`. Español neutro y sin
género gramatical.

## De dónde sale
Revisión visual en el Galaxy Z Flip5 (pantalla de 360 dp) del flujo Programas → «+ Nuevo» → «Importar»:
- pegar texto: vista previa, editor de ejercicio, avisos, revisión y guardado;
- un PDF de 5 páginas leído por OCR.

El análisis funciona; fallan la maqueta y el guardado desde la app.

## Cambios
### 1. Que nada quede tapado (`ImportProgramScreen.kt` y `MainActivity.kt`)
- **Problema:**
  - la barra flotante de la app (Inicio, Entreno, Nutrición y Cuerpo) tapa «Empezar de nuevo» y las acciones del editor;
  - «Guardar programa» queda debajo de la barra de navegación del sistema: solo asoman unos 20 px;
  - arriba, «Atrás» queda pegado a la barra de estado y, al desplazar, el contenido pasa por debajo de la hora.
- **`MainActivity.kt`, UNA línea:** añade `currentRoute == KpknRoute.ImportProgram.route ||` a la lista de `isFullscreenWizard`. Es la
  misma regla que ya oculta la barra en el editor de programas y en el asistente.
- **En la pantalla:**
  - `statusBarsPadding()` arriba y `navigationBarsPadding()` en la barra inferior de acciones;
  - el contenido desplazable deja abajo el hueco de esa barra, así que lo último («Empezar de nuevo») se puede leer y tocar;
  - el contenido no se dibuja bajo la barra de estado.

### 2. Avisos plegados cuando son muchos (`ImportWarningsPanel.kt`)
- **Problema:** con un PDF salen 47 avisos uno detrás de otro ANTES del programa: hay que desplazar muchísimo para ver los días.
- **Regla:**
  - Con 3 avisos sin revisar o menos, todo como hoy.
  - Con más de 3, el panel empieza plegado y solo muestra:
    - la línea «47 avisos sin revisar.»;
    - las acciones de texto «Ver avisos» e «Ignorar todos».
  - «Ver avisos» despliega la lista de H2c tal cual (con «Añadir como ejercicio» e «Ignorar»). Plegada o desplegada, la decisión no se
    guarda: al volver a la pantalla empieza plegada.
  - El «Ver avisos» de la revisión final «Falta revisar» despliega el panel y lleva hasta él.
  - El aviso de imagen («Lo leímos de una imagen: revisa los números antes de guardar.») SIEMPRE se ve, aunque el panel esté plegado,
    con su «Entendido».

### 3. Sin texto repetido en un aviso (`ImportWarningsPanel.kt`)
«No se entiende «Hacer 3 vueltas»» va seguido hoy de «Tu archivo dice «Hacer 3 vueltas».». Si el mensaje ya contiene el texto de origen
entre comillas angulares, la línea «Tu archivo dice …» no se pinta.

### 4. Días de entrenamiento al guardar desde la app (`ImportProgramViewModel.kt`, `ImportProgramScreen.kt` o `ImportReview.kt`)
- **Problema:** desde la app se guarda con `weekdays = emptyList()`, así que todas las sesiones caen el lunes («Lunes · 3 sesiones»). En el
  alta no pasa: allí está el paso «Así queda tu semana».
- **Diseño:**
  - Encima de «Guardar programa» y SOLO en la ruta de la app, una sección «Días de entrenamiento» con 7 interruptores de día: «L M X J V S D».
  - Objetivo táctil de 48 dp y estado elegido con el color de acento de la pantalla.
  - Selección inicial, según el número de días de la semana con más días del borrador:

    | Días | Selección |
    |---|---|
    | 1 | L |
    | 2 | L J |
    | 3 | L X V |
    | 4 | L M J V |
    | 5 | L M X J V |
    | 6 | L a S |
    | 7 | todos |

  - Línea de ayuda debajo:
    - con tantos días como sesiones: «3 sesiones en 3 días.»;
    - con menos días: «Algunos días tendrán más de una sesión.»;
    - con más días: nada.
  - Con 0 días elegidos no se puede guardar: la revisión dice «Elige al menos un día de entrenamiento.».
- **Cableado:** la selección vive en el estado del VM (también tras girar el teléfono) y llega a `ImportedProgramBuilder.build(weekdays = …)`,
  que ya reparte con `assignWeekdays`. La ruta del asistente no cambia.

### 5. Texto de la entrada (`screens/programs/CreateProgramTemplateSheet.kt:315`)
Hoy dice «Importar (Excel, CSV o texto)» / «Trae tu programa desde un archivo o pégalo.». Pasa a:
- título «Importar un programa»;
- subtítulo «Desde Excel, Word, PDF, texto o una foto de tu rutina.».

### 6. Textos de la app que se vieron en la revisión
- `screens/programdetail/components/DayView.kt:761`: «3 sesiónes» → «1 sesión» o «N sesiones».
- `screens/programs/ProgramsScreen.kt:500` y `:552`:
  - «1 semanas» → «1 semana»;
  - con una sola sesión, «1 sesión».
  - Usa una función pequeña y probada para el plural.
- `ProgramsScreen.kt:122`: el título «PROGRAMAS» se parte como «PROGRAMA / S» junto al botón «+ Nuevo».
  - Debe caber en una línea a 360 dp y con letra al 130 %: que se encoja hasta un mínimo o que el botón baje a su propia fila.
  - Nunca se parte una palabra.
- **Título de sesión cortado en el detalle del programa** («Day 2 - Upper» sale como «Day 2 -» junto a la etiqueta «Baja»): hasta 2 líneas
  y elipsis al final. Busca el composable de la tarjeta de sesión en `screens/programdetail/components/`.

### 7. Aviso de permisos que reaparece (`MainActivity.kt`, unas 3 líneas)
- **Problema:** «Permisos Recomendados» vuelve a salir CADA vez que la app pasa a primer plano mientras falte el permiso de notificaciones;
  por ejemplo, al volver del selector de archivos, tapa la lectura del PDF.
- **Cambio:**
  - una variable `rememberSaveable` «omitido en esta sesión»: «Omitir por ahora» la pone a `true` y el `ON_RESUME` no vuelve a mostrar el
    aviso mientras valga `true`;
  - al abrir la app de nuevo (proceso nuevo), se puede volver a ver.

## Pruebas
- **Robolectric:**
  - con más de 3 avisos el panel empieza plegado y «Ver avisos» lo despliega; con 3 o menos, como hoy;
  - el aviso de imagen se ve plegado;
  - «Tu archivo dice» no se repite;
  - selección inicial de días para 1 a 7 sesiones, y el builder recibe esos días (con 3 sesiones: lunes, miércoles y viernes);
  - con 0 días no se guarda;
  - el título de la entrada nueva.
- **Unitarias:** plural de sesión y semana.
- **Hoja de render** (`KPKN_RENDER_DIR`, ancho 360 dp y letra al 100 % y al 130 %), con PNG para revisar:
  - la vista previa con la barra de guardar al final;
  - los avisos plegados y desplegados;
  - los días;
  - la cabecera de Programas;
  - una tarjeta de sesión con título largo.
- Las pruebas que ya existían de `screens.programs.import.*`, `ProgramsScreen` y `DayView` siguen en verde.

## Comando
`bash /c/kw/tools/gradle_slot.sh <tu-worktree>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.programs.*" --tests "*DayView*" --tests "*ProgramImportStep*" --tests "*ImportActivation*"`

## Commit
Por rutas, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- El analizador, los lectores, el emparejador y el reconocedor: H5b va a la vez.
- En `MainActivity.kt`, cualquier cosa fuera de los puntos 1 y 7 (como mucho 5 líneas en total).
- Push.

## Informe
- Cada punto, con su captura de la hoja de render.
- Pruebas: número y resultado.
- Lo que no se pudo hacer y por qué.
