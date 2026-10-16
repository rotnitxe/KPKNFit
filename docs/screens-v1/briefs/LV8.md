# LV8 · La tarjeta de la serie: esfuerzo con fuego y obligatorio, «Avanzado», «Modo visual» con «Expandir», ficha y álbum

Rama `screens/lv8`, worktree `C:\kw\u4` (ya compilado), desde `feat/sessions-v1` `975431878`. Lee antes `LV_COMUN.md`,
`LV_COMUN2.md`, los mapas de `docs/screens-v1/maps/` y los prototipos aprobados: `Main.dc.html` (la tarjeta y el slider de
esfuerzo), `A2-Avanzado`, `A3-ModoVisual`, `G3-ModoVisualExpandido`, `A4-Album` y el `README.md` de la carpeta.

**Ley 1 (manda): la tarjeta NUNCA cambia de tamaño** en ningún estado (anverso, «Avanzado», «Modo visual», teclado, drops,
ejercicios con ROM). Lo que no cabe se desplaza DENTRO de la tarjeta o va a «Expandir». Cada fase añade una prueba que mide la
altura de la tarjeta en todos sus estados y exige que sea la misma.

## Fase A · Esfuerzo (Main) y RIR/RPE obligatorio

### A1. El slider de esfuerzo con fuego
Sustituye el carrusel de intensidad (`IntensityStepperBlock` + `WorkoutHorizontalWheelPicker`) por un slider propio
(`components/EffortFireSlider.kt`) con el aspecto de `Main.dc.html`:
- cabecera «ESFUERZO» y a la derecha «RIR 2 · exigente» (valor + palabra);
- pista de 46 dp, radio completo, fondo `#1A1A1A` con sombra interior; relleno desde la izquierda hasta el pulgar con el
  degradado del nivel (de gris frío a ascua y fuego: los 6 degradados de Main, interpolados para escalas más largas); partículas
  de fuego que suben dentro del relleno y que crecen con el esfuerzo (0 en lo cómodo, muchas en el fallo); pulgar de 36 dp
  `#F2EEE6`;
- **los valores son los de la app** (ley 2): la lista exacta que da hoy `buildIntensityCarouselItems` para esa serie (RIR, RPE,
  fallo planificado o SOLO_RM con «—»), en el mismo orden. Se arrastra con ajuste a cada valor (háptica suave en cada paso) y
  también se toca un punto de la pista. Debajo, etiquetas de algunos valores (el mínimo, el plan, el máximo y «Fallo»), sin
  amontonarse a 360 dp ni al 130 %;
- **empieza en el MÍNIMO** de la escala (el extremo izquierdo: el menos intenso de la lista), nunca en el plan;
- **el objetivo del plan** es una marca tenue vertical sobre la pista con «· plan» en su etiqueta; no se preselecciona;
- las palabras: reutiliza `difficultyLabel` o la que tenga la app para cada valor; si no hay, una tabla corta en el archivo
  («cómodo», «firme», «exigente», «muy duro», «al límite», «todo»);
- «reducir movimiento»: sin partículas, el degradado sí;
- accesibilidad: rol de slider con `setProgress`, descripción «Esfuerzo RIR 2, exigente».

### A2. RIR/RPE obligatorio
Decisión del usuario del 16-oct: el esfuerzo de cada serie pasa a ser OBLIGATORIO.
- El slider se muestra en TODAS las series de fuerza, también sin intensidad planificada (hoy en ese caso solo había reps y se
  preguntaba después «Qué tan intenso fue»: esa pregunta la quita LV6). El modo por defecto sale de
  `resolveDefaultReportedIntensityMode`.
- Mientras el slider no se ha tocado en esa serie, el ✓ no registra: vibra suave, el slider hace un pequeño balanceo y aparece
  bajo él «Marca tu esfuerzo para registrar la serie» (13 sp, en la misma altura reservada: la tarjeta no crece). Tocar el
  mínimo cuenta como elegirlo.
- Se conserva todo lo demás de `submitRecord` y sus validaciones (peso corporal en ASSISTED, carga, reps/tiempo).
- **Confirma con una prueba el bug del mapa** (RPE 0 fantasma en series sin intensidad planificada) y elimínalo: una serie nunca
  se registra con una intensidad que la persona no eligió.
- Fuera de la tarjeta (voz, notificación «Completar serie», ultrarrápido) no cambia nada: anótalo en el informe.

### A3. Regresión de la ronda anterior
LV3 quitó la rama `inlineChipsInField` (los chips de carga junto al valor en los ejercicios con ROM, `exercise.trackRom`) y dejó
`inlineRomLoadOptions` sin uso. Comprueba la tarjeta con ROM: si crece o pierde algo frente a la de sin ROM, recupera la
disposición en línea con el aspecto nuevo; si no hace falta, borra el código muerto. Prueba de altura con ROM.

## Fase B · «Avanzado», «Modo visual», «Expandir», ficha y álbum

### B1. «Avanzado» (A2)
El reverso `SetCardTechniqueBack` con el aspecto de A2: segmentado «Normal · Drop-set · Rest-pause» (sin «Myo-reps»: no existe),
la **cadena de la serie** en horizontal (Principal → Drop 1 → Drop 2 → +) con solo el eslabón activo abierto en una fila (kg y
reps con −/+), «ASÍ SE VE UN REST-PAUSE» (la explicación en una línea de bloques) y «NOTA DE LA SERIE». Los chips «−20 %/−30 %»
solo si la app ya sugiere la carga del drop. Se conserva todo lo que hoy hace el reverso (fallo, notas, drop y rest-pause) con las
mismas funciones del ViewModel.

### B2. «Modo visual» (A3) con «Expandir» (G3)
- En la tarjeta (A3): la cámara usa el espacio que haya, «Foto», «Vídeo», «Ficha», «Ver álbum» y la línea «Se guarda en el álbum
  con la serie 3 · 95 kg × 6 · RIR 2.» (datos reales). Se conservan «Galería», las miniaturas de la sesión y la imagen «Técnica
  KPKN» (pequeña).
- **«Expandir»**: con una animación de expansión (la tarjeta crece desde su sitio, 320 ms, curva estándar), «Modo visual» pasa a
  casi toda la pantalla ENCIMA de todo (G3: cabecera «Modo visual · serie 3», «95 kg × 6 · RIR 2», cámara grande, los mismos
  botones). Plegar (o Atrás) vuelve a la tarjeta con la animación inversa. La tarjeta debajo no cambia de tamaño.
- La cámara: CameraX con `isSettledPage`, `stopIfRecording` y sin liberarla mientras se graba, también al expandir y plegar
  (pruébalo).

### B3. «Ficha»
El botón «Ficha» abre la ficha del ejercicio en overlay propio: usa la ficha del catálogo que ya existe (`CatalogDetailContent`
vía `sessionExerciseDetailModel`, sin pie), encima de todo. Otro paquete (LV14) la rediseña para toda la app: tú solo la abres.

### B4. Álbum por ejercicio (A4)
- Hoy el álbum es solo de la sesión (`WorkoutSessionAlbumSheet`, `ModalBottomSheet` de Material) y `listForExercise` no se usa.
- Nuevo álbum del ejercicio con el aspecto de A4: «Álbum · Sentadilla trasera · 9 registros», filtros «Todo · Fotos · Vídeos»,
  agrupado por fecha («HOY · JUEVES 15 OCT», «LUNES 12 OCT»), cada elemento con «Serie 3 · 95 kg × 6 · RIR 2 · Vídeo · 18:42» y
  «Marca» si `isPr`, duración en los vídeos; «Seleccionar» para varios.
- Datos: `WorkoutMediaRepository.listForExercise` (por `canonicalExerciseId`, entre sesiones).
- **Borrar con «Deshacer»**: al borrar, el elemento desaparece y sale «Vídeo borrado · Deshacer» ~5 s; el borrado definitivo
  (`delete`, filas y archivos) ocurre al caducar el aviso o al salir del álbum, nunca antes. Si se deshace, no se toca nada.
  Implementa el aplazamiento en el repositorio o en un controlador de `data/` (no en la UI) y pruébalo (deshacer, caducar, salir).
- Se abre desde «Ver álbum» (tarjeta y expandido) y desde el icono de álbum de hoy. Se conserva la vista previa a pantalla
  completa.

## Lo que NO se puede romper
`submitRecord` y las validaciones; `RecordActionHolder`; el ✓ flotante (`WorkoutRecordFab`) y su sitio; la paridad de carga
inteligente; la cámara (CameraX) y los medios de la sesión (`mediaSessionKey`); las caras de la tarjeta (`SetCardFace`) y sus
atajos (récord → modo visual).

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.workout.*`, `com.example.kpkn.data.repository.*` y `com.example.kpkn.data.media.*`
  que toques.
- Nuevas: altura constante de la tarjeta en todos los estados (A y B); el slider (empieza en el mínimo, valores = lista de la app
  en cada modo, marca del plan, tocar y arrastrar); el ✓ bloqueado sin esfuerzo y desbloqueado al tocar el mínimo; sin RPE 0
  fantasma; la cadena del avanzado; expandir y plegar sin perder la cámara; álbum por ejercicio (orden, filtros) y borrar con
  deshacer.
- Hoja de render (amplía `render/LiveSessionRefineRenderSheetTest` o crea `render/LiveCardRenderSheetTest`), 360 dp, 100 % y
  130 %: slider en el mínimo, en el plan y en el fallo (dos fotogramas del fuego); aviso de esfuerzo obligatorio; avanzado con
  drop 1 abierto; modo visual en la tarjeta y expandido; álbum con dos fechas y el aviso de deshacer.

## Permitido y prohibido
- **Permitido:** `SetExecutionCard.kt`, `SetCardTechniqueBack.kt`, `SetCardExerciseMediaBack.kt`, `IntensityCarouselLogic.kt`
  (solo lo necesario), archivos nuevos de la tarjeta y del álbum, `WorkoutSessionAlbumSheet.kt`, `WorkoutMediaRepository.kt` y
  `WorkoutMediaCaptureController.kt` (solo el álbum por ejercicio y el borrado aplazado), en `WorkoutViewModel` solo lo mínimo,
  pruebas.
- **Prohibido:** el descanso (LV6), el relator (LV2), la cabecera, el estado previo, la barra inferior y el cockpit, las hojas de
  cierre, `KpknTowerRings.kt`, la ficha del catálogo por dentro (LV14).

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lv8 bash /c/kw/tools/gradle_slot.sh /c/kw/u4/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.data.repository.WorkoutMedia*" --tests "*LiveSessionRefineRenderSheet*" --tests "*LiveCardRenderSheet*" > /c/kw/logs/lv8-<n>.log 2>&1`

Tope: 5 corridas en la fase A y 5 en la fase B. Mira tus PNG antes de cada corrida nueva.

## Commits
- `feat(workout): el esfuerzo con un slider de fuego que empieza en el mínimo y marca el plan (LV8)`.
- `feat(workout): registrar una serie exige marcar su esfuerzo (LV8)`.
- `feat(workout): «Avanzado» con la cadena de la serie (LV8)`.
- `feat(workout): «Modo visual» cabe en la tarjeta y se expande encima de todo, con la ficha a un toque (LV8)`.
- `feat(workout): álbum por ejercicio con filtros y borrar con deshacer (LV8)`.
