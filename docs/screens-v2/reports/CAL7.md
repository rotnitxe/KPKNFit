# CAL7 · Recorridos con Room real

## Estado y límites

Preparación en `C:/kw/u23`, rama `codex/sv2-cal7`, sobre una base provisional pendiente de refrescar al SHA verde de Fase 3. El alcance de este cambio es pruebas e informe: no modifica código de producción, Room, entidades, DAO ni esquemas. **No se ejecutó Gradle, JVM ni adb; todos los resultados quedan pendientes de la corrida central sobre el SHA verde.**

La suite preparada reúne 21 recorridos end-to-end con Room en memoria: cuatro ya existentes y 17 añadidos en esta preparación. Se usan Robolectric, repositorio/ViewModels y cierres reales; ningún log de entrenamiento se fabrica en memoria de prueba ni se sustituye por un helper aislado.

## Sesiones libres y 1RM

`Cal7FreeSessionRoomTest` contiene tres recorridos. El primero registra y aplica un calibrador, recrea el ViewModel desde Room, revierte la decisión, registra una captura NEXT_SESSIONS, termina una sesión real y abre la siguiente ejecución. Comprueba que 60→62,5→60 hoy, referencia de 65 kg en la próxima, resolución durable y series ya realizadas intactas, sin cambiar el ejercicio contiguo de 40 kg. El segundo cancela por `WorkoutViewModel.cancelWorkout` y comprueba el borrado en Room, ausencia de logs/referencias aceptadas y de residuos en la siguiente ejecución. El tercero compara RINGS normal y calibrador conservado con los mismos registros, instante y metadatos musculares; exige drenaje positivo para que la igualdad no sea vacía. Los valores numéricos observados se anotarán después de ejecutar.

`Cal7OneRmRoomJourneyTest` materializa una receta de banca y press militar, migra el pool como precondición, abre la hoja real sobre el OverlayHost y pulsa Guardar y continuar. Comprueba que el gate no pide sentadilla ni peso muerto, el control de 48 dp, la falta de persistencia antes del click, los resultados de 127,5 y 82,5 kg, auditoría y cierre del gate, perfil y referencias 1RM/TM actualizados, cargas 70 % del TM de la semana siguiente y referencias principales ajenas preservadas. La ejecución contra la base verde debe confirmar la corrección de CAL5b necesaria para mantener el pool sincronizado y rechazar un guardado cuyo rebuild falle.

## Programas publicados: 12 recorridos

`Cal7ManagedRoomFixture` instala recetas publicadas, prepara un programa COMPLEX en Room, abre sesiones con `WorkoutViewModel`, registra todas las series por el grabador y termina con el controlador real. Verifica la identidad de todas las series en los logs durables y busca cada cierre por programa y fecha explícita, evitando confundir una sesión reutilizada en el ciclo siguiente; también verifica el ID base. El check-in sano queda asociado al mismo día. Accesorios sin kg autor usan una entrada explícita del atleta de 40 kg, que no se convierte en referencia de capacidad. El fixture también protege una sesión manual y una referencia de otra variante.

`Cal7ManagedProgramRoomJourneyTest` ejecuta 12 combinaciones (OFF/PROPOSE/AUTO) de `wendler-531-bbb`, `nsuns-531-lp-4d`, `juggernaut-2` y `gzclp`. Cierra el ciclo 5/3/1 y GZCLP tras cuatro semanas, la ola Juggernaut tras cuatro semanas y nSuns tras la semana publicada. En PROPOSE acepta los cambios de método por `ProgramDetailViewModel`; mantiene decisiones de recuperación separadas. Comprueba auditoría y TMs, que no se inventen 1RM, que el pool canónico acompañe al perfil, que las referencias/sesiones manuales y logs históricos queden protegidos, y abre y registra la carga prescrita en una sesión futura real. nSuns PROPOSE/AUTO deben demostrar el +5 kg publicado; OFF conserva el TM.

## APRE manual, ciclo simple y variante B: cinco recorridos

`Cal7ManualApreRoomJourneyTest` crea programas manuales sin receta ni protocolo publicado. En OFF/PROPOSE/AUTO ejecuta dos semanas por la ruta real: S3 con 120 kg × 9 se aplica desde el ViewModel y ajusta S4 a 122,5 kg; S4 ejecutado por la atleta produce captura XRM de 125 kg. Verifica el log durable, la propuesta tipada antes de aceptar, la resolución por `ProgramDetailViewModel`, auditoría/captura aplicada en AUTO, cargas reales de la siguiente semana y que una sesión manual y una referencia ajena no cambien.

El cuarto recorrido cierra una semana APRE en un ciclo simple de una semana. Fija identidad/cursor del ciclo 1, conserva esa identidad en el log cerrado y comprueba la propuesta APRE de 125 kg antes de que la misma semana se reutilice en el ciclo 2. Esta regresión cubre la omisión observada en la revisión de `completeCycle`.

El quinto activa B desde `WorkoutViewModel.setActiveMode` sobre una sesión base A cuyo `sessionB.id` es distinto. Graba S3/S4 APRE y el accesorio manual B por el ViewModel; verifica que el log conserva el ID base A pero contiene los ejercicios y contexto de B. La captura tipada afecta solo al APRE B de la semana futura después de aceptar; la banca A ajena, el accesorio B manual, la sesión manual separada y la referencia externa permanecen intactos.

## Hallazgos y dependencias de integración

- Luna informó que el canal de producción u28 ya corrige los hallazgos APRE encontrados en revisión: evaluación S4 al cerrar un ciclo simple, recolección APRE en variantes B/C/D y cierre de B con la identidad base A del log. Queda pendiente revisar esos cambios y validarlos contra el SHA verde; esta preparación solo aporta recorridos de regresión.
- El recorrido 1RM conserva aserciones para pool sincronizado y rechazo seguro si falla la reconstrucción. La preparación no sustituye la corrección CAL5b ni acredita que esté presente en el SHA verde.
- La API APRE manual, las propuestas tipadas y la corrección de identidad de sesión B aún deben estar en la base verde antes de compilar esta suite.

## Evidencia

Revisión estática de firmas/fixtures e inspección de diffs. Los cierres de las cuatro rutas CAL7 esperan a que terminen los ViewModels, vacían escrituras pendientes y esperan el scope privado de ProgramRepository antes de cerrar Room. `git diff --check` pasó y el escaneo UTF-8 estricto de los archivos editados no encontró BOM, U+FFFD, CR aislado ni whitespace final. No hay compilación, ejecución de pruebas, render ni validación en dispositivo. No se modificaron producción ni Room y no se tocaron otros worktrees.
