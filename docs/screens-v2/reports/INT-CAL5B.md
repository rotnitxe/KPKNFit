# INT-CAL5B — revision y correcciones

Base revisada: `cc94b308a`, diff contra `acc75872c`. Correcciones autorizadas en `codex/sv2-cal5b` (u21). Implementacion y pruebas preparadas, sin Gradle/JVM/adb. No hay declaracion de compilacion, Room ni renders verdes.

## Hallazgos corregidos

1. **Test1RM ocultado por la bolsa anterior.** `recordPendingOneRmTestMarks` cambiaba el perfil y rematerializaba sin actualizar las capturas existentes. Ahora `ProgramProgressEngine.withTestedProfile` captura la evidencia confirmada con `ExerciseTrainingMax.captureTestProfile` ANTES del avance del metodo. Conserva los cuatro lifts, incluido militar, origen TEST, fecha `resolution.resolvedAtMs`, motivo e identidad del evento. Tambien deja evidencia si el valor numerico se repite; no propaga a variantes ajenas ni a lifts sin resultado. Los incrementos posteriores siguen por AUTHOR_PROGRESSION.
2. **Test1RM con publicacion optimista y fallback incoherente.** El ViewModel ahora resuelve test, referencias, reconstruccion y cursor de programa dentro de `mutateProgramNow`. Si reconstruir falla, aborta antes del guardado; si Room rechaza, no publica candidato, avance ni exito. El test sigue pendiente y puede reintentarse. Las notificaciones y el cursor activo derivado se actualizan solo despues del resultado durable exitoso.
3. **PanelTM cerraba al lanzar un callback Unit.** El panel usa el resultado suspend `updateExerciseTrainingMaxAndAwait`. Conserva formulario y valor durante guardado/fallo; muestra guardando/error/reintento y cierra solo tras exito. No admite otro guardar ni cancelar mientras esta guardando. Se mantienen componentes comunes de 48dp y los nombres completos.
4. **Segunda ocurrencia compatible sin base en vivo.** Confirmar la primera ocurrencia actualizaba la bolsa global de ambas, pero solo hidrataba la primera en la sesion; `missing` eximia la segunda por su bolsa aunque su referencia raw seguia null. Ahora `sessionWithConfirmedTrainingMax` hidrata todas las ocurrencias de identidad global exacta en la sesion, partes y variantes. Mantiene otras configuraciones/convenios, dosis hechas, overrides/drafts manuales y bases explicitas de sesiones manuales. Un lado hecho conserva su dosis mientras el otro pendiente puede recalcularse. APRE OBSERVED conserva su bloque. Se comprueba identidad de ejecucion antes de publicar la confirmacion asincrona.

## Pruebas preparadas

11 metodos nuevos y refuerzo de expectativas existentes; sin duplicar el recorrido CAL7 completo de G1 en u23:

- `com.example.kpkn.domain.training.ExerciseTrainingMaxTest`: +1 evidencia TEST incluso con valor repetido, fecha, idempotencia y variante ajena intacta.
- `com.example.kpkn.domain.training.Cal5OneRmTestTest`: refuerzo del caso militar con bolsa ya migrada, TEST/fecha y TM real.
- `com.example.kpkn.screens.programdetail.ProgramDetailViewModelTest`: +2 casos; fallo Room del test/retry y resultado await TM con fallo de reconstruccion + Room/retry. La expectativa antigua de guardar perfil nuevo con semanas viejas se reemplaza por conservar programa/bolsa/cursor completos y reintentar. Exito con bolsa ya migrada verifica nueva captura TEST; el test de inventario espera ahora la confirmacion durable.
- `com.example.kpkn.screens.programdetail.Cal5bTrainingMaxPanelTest`: +1 caso de suspension real del callback, estado guardando, conservar valor95 ante fallo y reenviarlo en retry; cerrar solo al resultado exitoso. Clicks del overlay NATIVE.
- `com.example.kpkn.screens.workout.Cal5bTrainingMaxUiTest`: +2 casos de texto completo y click real Reintentar TM al130 en740/880. Mantiene cuatro renders 360x740/880 fuente100/130.
- `com.example.kpkn.screens.workout.Cal5bLiveTrainingMaxRoomTest`: nueva, 2 casos con VM/Room reales; dos ocurrencias compatibles reciben TM100 y peso80 antes de desaparecer la solicitud; otra configuracion queda pendiente; recreacion mantiene ambas. Fallo Room conserva referencias anteriores y solicitudes, retry actualiza ambas.
- `com.example.kpkn.screens.workout.Cal5bTrainingMaxSessionReferencesTest`: nueva, 3 casos de dosis hechas/manuales, aislamiento de configuracion, lado izquierdo hecho/derecho pendiente y variante manual con base propia.

## Limites y validacion pendiente

La revision del resto de CAL5b no encontro otro defecto accionable confirmado en migracion exacta, invalidacion de referencias, separacion TM/1RM ni PROMOTE_TM. No se ejecutaron pruebas ni se abrieron PNG nuevos. La geometria requiere la corrida central y revision visual de los renders; las aserciones nuevas no prueban pixeles antes de ejecutarse.

El helper de hidratacion reutiliza la proteccion lateral compartida `manuallyFixedCalibrationSet`; la correccion side-aware de u29 debe convivir con esta base al integrar. El guardado global de referencia/rematerializacion es una mutacion durable de programa; la sesion en curso conserva su canal existente de persistencia y se rehidrata de la bolsa al reiniciar. No se afirma una nueva transaccion multi-entidad programa+ongoing.

No se cambio Room, DAO, entidades, esquemas, datasets ni canal APRE/NativeProgressionCardModel. Verificados estaticamente UTF8 estricto de rutas propias, delimitadores y `git diff --check`; eso no equivale a compilacion.
