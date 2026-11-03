# Catálogo · punto 3 · «Aplicar plantilla o método» como catálogo (prefijo `ct`)

Archivos: `src/scenes-ct.js`, `src/ct.css`. El bloque `CT_META` del principio de `scenes-ct.js` está generado de `PlanEditorialTable.kt` (nombre, resumen,
nivel y autor de las 38 entradas); no se edita a mano.

## 1. Código real que corresponde
- Hoja de hoy: `screens/programdetail/design/structure/ProgramTemplatesSheet.kt` (`ProgramTemplatesSheet`, `ProgramTemplatesContent`, `ProgramLibraryItem`,
  `libraryFrequencyLine`, `libraryMethodLine`) y su cableado `screens/programdetail/components/editor/LibrarySection.kt` (`programLibrary()`: plantillas = entradas
  con `template`; métodos = entradas `CatalogSource.PROTOCOL` sin plantilla y con `visibleProtocolOf`; orden `libraryOrder` por `rank`).
- Datos: `data/programs/ProgramTemplates.kt` (10 plantillas: semanas, bloques, `defaultSplit`, `trackLabel`), `data/programs/PlanEditorialTable.kt`
  (nombre editorial, resumen, niveles, autor, notas), `data/protocols/definitions/*` (recetas; aquí `KpknNativeSbd4.kt` y `TexasWendlerProtocols.kt` para las progresiones).
- Aplicar: `ProgramDetailViewModel.applyProgramTemplate` / `applyProtocolOverwrite`, `domain/training/ProgramTemplateEngine.applyTemplate` y
  `ProgramProtocolEngine.applyProtocol`; versiones: `pushProgramSnapshot`, `restoreProgramSnapshot`, `ProgramHistorySheet`.
- Materia prima visual: `program-detail-v1/src/covers.js` (`coverSvg`: perfil, espiral, eco, ondas, relieve, campo, órbitas), ficha `pf-*` de `arreglos-v1`.

## 2. Escena → pieza
| Escena | Pieza de la app |
|---|---|
| `ct-portada`, `ct-lista`, `ct-plantillas`, `ct-metodos` | `ProgramTemplatesContent` (cabecera, grupos, filas) |
| `ct-filtro`, `ct-busqueda`, `ct-sin-resultados` | nuevo sobre el mismo listado; filtros de días/duración ya existen en `libraryVisibleEntries` (Planes) |
| `ct-ficha-plantilla`, `ct-ficha-blanca` | nueva ficha (hoy tocar una fila aplica) con `ProgramTemplateOption` + `PlanEditorial` |
| `ct-ficha-metodo`, `ct-ficha-ciclo`, `ct-metodo-requisitos`, `ct-ciclo-requisitos`, `ct-ficha-complemento` | nueva ficha de método con `Protocol.recipe` |
| `ct-aplicar-copia`, `ct-aplicar-sustituir`, `ct-vacio`, `ct-bloqueado` | el interruptor `StructureSwitch` de la hoja + `ApplyStrategy` |
| `ct-confirmar`, `ct-aplicando`, `ct-hecho-sustituir`, `ct-hecho-copia` | `applyProgramTemplate/applyProtocolOverwrite` (snackbar de hoy) |

## 3. Propuestas y decisiones (con su razón)
- `new` Hoja a pantalla casi completa con cabecera fija (título, búsqueda, segmentos Todo/Plantillas/Métodos, 4 filtros) y lista que se desplaza: 38 filas necesitan buscador.
- `new` Destacados: las tres entradas de menor `rank` de cada objetivo (Bloque de los tres básicos, Fuerza y músculo avanzado, Torso y pierna). Regla, no capricho.
- `move` Nombres: la lista pasa del nombre técnico (`template.name`/`protocol.name`: «PL Principiante 3 días · 12 sem») al editorial (`displayName`, el de Planes).
  El técnico sigue buscable y se muestra al pie de la ficha. Las versiones guardadas («Antes de …») y el nombre de la copia deberían usar también `displayName`.
- `new` Frases cortas de fila (campo `tag` de las 38): escritas por mí a partir de los resúmenes; hay que guardarlas en `PlanEditorial` (campo nuevo `tagline`) o derivarlas.
- Color con significado: objetivo → coral Fuerza, verde Músculo, ámbar Fuerza y músculo (portada y rótulo). Estilo de portada: perfil (plantillas por fases), espiral (ciclos que se repiten), ondas/relieve/campo/órbitas (resto), eco con la cifra de semanas (en blanco). Es arte.
- `out` Interruptor «Sustituir semanas con sesiones» de la hoja; pasa a la elección de la ficha.
- `ask` El texto de hoy («Rellena las semanas vacías. Si ya tienen sesiones, eliges si sustituirlas») no es lo que hace el motor: sin ninguna sesión rellena la estructura; con alguna sesión
  y sin sustituir CREA UNA COPIA (nombre «programa · plantilla»); sustituir es todo-o-nada, no por semana. Los métodos con interruptor apagado siempre copian, aunque el programa esté vacío.
  La ficha nombra lo que pasa: «Crear una copia» (de salida, más seguro) o «Sustituir mi programa».
- `new` Confirmación al sustituir con consecuencias reales del código: versión guardada antes, ciclos/fechas clave/pausas reiniciados (`loops`, `events`, `calendarBreaks` vacíos), modo y reparto
  semanal cambian (`mode` por `trackLabel`, `selectedSplitId` = `defaultSplit`). «El historial de sesiones hechas no se toca»: confirmar.
- `new` Resumen del resultado en el aviso («Plantilla aplicada · 12 semanas · 3 bloques») y `Deshacer` = restaurar la versión guardada antes de aplicar.
- `keep` El bloqueo con sesión en curso (`SESSION_IN_PROGRESS_MESSAGE`), ahora visible antes de tocar.
- Sin cargando de lista: `programLibrary()` es síncrono. El único «cargando» real es aplicar (corre en `Dispatchers.Default`).
- Ficha de método: gráfico por semanas del % de TM y series × repeticiones (datos reales de `KpknNativeSbd4`); tabla de ciclo (5/3/1, de `WendlerProtocols.mainWeeks`). Los otros métodos
  muestran su estructura (cifras + cuadrícula) y resumen; el gráfico de cada uno se deriva de su `recipe`.
- `ask` Volumen por músculo/grupos musculares en la ficha: no existe calculado por plantilla; no lo he dibujado.

## 4. Qué exige implementarlo
- Pantalla/ficha nuevas en `design/structure/` (`ProgramLibraryItem` crece: `key`, nombre editorial, técnico, meta, objetivo, autor, nivel, tagline, `blocks`, `weeks`, `days`, `kind`).
  `LibrarySection` deja de aplicar al tocar: abre la ficha y aplica con la elección.
- Funciones puras nuevas: filtro/búsqueda sobre las 38 filas, meta de fila, destacados por `rank`, agrupación de series × repeticiones por semanas, `weekProfile(recipe)` → % de TM por semana
  y por levantamiento (a partir de `SetPrescription.percent` de las series de trabajo; `derivedTerms` ya recorre la receta), tabla de ciclo para recetas que se repiten.
- Portadas de catálogo: `ProgramCoverImage`/`coverSvg` portado a Compose Canvas (el estilo `perfil` acepta el vector de carga por semana).
- Motor: (a) `applyTemplate` rellena sin snapshot: para `Deshacer` hay que guardar versión también en «rellenar» y en «sustituir de método vacío»; (b) el nombre de la copia usa `template.name`;
  (c) un método con programa vacío debería aplicarse sin crear copia (hoy: `overwrite=false` → copia siempre); (d) `Deshacer` = `restoreProgramSnapshot` con el id recién guardado.
- Complemento y especialización: el motor no sabe «sumar a tu plan»; hasta entonces el aviso de la ficha.

## 5. Dudas para el usuario
1. ¿Sustituir y Crear copia (lo que hace el motor) en lugar de «rellenar semanas vacías / sustituir semanas»? Es lo honesto; cambia el texto prometido hoy.
2. Coan–Philippi, Smolov y Smolov Jr: ¿se quedan en esta hoja con aviso, salen de ella o se pide un motor de «sumar a tu plan»?
3. ¿Nombres editoriales en toda la hoja (y en «Antes de …» y en el nombre de la copia) o seguir con los técnicos?
4. ¿Quieres volumen por músculo en la ficha de plantilla (hay que calcularlo de la receta) o basta el esquema semana × sesión?
5. ¿Deshacer también tras «Crear copia» (borrar la copia) o solo tras sustituir?

## Verificación hecha
`node build.cjs --out=shots/ct` y `check --prefix=ct`: 0 errores. Mirado a 360 × 880 (todas), 360 × 740 y 130 % (portada, ficha de ciclo, aplicar). Interacción comprobada por script:
segmentos, filtro de nivel, borrar filtros, abrir ficha, sustituir → confirmar → aplicando. Tope de ciclos de corrección respetado.
