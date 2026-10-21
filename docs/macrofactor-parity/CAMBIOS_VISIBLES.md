# Cambios visibles de la ola MFP

Cada paquete añade aquí lo que la persona ya veía y ahora cambia (cifras, rótulos, comportamientos). Es la base de las notas de versión. Nunca se reescriben registros guardados: solo cambia la presentación o el cálculo hacia delante.

| Paquete | Qué cambia | Antes → después | Por qué |
|---|---|---|---|
| H1 | Guardar una comida desde el atajo de creatina o desde `addLog` | Parecía guardada al instante aunque Room fallara (se perdía al reiniciar) → ahora espera a que Room confirme y, si falla, no la publica y avisa «No se pudo guardar la comida. Inténtalo de nuevo.» | Evita el falso éxito |
| H1 | Carga de nutrición con una fila ilegible | Una sola fila rota dejaba vacíos el diario, las plantillas y los planes → ahora cargan las filas sanas y la ilegible queda intacta en cuarentena (no se borra ni se sobrescribe) | No ocultar datos sanos |
| H1 | Creatina: tour de inicio | Se lanzaba antes de guardar → solo tras un guardado confirmado | Coherencia con el falso éxito |
| T2 | Pestaña Volumen del programa: «Real» | Dividía por «sesiones ÷ 3» (o por 1 sin calibración: total acumulado mostrado como semanal) → ahora es el **promedio semanal** del alcance elegido, con el mismo modo de roles que la barra | La cifra real y la planificada pasan a ser comparables |
| T2 | Fila de volumen: unidad | «6 series» (plan) y «Real 4 series/sem» → «6 series/sem» en Semana/promedio y «series» solo en totales; cifra y unidad apiladas | Misma unidad en plan y real |
| T2 | Registros sin fecha | Se colaban en los totales → no suman y aparece «N registros sin fecha no se cuentan» | Honestidad del dato |
| T2 | Desglose por ejercicio y por cabeza | Máximo por nombre en toda la ventana; la suma no cuadraba con la barra → máximo por ocurrencia, suma entre ocurrencias, ajuste de intensidad aplicado una sola vez; el desglose suma lo mostrado | 3 + 3 series del mismo ejercicio = 6 |
| T2 | Diagnóstico de sobreentrenamiento (Inicio y detalle del programa) | Ventana distinta (días entre el primer y el último registro ÷ 7) → últimos 28 días con divisor 4, igual en ambos sitios | Mismo criterio en todas las pantallas |
| T2 | Análisis del programa: cobertura | Aplanaba todas las semanas y lo rotulaba «semanales»; vacía en programas con configuraciones v2 → promedio semanal sobre las semanas prescritas; índice de catálogo unificado | Cifra coherente y no vacía |
