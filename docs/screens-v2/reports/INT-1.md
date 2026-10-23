# INT-1 · Integración de screens-v2 · en curso

Se integra en `feat/screens-v2`, C:/kw/u11, en el orden del relevo. El artefacto ajeno `artifacts/aud2-editor-catalogo-20261020/` se conserva fuera de los commits. No se modifica master ni se hace push. Room sigue congelado en v30.

## Fusiones

| Paquete | Preflight | Fusión | Verificación posterior |
|---|---|---|---|
| u13 · PROG/FIX-TM/INV · 2c549b7ac | Limpio, árbol 60d1e6480057c97e3b312d3ed645d4c33d4516b1 | 030341408, sin conflictos | JVM: 161 clases, 1700/0; Android: 6 clases, 45/0 |
| u12 · ED-E · a76b4edc2 | Limpio sobre 18985e121, árbol 37860253aecd833693ded31d9bc28882f07ff638 | Fusión en este commit, sin conflictos | Pendiente |
| u14 · ED-A/ED-B | Pendiente | Pendiente | Pendiente |
| u17 · ED-D | Pendiente | Pendiente | Pendiente |
| u18 · ED-C | Pendiente | Pendiente | Pendiente |
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
