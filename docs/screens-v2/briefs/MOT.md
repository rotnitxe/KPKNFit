# MOT · Motores del editor y del catálogo (AUD1), pista paralela

**Worktree:** `C:\kw\u12`, rama `sv2/motores`, nacida de `feat/screens-v2` @ `373729ae0`. Trabaja **solo** ahí. En paralelo, otro agente trabaja en `C:\kw\u11` (sesión en vivo) y otro en `C:\kw\u13` (programas): no toques esas carpetas.

## Qué leer

- `C:\kw\u12\CLAUDE.md`
- `C:\kw\u12\docs\screens-v2\PLAN_IMPLEMENTACION.md`, por sus leyes
- `C:\kw\u12\docs\screens-v2\audits\AUD1_motores_editor.md`, completo: es tu especificación
- `C:\kw\u12\docs\screens-v2\audits\AUD2_bugs_editor_catalogo.md`, la sección de pendientes (tres de ellos son de AUD1)
- `C:\kw\u12\docs\screens-v2\maps\MAPA_editor_catalogo.md`

## Qué implementar, en este orden (paquetes de AUD1 §1.5, §2.8, §3.6 y §4)

1. **Impacto y Columna (I1, I2, I4)**
   - El editor usa el cálculo completo del motor general, sin duplicarlo.
   - Conserva los decimales.
   - Distingue cero, valores menores de uno y «desconocido».
   - Suma el cardio a los totales.
   - Fixture de AUD1 §1.2: con sentadilla y peso muerto, el editor da el mismo valor de Columna que el motor general (8, no 2).
2. **Sugerencias (S1–S5)**, según el rediseño de AUD1 §2.7:
   - Contexto: objetivo, rol de cada ejercicio, fase, historial y tiempo disponible.
   - Los movimientos principales quedan protegidos.
   - Cada acción se verifica antes de proponerla: «acortar descansos» no puede alargar la sesión, y el drop-set no se presenta como ahorro.
   - Las sesiones planificadas no se tratan como hechas.
   - Tono no alarmista; nada de afirmaciones sobre lesiones.
   - Como mucho **3 avisos**, sin duplicados.
   - Usa las recetas de AUD1 §2.5 como pruebas.
3. **Crear ejercicio: el motor (E1, E2, E3/E4/E5 en su parte de datos, E7)**
   - Unifica la inferencia de parecidos con la búsqueda del catálogo (sin sesgo hacia mancuernas, con alias coherentes).
   - Abstención explicable si el nombre es ambiguo.
   - Valida la configuración y conserva los ejercicios propios y los datos manuales, persistidos por UUID.
   - La descripción automática sale **solo de una configuración confirmada**.
   - Conecta la imagen al ejercicio propio: elegir una portada del álbum o almacenamiento privado que ya existe, reutilizar una imagen compatible, o mostrar la ilustración del patrón.
   - Medición: la muestra de 36 nombres de AUD1 §3.2, como prueba, debe acercarse a la búsqueda (72,2 % top-1 y 86,1 % top-3). Escribe el resultado.
   - La interfaz nueva del creador (tablero E7) **no** es de este paquete: deja la API lista para ella y adapta lo mínimo para que lo actual siga funcionando.

## Pruebas

Las de AUD1 §1.5, §2.8 y §3.6, en `app/src/test`. Por cada punto, la reproducción en rojo primero y luego el arreglo.

## Gradle (importante)

- Solo con `bash /c/kw/tools/gradle_slot.sh /c/kw/u12/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas>`.
- Hay **una sola ranura global** de Gradle en la máquina: espera tu turno.
- Tu herramienta Bash corta a los 10 minutos, así que lanza Gradle **en segundo plano** redirigiendo a un log (por ejemplo, `> C:/kw/logs/mot-<n>.log 2>&1`). Espera con bucles `until grep -qE "BUILD (SUCCESSFUL|FAILED)" log; do sleep 20; done` de 9 minutos como máximo cada uno, repetidos hasta que termine. No lances otra corrida mientras haya una tuya viva.
- Ejecuta **solo** `:app:compileBaseDebugKotlin` y `:app:testBaseDebugUnitTest --tests <tus filtros y las pruebas existentes de las clases que tocas>`. **No** ejecutes la batería completa: la hace el orquestador al integrar.
- Nunca `--stop` ni matar java. Sin teléfono ni adb. Sin push.

## Commits e informe

- Commits pequeños por punto, en español, que terminen con `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Informe en `C:\kw\u12\docs\screens-v2\reports\MOT.md`: qué cambió, las mediciones antes y después (Columna, número de avisos, top-1 y top-3), las pruebas con su comando y resultado, y los riesgos para integrar con la rama principal.
