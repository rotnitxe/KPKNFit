# CAT-A · Catálogo de ejercicios (E1–E6, E8 y E9)

**Worktree:** `C:\kw\u15`, rama `sv2/catalogo`, nacida de `feat/screens-v2` @ `373729ae0`. Trabaja **solo** ahí.

Otros agentes trabajan en paralelo; no reescribas sus zonas:
- `u14`: marco del editor (`SessionEditorScreen`).
- `u12`: motor del creador y parecidos (E7).
- `u11`: sesión en vivo.
- `u13`: programas.

## Lectura previa

- `CLAUDE.md`
- `docs/screens-v2/PLAN_IMPLEMENTACION.md`: sus leyes
- `docs/screens-v2/DECISIONES_PROTOTIPOS_2026-10-20.md`: las decisiones del usuario para E1, E2 y E7
- `docs/screens-v2/maps/MAPA_editor_catalogo.md`, sección 3
- `docs/screens-v2/audits/AUD2_bugs_editor_catalogo.md`: sus arreglos del catálogo ya están en la rama y no se pueden romper

Tableros aprobados en `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\` (solo lectura): **E1-Anadir, E2-Busqueda, E3-Seleccion, E4-SuperserieCatalogo, E5-Reemplazar, E6-SinResultados, E8-CargandoError, E9-ElegirTecnica**. Los informes `INFORME_R1.txt` e `INFORME_R2.txt` dicen de qué código sale cada valor.

## Qué construir

1. **E1 · Añadir.**
   - Sin volumen en porcentajes. Se muestra el aporte a series efectivas según el rol muscular, con los valores de la app (`EMI` y pesos por rol): por ejemplo, «Primario · 1 serie» o «Secundario · 0,5 series».
   - No inventes valores.
2. **E2 · Búsqueda y variantes.** La altura de la polea, y el agarre si existe, se configuran en el mismo resultado, con un selector visual compacto, sin pasar por la ficha.
3. **E3 · Selección múltiple** con orden.
4. **E4 · Superserie desde el catálogo**, con un máximo de 4 (arreglo AUD2-21).
5. **E5 · Reemplazar**, también desde el editor.
6. **E6 · Sin resultados.**
7. **E8 · Cargando y error.**
8. **E9 · Elegir para Técnica.**

**Leyes:**
- Refinar, no quitar.
- No inventes opciones.
- Objetivos táctiles de 44–48 dp o más.
- Paleta y tipografía KPKN, como en los tableros.

## Pruebas

- **Robolectric/Compose:** cada tablero, incluidos el aporte por rol con números reales y la altura de polea en el sitio.
- **Renders** a 360 × 880 con el arnés que ya existe.
- Adapta las pruebas existentes del catálogo sin rebajar aserciones sin explicarlo.

## Reglas

- **Gradle:** solo con `bash /c/kw/tools/gradle_slot.sh /c/kw/u15/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas>`, en primer plano. Hay cola.
- **Tareas permitidas:** `:app:compileBaseDebugKotlin`, `:app:compileBaseDebugAndroidTestKotlin` y `:app:testBaseDebugUnitTest --tests` con filtros de las pruebas del catálogo, las del editor que toques y las nuevas. **No** ejecutes la batería completa.
- **Prohibido:**
  - `--stop`, matar java, un `GRADLE_USER_HOME` paralelo o `JAVA_TOOL_OPTIONS`;
  - teléfono o adb;
  - push.
- **Commits** en español, que terminen con `Co-Authored-By: Codex <noreply@openai.com>`.

## Informe

En `docs/screens-v2/reports/CAT-A.md`.
