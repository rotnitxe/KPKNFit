# Competición: contrato de la ampliación del 25-oct

Implementación primaria Android. Los motores puros aprobaron 10 pruebas compartidas con temporada; la primera ola D aprobó sus casos nuevos de VM y Compose. La reparación de finalización sin receta, el guard por modalidad y el enlace a la ficha están en el segundo gate Android D, todavía sin resultado al redactar. No hay instalación ni QA funcional acreditados.

## Antes del evento

Se usa el CompetitionRecord enlazado al programa y su fecha clave, con cuenta atrás por fecha de calendario. Los intentos pertenecen al evento, no a sesiones de entrenamiento. La planificación propone pesos editables de SBD para powerlifting; otras modalidades conservan su ficha y no reciben esos porcentajes.

| Estrategia | 1 | 2 | 3 |
|---|---:|---:|---:|
| Conservadora | 88 % | 94 % | 98 % |
| Objetivo | 91 % | 96 % | 100 % |
| Ambiciosa | 92 % | 97 % | 103 % |

Cada propuesta usa una referencia válida y positiva: 1RM guardado, si falta e1RM y, si falta, TM dividido por la fracción del método. Redondeo de propuesta: floor(kg/2,5 + 0,5) × 2,5. Con 200 kg y estrategia Objetivo: 182,5/192,5/200 kg. La base queda visible; los pesos se pueden editar antes de revisar y guardar. No se transfieren referencias de front squat, banca de mancuernas/máquina o trap-bar a SBD de competición.

Categoría y peso objetivo son opcionales. El peso del perfil es una declaración; la tendencia procede sólo de observaciones corporales originales con fechas válidas. La proyección lineal current + rate × days/7 compara el resultado con el objetivo, con tolerancia de 0,05 kg. Sin tendencia suficiente se indica que no puede proyectarse. La diferencia equivalente en kg/semana es matemática, no pauta de recorte ni garantía de dar el peso. No se altera nutrición.

Guardar comprueba instantánea del record, pesos finitos/positivos, intentos 1–3 únicos y orden no decreciente. Conserva resultados, bitácora, fotos y estado del evento. Las escrituras versionadas impiden que una persistencia asincrónica antigua sobrescriba una edición posterior.

## Día del evento

MEET_DAY es una fase de Inicio no serializada, distinta de REGISTER para fechas pasadas. El módulo de programa ofrece calentamientos sobre la apertura con la rampa avanzada existente: 40/60/80/90 %, redondeados a 2,5 kg, sin superar 92 % de la apertura. No se usa el 1RM como base de calentamiento.

El modo competición mantiene selecciones válidas/nulas sólo en la hoja. Tras un nulo en el primero se orienta a repetir la apertura; tras nulo en el segundo, repetir el segundo. Tras el tercero finaliza la orientación. Cerrar la hoja no crea registros ni resultados. La ficha de competición existente es la que registra el resultado real.

## Después del evento

Sólo un record COMPLETED y aún sin aplicar puede proponer marcas. Se toma el mejor peso válido declarado por SBD; no se usa un NO_LIFT ni se baja una marca existente. La referencia 1RM guardada prevalece sobre e1RM en la comparación. Las variantes no comparables no reciben referencias ajenas.

La revisión muestra marcas anteriores/nuevas. Confirmar combina TrainingMaxMerge y ExerciseTrainingMax con las mismas protecciones de Guardar TM: fuente intacta, sesiones registradas/iniciadas/manuales preservadas y reconstrucción sólo de semanas de receta elegibles. Perfil, decisión y meetResultAppliedAtMs se guardan juntos en una transacción. La prueba de fallo después de la primera fila verifica rollback sin marcador ni perfil parcial; una edición posterior invalida la revisión.

La planificación SBD no sustituye herramientas específicas de halterofilia, strongman, culturismo u otras modalidades. Sus records y asistentes actuales permanecen disponibles. Swift transporta los campos nuevos como opcionales y los conserva al copiar un record; hay test preparado, sin compilación/QA Swift en este host.
