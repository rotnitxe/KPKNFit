# ED-B · Ejercicio y series en el editor

Implementación y comprobaciones automáticas terminadas el 22-oct-2026, continuando íntegramente el diff recibido sobre `107446d52` / `db5aa08cf`. La comprobación visual en emulador corresponde a la QA final de la ola; no se declara realizada aquí.

## Cambios

| Tablero | Resultado |
|---|---|
| B1/B5 | Lateralidad en ⋯, indicador discreto y orden L/R. Se conservan las dosis de cada lado. |
| B2 | Fichas reales de 85 × 76 dp y un foco con número, flechas, carga de lectura, descanso propio de la serie y acciones. Drop-set/rest-pause solo tras FALLO y «¿Vas más allá del fallo?». Salir de FALLO limpia esas técnicas y conserva el descanso. |
| B3 | Porcentaje, reps estimadas y peso enlazados. Referencias separadas de 1RM/TM, ausencia explícita y edición de 1RM o PR. Editar un porcentaje conserva el RIR/rango de una receta; cambiar explícitamente de modo aplica las reglas del modo confirmado. |
| B3b | AMRAP/Calibrador, mínimo nullable, alcance, ayuda y vista previa del mismo ejercicio y lado. La carga mostrada procede del lado seleccionado. |
| B3c–B3f | Solo RPE sin carga fija; minutos/segundos y prueba de cronómetro; distancia con pasos de 5 m; cantidad personalizada con pasos de una unidad. Una carga guardada no inventa un RPE. |
| B4 | Elegir un modo lo marca; confirmar lo aplica. |
| B7 | Una hoja para descanso de series y entre lados, con atajos y ±15 s. |
| B8/B9 | Opciones, lateralidad, unidad, carga inteligente, meta y confirmaciones en hojas, conservando sus validaciones y callbacks. |
| Bloque APRE | APRE 3/6/10 según Mann; confirmación antes de sustituir las series. S3 es AMRAP. El JSON guarda la variante y los cuatro IDs. Los ejercicios unilaterales reciben cuatro pares L/R completos. |

`restAfterSeconds = null` hereda el descanso del ejercicio. El bloque APRE conserva sus cargas al normalizar. No se infiere un TM. La aplicación en vivo de S3/S4 sigue siendo el paquete CAL4-V de la fase 4.

En RM, el porcentaje y la referencia continúan siendo comunes a ambos lados, según el contrato existente de `withExplicitRmLoads`. El foco muestra también la carga del lado seleccionado y avisa de ese alcance; no se inventa un porcentaje independiente que el modelo no guarda. Los RIR distintos de L/R se conservan al recalcular cargas.

## Verificación

| Corrida | Evidencia |
|---|---|
| `C:/kw/logs/edb-9.log` | 39 pruebas, 0 fallos; 4 min 7 s. Mismos clics físicos y aserciones con `GraphicsMode.NATIVE`. |
| `C:/kw/logs/edb-10.log` | 55 pruebas, 0 fallos; 3 min 48 s. Dosis, lista, AMRAP, cargas RM, estado de fila y 25 escenas de render. |
| `C:/kw/logs/edb-11.log` | 55 pruebas, 1 fallo de selección ambigua: «40 kg» estaba tanto en la ficha como en el foco. RM unilateral y APRE unilateral pasan. |
| `C:/kw/logs/edb-12.log` | **55 pruebas, 0 fallos**, 7 min 16 s. 17 de focos, 5 de opciones, 20 de lista y 13 escenas de render. La nueva aserción AMRAP se acota al foco mediante su ancestro, manteniendo los valores esperados y comprobando después la persistencia del mínimo y los lados. |
| `C:/kw/logs/edb-apre-jvm-1.log` | **15 pruebas JVM, 0 fallos/errores/omitidos**. Log completo: `C:/kw/logs/kpknjvm-u14-20261022-172539.log`. Tablas APRE 3/6/10, S3 AMRAP y prescripción. Commit de dominio/JSON: `b6c79f1ed`. |

Las corridas 4–8 documentaban la transición desde el diff recibido. La clasificación inicial de un clic de lateralidad como fallo real de la app queda corregida por el contraste de edb-9: cambiar únicamente las tres clases de pruebas al renderizador nativo resuelve los cinco fallos de clic sin cambiar sus callbacks ni sustituir los clics por acciones semánticas. Las expectativas de espacio no separable y L/R se adaptaron al diseño; el RPE de cardio conserva su texto original.

Renders en `C:/kw/shots/edb`: 26 PNG de `EdbRenderSheetTest` (13 escenas a 100/130 %, viewport final de 360 × 880) y 26 del arnés existente de ejercicio completo, de 360 dp de ancho y altura natural. Se revisaron focos, RM, AMRAP, Solo RPE, tiempo, distancia, modos, descansos, opciones y APRE. **Hay recortes variables en algunas capturas de Canvas de Robolectric**, aunque la aserción geométrica de cabecera visible pasa y las escenas de altura natural muestran el contenido completo. Esas capturas no acreditan la QA visual definitiva: comprobar expresamente las cabeceras de los focos en el emulador, también al 130 %.

## Límites e integración

- Room congelado: no se modificaron entidades, migraciones, base ni esquemas. `Exercise.apreBlock` es metadata opcional del JSON existente.
- G debe adoptar los focos RM/AMRAP nuevos al integrar su editor de series; no reintroducir sus controles antiguos.
- ED-C conserva la misma pieza `SessionEditorSeriesChip.kt` y la base única de calentamiento de LV-C. Mezclar sobre los cambios de `ExerciseEditorExpanded.kt` y `PreparationCarouselCards.kt` de u11.
- Sin emulador, APK, merge ni push en este paquete. La QA y la entrega siguen los gates del plan.
