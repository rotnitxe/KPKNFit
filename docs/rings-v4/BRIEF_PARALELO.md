# Brief paralelo · Rings v4 (21-oct-2026 en adelante)

Se añade a `BRIEF_COMUN.md` y manda sobre él donde se contradigan. Desde el 21-oct los paquetes se reparten en **carriles paralelos**, cada uno en su worktree y su rama, ejecutados por distintos agentes (Codex Sol 6.1, Codex Luna, Antigravity Gemini 3.8 Flash, OpenCode DeepSeek v4.1 Flash). El estado vivo de todos los carriles está en `docs/rings-v4/ESTADO.md` (lo mantiene el orquestador, no lo edites).

## Reglas de convivencia
1. **Solo tu worktree y tu rama** (`r4/<id>`, ya creada desde `feat/rings-v4`). Jamás toques otro worktree, el árbol principal ni `feat/rings-v4`: **no fusionas ni haces `push`**; la integración la hace el orquestador.
2. **Otros carriles tocan archivos vecinos.** Respeta tu lista de archivos permitidos al pie de la letra. Un cambio que necesites fuera de ella se describe en el informe; no se hace.
3. **Antes del informe final** comprueba si `feat/rings-v4` avanzó (`git log --oneline HEAD..feat/rings-v4`). Si avanzó, tráela a TU rama con `git merge feat/rings-v4` (permitido solo en tu rama), resuelve los conflictos con el criterio más conservador, y repite las pruebas de tu paquete antes de entregar. Para `android-native/app/src/test/resources/auge/golden/expected.txt`: si conflictúa, no lo mezcles a mano; toma la versión de `feat/rings-v4`, vuelve a generar con `AUGE_GOLDEN_UPDATE=1` y explica en el informe qué filas cambian por TU paquete.
4. **Solo conocimientos del repositorio:** nada de red, `adb`, teléfono, emuladores ni servicios externos. Los datos personales del usuario no existen aquí.

## Gradle: dos carriles
- **Android** (`:app:compileBaseDebugKotlin`, `:app:testBaseDebugUnitTest --tests …`): siempre por `bash /c/kw/tools/gradle_solo.sh /c/kw/<worktree>/android-native <tareas>` (delega en la cola por orden de llegada de `gradle_slot.sh`, compartida con otras sesiones; cada corrida puede esperar 10–60 min). **Agrupa tus ediciones**; una sola corrida tuya a la vez; PRIMER plano con tiempo máximo ≥ 3 600 000 ms si tu herramienta lo permite, o en segundo plano con log y espera sin sondeos. Prohibido `--stop`, `clean`, matar procesos java, un `GRADLE_USER_HOME` paralelo y la suite entera.
- **JVM sin Android** (`bash /c/kw/tools/r4-jvm.sh <worktree> [--tests <clase completa>]…`): compila `domain/**`, `data/models/**` y las pruebas puras (`domain/auge` y vecinas) en minutos, sin pasar por la cola de Android. Qué cubre y qué no: `C:/kw/r4-jvm/README.md`. **Úsalo para iterar** en lógica pura; la corrida Android oficial de tu paquete se hace al final (y para todo lo que el arnés no cubre: Robolectric, Compose, Room, `screens/`, `data/repository/`).
- Tras cada ejecución comprueba que corrió algo (`python -I docs/rings-v4/tools/count_tests.py --clases`); 0 pruebas = filtro mal.

## Firmas de commit (según TU modelo)
- Codex (Sol/Luna): `Co-Authored-By: Codex <noreply@openai.com>`
- Antigravity (Gemini 3.8 Flash): `Co-Authored-By: Antigravity Gemini 3.8 Flash <noreply@google.com>`
- OpenCode (DeepSeek v4.1 Flash): `Co-Authored-By: OpenCode DeepSeek v4.1 Flash <noreply@opencode.ai>`

## Informe y estado
Tu último mensaje es el informe (≤ 400 palabras) y lo guardas en `docs/rings-v4/reports/<ID>.md` (commit aparte). Empieza con una línea `ESTADO: LISTO | PARCIAL | BLOQUEADO` y, si no es LISTO, la causa raíz y lo que falta. Tope de **3 iteraciones** (compilar → probar → corregir); si no cierra, entrega lo hecho y explica; no sigas dando vueltas.
