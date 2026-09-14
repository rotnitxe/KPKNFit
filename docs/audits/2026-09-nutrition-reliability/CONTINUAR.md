# Prompt maestro: continuar la validación de nutrición

Trabaja en `/home/rotnitxe/KPKNFit`. Continúa la reparación del registro natural de comidas de Android que ya está implementada en el árbol de trabajo. El usuario pausó la tarea por cuota y volverá al agente anterior para una revisión final.

## Objetivo y forma de trabajar

Completa los dos bloques pendientes descritos abajo y sus pruebas, la suite nutricional, la compilación y el QA en emulador. La política de producto es inferir porciones domésticas habituales y preguntar sólo ante ambigüedades materiales; no exigir gramos ni aceptar otra identidad porque sus calorías parezcan plausibles.

Trabaja como Constructor/Auditor en una sola tarea, sin subagentes, por ahorro de cuota. Esta instrucción del usuario sustituye la delegación del flujo local para esta continuación. Lee código directamente y de forma dirigida. No reinicies la auditoría completa ni reimplementes lo terminado. No amplíes el trabajo a otras funcionalidades. Corrige los fallos críticos que aparezcan en las verificaciones del alcance.

No omitas validación para ahorrar tokens: ahorra en lecturas y salidas, redirige Gradle a archivos y resume XML. Evita repeticiones de tests salvo cambios, fallos o dudas concretas. No pidas otra autorización para el trabajo ya aprobado. No publiques, hagas push, cambies versiones ni crees una release. Conserva el trabajo ajeno y deja los cambios revisables para el agente original.

## Leer primero

- `AGENTS.md`, `.opencode/kpkn-map.md`, `.opencode/memory/MEMORY.md` y `USER.md` de esa carpeta.
- `.opencode/plans/2026-09-12_nutrition-natural-language-reliability.md` (plan aprobado, flags nutrition y room).
- `docs/contracts/nutrition_interpretation_v2.md`.
- `docs/audits/2026-09-nutrition-reliability/README.md` y los JSON del mismo directorio.
- Skill `/home/rotnitxe/.codex/plugins/cache/openai-curated-remote/test-android-apps/0.1.2/skills/android-emulator-qa/SKILL.md` antes del QA.

El dominio sigue siendo Kotlin puro, sin Android. No hay migración Room ni recálculo histórico. Se añadieron propiedades JSON opcionales, adaptadores de base nutricional y transacciones. No regeneres activos grandes. No leas `.env`, claves de firma, keystores ni tokens. No invoques telegramBot.

## Estado exacto al pausar

Los agentes anteriores están interrumpidos. No había proceso Gradle ni helper de QA activo al comprobarlo.

**566 pruebas nutricionales en 53 suites pasaron, sin errores, fallos ni omitidas; assembleBaseDebug pasó. Pero son un CHECKPOINT: después se empezaron los dos bloques siguientes y esos últimos cambios aún NO se han compilado ni probado.** Puede faltar una API entre el drawer y TagResolver. No declares verde el árbol actual usando resultados anteriores.

El APK de ese checkpoint está instalado. El repositorio tiene numerosos cambios y archivos nuevos sin commit; no los reviertas. `android-native/app/build.gradle.kts`, el plan base-release-14-8 y `releases/KPKN Beta 14.8.apk`/`.verification.json` eran trabajo ajeno. La tarea de wizard ajena quedó en HEAD `c50e6cd5`; respétala. No reemplaces archivos de release con el APK de QA.

Ya se repararon y probaron: plantillas parciales; decimales/fracciones/rodajas; separación y exclusiones; especie/composición/marca; base por100g vs ración; estado real de cocción; V2 por mención sin reparse; preguntas visibles; rango/nota persistidos; tamaño desde base inmutable; guardado transaccional con borrador retenido ante error; aprendizaje confirmado tras éxito y Olvidar; nombres lexicalizados y scoops. El catálogo real OFF también está cubierto por regresiones de avena y leche.

## Pendiente 1: exclusiones contaminan contexto y cantidades

Reproducción en el APK del checkpoint, mismo drawer/contexto:

- `avena` -> Avena en Hojuelas, 40g,156kcal.
- `avena sin leche ni huevo` -> misma ficha,99g,385kcal. Leche/huevo aparecen Excluido y no se suman, pero alteran el contexto.
- `avena y leche y huevo` también daba99g a los componentes por el contexto genérico.

Causa trazada: TagResolution pasaba texto crudo y todas las menciones al detector. El huevo negado contaba como proteína y tres menciones como plato principal/almuerzo. Avena caía en rol lateral genérico:90g×1.1=99g, saltándose su prior específico40g.

Se estaba implementando en `TagResolution.kt`, `ContextDetector.kt`, `HouseholdPortions.kt` y pruebas:

1. Derivar composición, conteos y ajustes del AST de menciones consumidas, excluyendo las negadas.
2. Conservar el texto original sólo para la ocasión/hora explícitamente declarada.
3. Prior específico por alimento antes del lateral genérico: avena40g, leche200g; huevo unidad doméstica50g, según reglas existentes.
4. Excluir negados también de ajustes posteriores y totales de contexto.

Inspecciona el diff actual; pudo interrumpirse a mitad de este bloque. Comprueba avena sola vs exclusiones y su reordenación, pesos explícitos intactos y desayuno positivo de tres componentes con priors propios. No inventes densidades ni ajustes nutricionales para compensar una masa incorrecta.

Evidencias: `/tmp/kpkn-oat-quantity-probe.txt`, `/tmp/kpkn-nutrition-final-oats.xml`, `/tmp/kpkn-nutrition-final-oats-alone.xml`.

## Pendiente 2: respuestas de composición deben corregir la identidad

El checkpoint pregunta correctamente `¿De qué era la tortilla?`, con supuesto visible de trigo y rango amplio. Pero `Buscar tortilla de huevo` sólo abría una búsqueda avanzada. Sin ficha compatible, la pregunta y el centro seguían siendo trigo. Técnicamente era búsqueda sin confirmación, pero como respuesta a esa pregunta era engañoso.

Se estaba cambiando la UI a respuestas explícitas `De huevo`, `De maíz`, `De papas`:

- Reemplazar sólo esa mención y resolverla por el pipeline actual, conservando id y otros alimentos.
- Retirar inmediatamente la ficha anterior; no restaurarla si falla el resolutor.
- Preservar pesos/medidas declarados; reinferir la cantidad si venía de otro subtipo (40g de wrap no es un peso declarado de tortilla de huevo).
- Sin ficha: mantener la identidad corregida con incertidumbre/nota, nunca guardar trigo por haber aceptado posteriormente «No estoy seguro».
- Ignorar respuestas asíncronas obsoletas cuando se edita o elimina el tag; un error conserva el nombre nuevo y bloquea Guardar hasta resolver.

**Último mensaje del constructor de UI:** drawer/callbacks listos, esperando método compartido de TagResolver coordinado con el otro constructor. Revisa llamadas nuevas en FoodLoggerDrawer y API de TagResolution: pueden estar incompletas. Añade/verifica regresiones del recorrido completo y de masa explícita vs inferida.

Evidencias previas: `/tmp/kpkn-nutrition-final-tortilla.xml`, `...-tortilla-options.xml`.

## Validación que debes ejecutar

Usa Linux, no el wrapper PowerShell de AGENTS. CWD para Android: `/home/rotnitxe/KPKNFit/android-native`.

Prefijo de todos los comandos Gradle:

```sh
env JAVA_HOME=/home/rotnitxe/.local/share/kpkn-android/jdk \
 ANDROID_HOME=/home/rotnitxe/.local/share/kpkn-android/sdk \
 GRADLE_USER_HOME=/tmp/kpkn-gradle \
 timeout 600 ./gradlew --no-daemon --console=plain --warning-mode=summary --offline
```

Primero dirigido: `testBaseDebugUnitTest` con filtros para NutritionQaRegressionTest, NutritionLoggerReliabilityTest, NutritionDurableSaveTest, IndependentNutritionCorpusTest, ReservedNaturalMealCorpusTest, NaturalLanguageReliabilityTest y los tests de ContextDetector/HouseholdPortions afectados (localízalos).

Después suite completa:

```sh
[prefijo anterior] testBaseDebugUnitTest \
 --tests 'com.example.kpkn.domain.nutrition.*' \
 --tests 'com.example.kpkn.data.food.*' \
 --tests 'com.example.kpkn.data.repository.Nutrition*' \
 --tests 'com.example.kpkn.screens.nutrition.NutritionViewModelTest'
```

Finalmente `[prefijo anterior] assembleBaseDebug`. Gradle puede necesitar escalación para sockets locales; ya fue autorizado en la tarea anterior. No ejecutes dos Gradle simultáneos y congela código durante cada corrida. Resuelve fallos semánticamente; no relajes oráculos para aceptar otro alimento o cualquier cantidad positiva.

Resultados XML: `app/build/test-results/testBaseDebugUnitTest/`. Corpus generado: `app/build/reports/nutrition-reliability/independent-corpus.json`. Logs de checkpoints en `/tmp/kpkn-nutrition-*-gradle.log`. La suite anterior de566 no sustituye esta nueva ejecución.

## Emulador y protección de datos

ADB: `/home/rotnitxe/.local/share/kpkn-android/sdk/platform-tools/adb`
Serial: `emulator-5554`; paquete `com.example.kpkn`; actividad `.MainActivity`.
APK: `app/build/outputs/apk/base/debug/app-base-debug.apk`.

**Incidente ya comunicado al usuario:** el primer `adb install -r` eligió instalación incremental; el paquete quedó no disponible y el perfil anterior se perdió. No hubo pm clear/uninstall explícito. No se encontró respaldo útil; el usuario dijo «creo que no». No atribuyas esto a Room ni afirmes que preservamos ese perfil. No intentes otra recuperación ni cargues snapshots/reinicies el emulador.

La posterior instalación STREAMED conservó el perfil sintético y el registro. Usa exclusivamente:

```sh
adb -s emulator-5554 install --no-incremental -r [APK]
```

Antes de otro reemplazo, verifica un respaldo actual del perfil. Ya existe `/tmp/kpkn-nutrition-test-profile-before-final-install.tar`:3.450.368bytes,24archivos, SHA256 `43161b0b112f903e9ff5c7f87b8c296f3ebd64edbbaf743454496cc0da7504b2`. Es del perfil NUEVO de prueba, no recupera el original. Se creó con la app detenida y `adb exec-out run-as com.example.kpkn tar -cf - databases shared_prefs files`. Si hay cambios nuevos, respalda en otro archivo y verifica el tar antes de instalar. No imprimas datos sensibles.

No ejecutes pm clear, uninstall, wipes ni borres snapshots existentes. La instantánea temporal creada por nosotros ya fue eliminada; default_boot se conservó.

El perfil nuevo tiene **un único registro sintético guardado**: Arroz Blanco(cocido),200g,260kcal, incertidumbre260–720, grupoAlmuerzo del12/09/2026. Reapareció sin duplicarse tras force-stop/coldstart y tras instalar el APK de566tests. No se aprendieron hábitos de ese Unsure. Elimina sólo ese registro al terminar; si el usuario agregó otros, consérvalos.

## QA pendiente y controles

Ruta sin crear un plan nutricional ficticio: Home -> REGISTRO DE HOY -> Agregar comida. El botón de registro en la pestaña Nutrition puede pedir plan; usa Home. Omite permisos opcionales y cierra bienvenida con «Cerrar por ahora». No inventes datos físicos ni programa.

Hay helpers ya escritos, léelos antes de usarlos:

- `/tmp/kpkn_nutrition_qa.py`: dump, tap por etiqueta exacta, input, scroll, drag_close. Siempre obtiene árbol fresco.
- `/tmp/kpkn_nutrition_case.py 'descripcion ASCII' --name nombre`: sustituye la descripción, oculta teclado e interpreta. Úsalo SÓLO cuando el editor visible sea Descripción y la edición avanzada esté cerrada; no confundir el campo de búsqueda con Descripción.
- Dumps `/tmp/<nombre>.xml`. No derives coordenadas de screenshots. El enabled del texto Guardar puede diferir del enabled de su antecesor clickable; comprueba ese antecesor.

Estado UI al pausar: borrador sintético de tortilla, editor avanzado abierto tras probar cancelar arrastre. Obtén árbol fresco. Puedes descartar ese borrador y abrir uno nuevo; no hay comida nueva guardada por esa prueba.

Verifica en el APK que compile el árbol final:

1. Avena sola/exclusiones/reordenación y desayuno positivo: identidades, cantidad doméstica, exclusiones fuera de totales.
2. Respuesta de tortilla de huevo/papas: identidad corregida aunque no haya ficha, gramos explícitos conservados, Unsure no restaura trigo.
3. `arroz` -> Editar -> Grande/Pequeño/Grande: sin nueva pregunta de peso crudo/cocido por sólo cambiar tamaño; mismo resultado al volver al tamaño inicial.
4. Ensalada: supuesto visible de verduras basado en fuentes existentes, pregunta natural de composición, intervalos por densidad además de cantidad. `100 g preparacion desconocida`: nota sin referencia y rango no puntual. Unsure conserva esto al guardar/reabrir y no aprende.
5. Drag-dismiss -> Seguir editando: editor visible y borrador conservado. Esta recuperación ya pasó en el checkpoint; la búsqueda avanzada NO confirmada se reinició a la consulta del tag, por lo que no afirmes conservación de ese texto si no lo verificas.
6. Guardado/cierre/reinicio/reapertura de un registro sintético y rango visible en Home y Nutrition. El fallo de escritura y Olvidar ya están cubiertos en pruebas Room/JVM; no simules éxito de una inyección de error en UI que no hiciste. No dañes la base para inyectarlo.

Controles numéricos ya vistos en emulador, repetir selectivamente si los últimos cambios los afectan:

- media/una/dos tazas de leche:123.6/247.2/494.4g (UI124/247/494),75/151/302kcal.
- `0,5 kg arroz cocido`:500g650kcal.
- `100 g pan integral`:265kcal, no530.
- `dos rodajas de manzana`:50g26kcal.
- `una cucharada de aceite`:13.5g(UI14),119kcal; nunca atún.
- `100 g pollo frito y 100 g pollo cocido`:dos filas100g,223/166kcal.
- `un scoop whey`:gen105,30g120kcal; dos/medio proporcionales.
- `leche sin lactosa`:producto compatible, sin ofrecer queso/condensada/sabor incompatible. Ya pasó en el APK de566.
- `200 g arroz`:pregunta seca/cocida, Guardar bloqueado; Unsure retiene260–720 y habilita.
- `tres leches`:postre o incertidumbre explícita, jamás leche genérica; quesos contados vs nombres de pizza conservados.

Para limpiar el registro sintético: pestaña inferior `Nutrition`, localiza su fila/grupo y botón con content-desc `Eliminar`; elimina sólo ese log. No borres snapshots diarios ni otras comidas. Verifica total del día sin los datos sintéticos.

## Entrega para revisión posterior

Actualiza README, unit-test-summary.json, independent-corpus.json, device-qa.json y MEMORY con la evidencia FINAL, separando unit/JVM/device. El PNG `device/reopened-uncertainty.png` ya documenta el checkpoint; no lo atribuyas a un APK posterior sin comprobarlo.

El corpus independiente es23casos:23/23 identidad/cobertura/cantidad/política,0adiciones/omisiones,14rangos nutricionales evaluados y9sin oráculo, preguntas6/23. Cuatro controles de aritmética tienen MAE0.151kcal/máximo0.396 por redondeo; NO miden error real de porciones. Ocho casos narrativos se reservaron inicialmente; regresiones posteriores no son un benchmark ciego. No declares fiabilidad universal a partir del porcentaje de tests verdes.

Ejecuta git diff --check. Deja un resumen conciso de cambios finales, comandos/resultados, casos QA realmente ejecutados y cualquier limitación. No cierres con pendientes críticos conocidos ni ocultes el incidente del perfil. Si hay un bloqueo real, documenta exactamente qué faltó. No publiques: el usuario volverá al agente original para revisar.
