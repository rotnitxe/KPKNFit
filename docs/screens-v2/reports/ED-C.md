# ED-C · Preparación y movilidad · cierre 23-oct-2026

Se continúa el WIP recibido de H1–H4, B6 y D9, conservando sus funciones. Los componentes se prueban dentro del ejercicio y del grupo reales. No se modifica Room, las migraciones ni los esquemas, y no hay push, instalación ni APK en este paquete.

## Implementación

- H1/B6: aproximaciones en fichas de 85 × 76 dp, radio 14, separación 6, el mismo contrato que las series efectivas. Un foco por ficha, carga de trabajo y capacidad de respaldo, entradas de %, kg y reps, descanso común con excepciones, añadir y quitar. La preparación vive dentro del ejercicio.
- El peso usa una única base de LV-C, `warmupTargetLoadKg`: primero el trabajo válido, y solo si falta, la capacidad. El 50 % de trabajo 100 kg con capacidad 130 kg son 50 kg. No se vuelve a aplicar el porcentaje al 1RM.
- Se corrige la conversión de entradas: el campo de porcentaje divide siempre por 100; kg/base se trata como fracción. Los valores finitos se limitan al rango del editor. Las regresiones cubren 1 % y kg por encima de la base, que antes acababan en 95 % y 10 %. Los datos legados hasta 100 % siguen siendo legibles; el editor conserva su máximo de 95 %.
- H2: miniaturas revisadas de 20 dp en fichas y 36 dp en el foco, consultadas por identidad persistida. Un nombre igual no permite reutilizar otra imagen. Sin correspondencia hay símbolo y nombre completo accesible. Tiempo/reps, series, notas, reordenar y quitar conservan su modelo.
- H3/H4: catálogo con buscar, filtrar, consultar ficha y añadir/quitar, y editor de dosis por tiempo o repeticiones. Las entradas se aplican al destino que abrió la hoja. Al 130 % se separan las acciones del catálogo para que «Añadido · Quitar» se lea completo; la última corrida verifica el texto completo.
- B6: las cabeceras tienen objetivo de 48 dp; con letra grande el control de tiempo/descanso pasa debajo y no roba el contador. TextLayoutResult comprueba los dos títulos completos al 130 %.
- D9: movilidad de sesión con destino, deduplicación por identidad, orden, dosis y tiempo global. Una prueba real guarda, cierra el ViewModel y la base, vuelve a abrirlos y verifica esos valores y las series de fuerza intactas.

## Decisión de producto

El tablero H2 `#grupo` dejaba los grupos generados en lectura; el WIP los hacía editables. Se consultó la contradicción y el usuario autorizó priorizar la implementación completa. Se conserva la edición de todos los grupos de movilidad de sesión como excepción explícita, registrada en DECISIONES_PROTOTIPOS_2026-10-20.md. No se atribuye un origen que el modelo no registra ni se añade un campo Room.

## Evidencia

| Corrida | Resultado |
|---|---|
| `C:/kw/logs/edc-warmup-jvm-codex-1.log` | BUILD SUCCESSFUL, 2 clases, 13 pruebas, 0 fallos/errores/omitidas |
| `C:/kw/logs/edc-codex-20261022-2.log` | BUILD SUCCESSFUL, 4 clases, 32 pruebas, 0 fallos/errores/omitidas |
| `C:/kw/logs/edc-codex-20261022-3.log` | BUILD SUCCESSFUL, 8 min 55 s, 4 clases, 33 pruebas, 0 fallos/errores/omitidas; incluye los contadores completos al 130 % |
| `C:/kw/logs/edc-codex-20261023-4.log` y `…-5.log` | Producción compila; 21 pruebas, una falla por la bandera de ancho del ensayo de aserción, también con renderizador nativo. El render muestra la acción completa. No cuentan como corridas verdes. |
| `C:/kw/logs/edc-codex-20261023-6.log` | BUILD SUCCESSFUL, 3 min 46 s; PreparationChipsComposeTest completo: 20 pruebas, 0 fallos/errores/omitidas, incluidos caracteres y límites de H3 al 130 % |

Quedan verificadas 34 pruebas Android únicas del paquete: las 20 Compose de la última corrida, las 12 del ViewModel y la persistencia real de movilidad de la tercera (esas rutas no cambiaron con H3), y el render de la quinta, con producción idéntica a la sexta. Son además 13 pruebas puras de la base de carga. No se presenta la unión de corridas como una única ejecución de 34 pruebas.

La comprobación final exige texto completo, todos los caracteres visibles, ninguna elipsis, ninguna pérdida de altura y límites de cada línea dentro de su caja (un píxel máximo para redondeo). Medida nativa: caja 470 × 74 px, línea de 0 a 469,29297 px, párrafo con ancho disponible 744 px. `didOverflowWidth` comparaba la caja ajustada al contenido con el ancho del párrafo, aunque la línea cabía; por eso se verifica el contenido y la línea reales. El mismo caso conserva assertIsDisplayed. No se sustituye ningún clic físico por una llamada al callback.

La primera corrida Android compiló producción, pero el WIP de prueba importaba assertIsDescendantOf, inexistente en esta versión. Se sustituyó por assert(hasAnyAncestor(hasTestTag(...))) en los mismos controles, preservando su exigencia de pertenencia al ejercicio y sus clics físicos.

Catorce PNG en `C:/kw/shots/edc/`, letra 100/130 %, de H1, H2, H3, H4, B6, catálogo D9 y grupo D9. Se revisaron H1/H2, B6 tras corregir sus títulos, ambas dosis H4 y el grupo real D9 al 130 %. La revisión H3 reveló el texto cortado de su acción; la captura final de la quinta corrida lo muestra completo y la sexta comprueba su disposición. No se cambió producción entre ambas. Algunos textos del render nativo pueden recortarse de forma intermitente aun con layout correcto; el QA real sigue pendiente.

## Revisión adicional y commits

Auditoría externa de solo lectura expresamente autorizada: CommandCode 1.87.1, xiaomi/mimo-v2.6-pro, informe final `C:/kw/logs/mimo-edc-audit-20261022-2.ndjson`. No halló bloqueantes en firmas, conversiones, unidades, JSON, deduplicación, destino ni orden. No ejecutó Gradle ni pruebas y su informe no acredita compilación; precede a los últimos ajustes de imágenes y letra grande. El intento inicial sin informe no cuenta como validación.

Commits: f37329fe0 (base de carga y pruebas puras), b7381c436 (fichas/focos y cabeceras), 142aa2e58 (edición y persistencia de movilidad de sesión con la decisión del usuario). ae5253498 guarda H3 y las veinte pruebas Compose verificadas. El cierre documental guarda los renders y este informe.

Al integrar: conservar la sección extraída de preparación y la base única de LV-C. El archivo compartido SessionEditorSeriesChip añadido también por ED-B se resuelve con el slot opcional de ED-C y los valores predeterminados S1 de ED-B, sin alterar geometría. La integración, el QA de teclado y navegación en ZFlip5_QA y la APK son pasos posteriores.

## Seguimiento QA H3 · 23-oct

La búsqueda de gato no tiene coincidencias en el catálogo publicado actual: no demostraba un defecto de altura ni una pérdida de resultados. La captura H3-gato-expanded-1792759359841007300.png en C:/kw/shots/screens-v2-final-emu-20261023-c3 muestra un estado vacío sin explicación. Se agrega Sin resultados y la indicación de cambiar palabra o zona, conservando el buscador y los filtros. No se modifica el reparto de altura sin evidencia de un defecto geométrico.

PreparationChipsComposeTest añade recuperación real en la hoja con portal, viewport360×740 y fuente130: búsqueda imposible, estado vacío visible, reemplazo por Retracción cervical del catálogo, acción Añadir y callback con su ID. Preparado sin Gradle/JVM/ADB en u18; la corrida399 activa usa la fuente anterior y este caso requiere validación posterior. No cambian Room, catálogo ni persistencia.
