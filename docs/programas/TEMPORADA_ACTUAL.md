# Temporada: contexto independiente de entrenamiento y nutrición

T120, T122 y el motor T123 tienen pruebas dirigidas aprobadas. El repositorio, observador, módulos y guarda transaccional están en la ola Android D; el primer intento aprobó los casos nuevos de VM, y el segundo sigue pendiente. No hay validación funcional instalada aún.

SeasonAdvisor recibe hechos y declaraciones con ausencia explícita de datos: objetivo del bloque actual/siguiente, descarga programada, dirección y kcal del plan nutricional activo, referencia eerKcal guardada en su snapshot, tendencia corporal observada, fecha de competición, categoría objetivo y peso declarado en el perfil. No infiere recuperación, técnica, movilidad, lesión o gasto de fuerza.

## Reglas visibles

- Pico, taper o realización con dirección DEFICIT: ofrece revisar la dirección, sin cambiarla.
- Competición en 0–14 días con déficit y sin necesidad declarada de bajar a categoría: propone revisar el contexto nutricional.
- Descarga: recuerda que forma parte del programa y que la contabilidad/check-in de nutrición sigue sus propios registros.
- Déficit aritmético de al menos 15 % entre objetivo y mantenimiento positivo del plan: las propuestas semanales pueden usar el límite intermedio de planificación y RIR mínimo 2. Sigue habiendo revisión y confirmación; no representa una medición fisiológica.
- Acumulación y superávit declarados: invita a revisar objetivos de planificación; no autoriza por sí solo más series.
- Peso objetivo y proyección fuera de él: muestra la comparación matemática, con ausencia de tendencia explícita.
- Sin dirección nutricional disponible: informa de que falta ese contexto, manteniendo el entrenamiento independiente.

La tarjeta lee el estado activo persistido, sin reinterpretar isActive de otra fila. Un observador de invalidación de Room refresca lecturas de planes, estado activo, observaciones corporales y records. Ninguna tabla ni esquema cambia. La comparación de déficit no fabrica EER cuando falta: usa sólo result.eerKcal del snapshot y objetivo válido.

## Propuestas semanales

Con contexto de déficit, el máximo de planificación es maxAdaptiveVolume (referencia intermedia), acotado por los límites elegidos; sin él, se mantiene el comportamiento previo. Los cambios de RIR excluyen aproximaciones y AMRAP y respetan allowRirChange y sesiones protegidas. Si el suelo 2 cambia un objetivo aunque la respuesta mantuviera RIR, se presenta como cambio revisable; no se omite del preview ni se atribuye un delta falso.

El repositorio captura el contexto de temporada y settings al preparar. Al confirmar vuelve a compararlos dentro de la transacción que escribe el programa; una edición nutricional en la espera del candado exige revisar de nuevo. La prueba provoca esa edición antes del chequeo y conserva tanto la respuesta original pendiente como la nueva nutrición.

## Alta nutricional

Sólo si falta dirección se ofrece MAINTENANCE para POWERLIFTING/POWERBUILDING. HYPERTROPHY y ausencia de programa no reciben una sugerencia de esta política. Los explícitos DEFICIT/SURPLUS/PROFESSIONAL se conservan. TRACKING_ONLY no genera plan ni metas. Se reutiliza la preparación canónica: un manual completo puede conservar su fallback existente sin que este módulo invente calorías, macros o peso objetivo.

Tras la retirada, las sesiones de fuerza se consideran gasto no estimable por el adaptador nutricional, distinto de descanso. Los tests conservan el presupuesto semanal exacto y verifican reparto provisional uniforme cuando falta gasto válido; no recuperan el estimador retirado.

No se toca el estimador MFP, gasto, calibraciones, diarios o check-ins nutricionales desde programas. El antiguo AUGE D4 no existe. Una pantalla de temporada dedicada queda para el rediseño; la tarjeta de contexto de esta ola no pretende sustituirla.
