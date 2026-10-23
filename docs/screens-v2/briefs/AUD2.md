# AUD2 · Revisión de funcionamiento completa del editor de sesiones y del catálogo (cazar y arreglar bugs)

Worktree `C:\kw\u11`, rama `feat/screens-v2`. Orden del usuario (2026-10-20): «necesito una revisión de funcionamiento completa para detectar bugs que afecten la experiencia del editor/catálogo, bugs serios, bugs sencillos, etc. Hay que debuggear completamente ese apartado para que nunca haya algo bloqueante o raro».

Alcance: **todas las instancias** del editor y del catálogo según `docs/screens-v2/maps/MAPA_editor_catalogo.md`: editor de sesión (los 7 modos de `SessionEditorSheet`, grupos, superseries, cardio, preparación, arrastrar, deshacer, guardar, semana y variantes, plantillas, reglas, tiempo, versiones, transferir), catálogo (añadir, buscar, filtros, selección múltiple, superserie desde catálogo, reemplazar, crear y editar ejercicio propio, elegir técnica, ficha en cada contexto), y los editores que lo reutilizan (editar series y cardio en vivo, importador, «Cambiar» del asistente). Código en `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/`, `domain/exercises/`, `data/repository/ProgramRepository.kt` y vecinos.

**No rediseñes nada visual**: el rediseño ya está prototipado y se implementará después. Aquí se arregla el **funcionamiento**.

## Método

1. **Lectura dirigida** de cada flujo: estados posibles, rutas de error, concurrencia (corrutinas, `StateFlow`, guardado), recreación de la actividad, deshacer, límites (0 series, 1 serie, 30 series, 20 ejercicios, superserie de 4, sesión vacía, solo cardio, unilateral, todos los modos), datos raros (ejercicio sin ficha del catálogo, ejercicio propio borrado, programa importado, plantillas antiguas, sesiones con campos nulos), textos que se cortan, acciones que no hacen nada, botones que deberían estar desactivados, pérdidas de datos al salir sin guardar.
2. **Pruebas que reproducen** cada bug encontrado (unitarias o Robolectric, junto a las existentes), en rojo antes de arreglar.
3. **Arreglo** de los bugs de lógica o de funcionamiento, con su prueba en verde. Si un arreglo exige decisión de producto o de diseño, no lo hagas: documéntalo.
4. Corre las suites afectadas al final.

## Clasificación

- **S1 bloqueante**: se pierde trabajo, se corrompe el programa, se cuelga o se cierra la app, no se puede guardar o salir.
- **S2 grave**: una función no hace lo que dice, resultados incorrectos (cálculos, conteos, tiempos), estados imposibles.
- **S3 molesto**: comportamiento raro, foco o desplazamiento erróneo, texto o valor incoherente, accesibilidad rota.
- **S4 menor**: pulido.

## Reglas firmes

- Gradle SOLO con `& 'C:\Program Files\Git\bin\bash.exe' -c "bash /c/kw/tools/gradle_slot.sh /c/kw/u11/android-native <tareas>"`, en primer plano, timeout ≥ 3 600 000 ms; si falta memoria, primer argumento `"-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8"`. Prohibido `--stop`, matar procesos java, `GRADLE_USER_HOME` paralelo o `JAVA_TOOL_OPTIONS`. Si el candado de Gradle es de otro proceso, para y cuéntalo.
- Sin teléfono, emuladores ni adb (las pruebas de dispositivo las corre el orquestador). Robolectric sí.
- Nada fuera de `C:\kw\u11`. Sin push. Commits pequeños en `feat/screens-v2` (`fix(editor): …`, `test(editor): …`) terminados con `Co-Authored-By: Codex <noreply@openai.com>`. No commitees `docs/screens-v2/maps/`.
- Refinar, no quitar: ninguna función desaparece.
- Hay otra auditoría de solo lectura (AUD1) en el mismo worktree sobre Impacto/Columna, Sugerencias y Crear ejercicio: no arregles esos tres motores (se harán con su informe); sí anota cualquier bug que veas en ellos.

## Entregables

1. `docs/screens-v2/audits/AUD2_bugs_editor_catalogo.md`: tabla de todos los bugs (id, severidad, flujo, pasos para reproducir, causa con archivo:línea, estado: arreglado con commit / pendiente con motivo), y una lista de **pasos de prueba manual en el teléfono** para que el orquestador verifique los S1–S2 en el Z Flip.
2. Commits con los arreglos y sus pruebas.
3. Pasan `:app:compileBaseDebugKotlin`, `:app:compileBaseDebugAndroidTestKotlin` y las suites `com.example.kpkn.screens.sessioneditor.*`, `com.example.kpkn.domain.exercises.*` y `com.example.kpkn.data.*` relacionadas (si alguna falla desde antes, dilo con evidencia comparando con `8ac57c9d4`).
4. Informe final en español: resumen por severidad, lo arreglado, lo pendiente y lo que necesita decisión del usuario.
