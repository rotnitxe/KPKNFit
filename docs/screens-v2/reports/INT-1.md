# INT-1 · Integración de screens-v2 · en curso

Se integra en `feat/screens-v2`, C:/kw/u11, en el orden del relevo. El artefacto ajeno `artifacts/aud2-editor-catalogo-20261020/` se conserva fuera de los commits. No se modifica master ni se hace push. Room sigue congelado en v30.

## Fusiones

| Paquete | Preflight | Fusión | Verificación posterior |
|---|---|---|---|
| u13 · PROG/FIX-TM/INV · 2c549b7ac | Limpio, árbol 60d1e6480057c97e3b312d3ed645d4c33d4516b1 | 030341408, sin conflictos | JVM: 161 clases, 1700/0; Android: 6 clases, 45/0 |
| u12 · ED-E · a76b4edc2 | Limpio sobre 18985e121, árbol 37860253aecd833693ded31d9bc28882f07ff638 | 6bf182d81, sin conflictos | 33/0 base y 11/0 tras corrección D8; 34 pruebas únicas |
| u14 · ED-A/ED-B · 385cab731 | Limpio tras D8, árbol 6e533815cb855eccd804bba94e78a7f17f745e07 | 67ded417b, sin conflictos | 10 clases, 87/0 |
| u17 · ED-D + C3 · 2cf81b677 | Limpio sobre 6487f3059, árbol fadf0ced2030287f8598084998c4c2145287ac30 | e7fd65eac, sin conflictos | 10 C3 finales/0; 67 anteriores ED-D/0 y 50 ED-B/0 en corridas separadas, descritas abajo |
| u18 · ED-C · 296bb3ff6 | Cuatro conflictos previstos, resueltos conservando LV-C/ED-B | 9d6d11730 | 78 casos únicos/0; 72/0 iniciales y 6/0 tras migrar H1/H2 |
| u15 · CAT-A | Pendiente | Pendiente | Pendiente |
| u16 · G | Pendiente | Pendiente | Pendiente |

## Contratos que deben conservarse

- Una base de aproximaciones (`warmupTargetLoadKg`) compartida por LV-C y ED-C. Conservar la sección de preparación extraída en ED-C y el resto de consumidores del carrusel de LV-C.
- La ficha común añadida por ED-B y ED-C conserva la geometría de 85 × 76 y los valores predeterminados S1 de ED-B; se añade el slot opcional de ED-C para las imágenes de movilidad.
- Adaptar los consumidores de series de C3 y G1 a los focos RM/AMRAP, con TM declarado, lado y recálculo de cargas explícitas. G1 conserva sus fichas compactas de 56 dp, expresamente aprobadas en su propio tablero.
- Confirmación común de modo en G1 y preservación de RIR/rango al editar un porcentaje simple. Comprobar el aviso de límites de ED-E para evitar duplicados.
- Los grupos de movilidad de sesión son editables por decisión explícita del usuario de 22-oct-2026; no se inventa un origen que no guarda el modelo.

## Fallos y regresión

Pendiente de las corridas de integración y de la regresión final. No se atribuye a integración un fallo sin examinar su contrato, la base y su evidencia. Las imágenes faltantes del checkout disperso y los hashes afectados por CRLF son límites de entorno ya documentados; las pruebas funcionales deben quedar verdes.

Este informe no acredita todavía QA en emulador, instalación, APK, firma ni publicación.

## Corridas de la primera integración

- `int-u13-jvm-codex-20261022-1.log`: compiló main, pero el filtro completo de domain/calculations incluyó Cal1CapacityContractTest, que importa screens.workout.LiveSetPrPolicy, fuera del arnés. Es un error de selección de carril, no un defecto del producto. Esta clase pasa a la validación Android junto con TrainingPlanRecipeJsonCompatTest (depende de dbJson/Room).
- `int-u13-jvm-codex-20261023-2.log`, log completo `kpknjvm-u11-20261023-000348.log`: BUILD SUCCESSFUL en 4 min 5 s; 399 s incluido cierre/resumen del wrapper. 161 clases, 1700 pruebas, 0 fallos, 0 errores, 0 omitidas. Protocolos y training completos, FixTmLoadBasisContractTest y los dos fixtures por archivo. No se modificó la prueba que requiere pantalla ni se amplió el arnés.

- `int-u13-android-codex-20261023-1.log`: BUILD SUCCESSFUL en 9 min 33 s, compilación de producción y pruebas; 6 clases, 45 pruebas, 0 fallos/errores/omitidas. TrainingPlanRecipeJsonCompatTest (19), Cal1CapacityContractTest (4), AuthoredTmLoadConsumerContractTest (13), SessionEditorRmLoadEditTest (4), SessionEditorRulesRmLoadViewModelTest (2), FixTmWarmupAnchorTest (3). Primera integración cerrada en verde.

## ED-E: unión y aviso de límites

Preflight y merge de u12 sin conflictos. La revisión de `RulesSheet` confirma una llamada por edición; `KpknNumberCell.commit()` apaga `editing` antes del callback, de modo que Hecho y perder foco no lo repiten. El ViewModel normaliza y guarda el borrador sin emitir otro aviso; la pantalla conserva un único snackbar de límites por callback. No se añade una segunda notificación. Esta conclusión es por lectura; la verificación de interacción sigue en las clases concretas de ED-E y el QA final.

ED-C se cerró en u18 (296bb3ff6) mientras se valida ED-E. El preflight de u14 sobre 6bf182d81 es limpio (árbol b3df59c9d6d0d1eee2dda3272e2226ab01ebaa56), pero la rama aún no se aplica hasta cerrar ED-E.

## ED-E: verificación y defecto visual detectado

`int-u12-android-codex-20261023-1.log`: BUILD SUCCESSFUL en 9 min 23 s; 6 clases, 33 pruebas, 0 fallos/errores/omitidas. Se revisaron Reglas y Transferir al 130 % entre los 34 PNG generados en C:/kw/shots/int-ede. Reglas conserva sus controles; Transferir cortaba «Traer de otra sesión». Las pruebas verdes no cerraron ese defecto visual.

Se corrige D8 en integración: modos en columna con letra grande, atajos separados del título y cuerpos con peso que reservan el pie, dentro de máximo 720 dp. Nueva regresión física a 360 × 740 y 130 %: etiquetas y todos sus caracteres, objetivos de 48 dp, cuerpo útil ≥96 dp y acción fija al copiar/reemplazar/traer. `int-u12-android-codex-20261023-2.log`: BUILD SUCCESSFUL en 9 min 31 s; tres clases, 11 pruebas, 0 fallos/errores/omitidas (Ede 6, D5D8 4 y render 1). Se revisó el nuevo PNG Transferir al 130 %: etiquetas y pie completos. Las otras 23 pruebas base no tienen rutas modificadas; total 34 únicas, sin atribuirlas a una sola corrida. Se cierra ED-E y el siguiente paso es u14 tras salir el wrapper.


## Reparto de adaptaciones de integración

El usuario autorizó subagentes 6.1 Sol Medium. C3 se prepara en u24-int-c3 (ramas u14 y u17); G1 en u25-int-g1 (u14 y u16). No ejecutan Gradle ni JVM; todas las pruebas y el orden de fusión siguen centralizados en el principal. Sus commits de preparación requieren validación en u11 antes de acreditar el verde.


## ED-B: verificación en integración

`int-u14-android-codex-20261023-1.log`: BUILD SUCCESSFUL en 9 min 54 s, 10 clases, 87 pruebas, 0 fallos/errores/omitidas. Focos (17), opciones (5), lista de series (20), estado de fila (3), lista de sesión (10), AMRAP (5), cargas RM (4), reglas RM (2), dosis (8) y render (13). Se generaron 26 PNG en C:/kw/shots/int-edb. Se revisaron RM y Calibrador al 130 %: RM completo; Calibrador conserva el recorte variable de Canvas documentado en ED-B pese a geometría e interacción verdes. No se atribuye a este PNG una QA visual completa; verificar cabeceras en el emulador final.

C3 preparado en 2cf81b677, preflight sobre 67ded417b limpio (árbol 6b35564d96c0239059646dd7ffc1716bb891e7cf). Se repetirá tras guardar esta evidencia. La revisión de pendientes de ED-A se prepara aparte en u26-int-eda; A5 usa insignias neutras exactas del tablero, no colores inferidos del informe antiguo, y A1 requiere comprobar el espacio de acciones al 130 %.


## ED-D/C3: primera corrida y correcciones pendientes

`int-u17-android-codex-20261023-1.log`: BUILD FAILED en 8 min 59 s; producción y pruebas compilan, 76 pruebas ejecutadas, 4 fallos en SupersetRoundsFocusIntegrationTest. Los 67 casos anteriores de ED-D pasan y 5 de los 9 nuevos pasan. Tres fallos seleccionan ambiguamente Eliminar de ronda y Eliminar del foco; el cuarto compara bounds recortados fuera del viewport. El commit aislado 5439b7760 acota acciones al foco y usa coordenadas sin recorte; aún no aplicado en u11 ni verificado.

Se revisaron los renders nuevos RM/TM y AMRAP lateral al 130 % en C:/kw/shots/int-edd. Además del arnés, se detectó un defecto de interfaz real: el control anidado recorta Próximas sesiones al 130 %, y el foco AMRAP lateral no identifica el lado que sustituyó InlineSetRow. Se adapta el alcance local a la anchura/fuente y se conserva la identidad L/R; no cerrar esta unión hasta la nueva corrida y revisión de PNG. El fixture de render TM se hará coherente con su % y referencia, sin rebajar las pruebas de edición.


## ED-D/C3: cierre verificado

La tercera corrida `int-u17-android-codex-20261023-3.log` termina BUILD SUCCESSFUL en 3 min 33 s: 10 C3/0, incluidos ambos alcances por click y datos L/R. Las correcciones quedan en 030e6b522, c3f44fcef y a0332f998. La segunda pasó también 50 controles ED-B; producción no cambia entre segunda y tercera. Los 67 casos previos de ED-D, de rutas no afectadas por el alcance estrecho AMRAP, estaban verdes en la primera. Son evidencias de varias corridas, no una ejecución única de127.

Render AMRAP lateral al130 revisado: LadoR y las dos etiquetas completas; RM/TM usa fixture coherente100×80%=80kg. Detalle de medidas y diagnóstico de la aserción en INT-C3.md. Wrapper cerrado; siguiente preflight ED-C y resolución conservadora de cuatro conflictos conocidos.


## ED-C: integración y primera verificación

Merge 9d6d11730 sobre ba304b1e5; cuatro conflictos: WarmupRules.kt, WarmupRulesTest.kt, ExerciseEditorExpanded.kt y SessionEditorSeriesChip.kt. Se conservan los avisos y constantes de LV-C, los once casos combinados de WarmupRules, la base única warmupTargetLoadKg y los valores/medidas ED-B con el slot opcional de movilidad ED-C. Sin cambios Room. La validación del máximo usa ahora warmupMaxRecommended, sin duplicar su fórmula.

`int-u18-android-codex-20261023-1.log`: producción y pruebas compilan; 7 clases, 78 pruebas, 2 fallos en ExerciseEditorExpandedComposeTest. Las otras seis clases pasan (72/0), incluidas 47 pruebas de tarjetas de preparación en vivo, 20 del editor y la persistencia real. Las dos aserciones esperan la interfaz anterior: tres filas Descanso entre series y el texto Tiempo global de movilidad. Se migran a las fichas, foco y cabeceras H1/H2; se comprueba también el selector real, los callbacks, la conservación de dosis y el valor global al guardar. Las otras cuatro pruebas mantienen sus cuerpos originales. No se rebaja el contrato a una comprobación de existencia. Wrapper cerrado con exit1; nueva corrida de la clase afectada pendiente.


ED-C cerrado: `int-u18-android-codex-20261023-2.log`, BUILD SUCCESSFUL en 8 min 30 s, 6/0 en ExerciseEditorExpandedComposeTest; wrapper exit0. Con los otros 72 casos de la primera corrida, cuyas rutas no se alteraron salvo reutilizar la fórmula idéntica del máximo, hay 78 pruebas únicas verdes. La regla se repetirá también en la regresión JVM de fase 3. Revisados PNG al130 H1/H2/H3, B6, grupo D9 y LV-C a740: controles completos; las fichas se desplazan horizontalmente. Sin QA de dispositivo ni APK todavía.
