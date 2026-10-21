# Kit del agente (Claude Code: Sonnet 5.5 / Haiku 5.5 · Gemini 3.8 Flash · DeepSeek v4.1 Flash)

**Para qué sirve:** tu prompt te da un worktree, un ID de paquete y quién eres. Este kit dice CÓMO se trabaja aquí. Lee, en este orden: este kit, `docs/macrofactor-parity/briefs/COMUN.md` (reglas), tu brief `briefs/<ID>.md` (**manda sobre todo lo demás**), `CLAUDE.md` y el código que vas a tocar.
**Si algo de COMUN.md habla de «Codex» (pie de commit, `timeout` de 3.600.000 ms, PowerShell), este kit lo sustituye.**

## 1. Dónde trabajas
- Solo en TU worktree (`C:\kw\<nombre>`, rama propia). No cambies de rama, no toques otros worktrees ni `C:\kw\mfp` salvo que tu prompt lo diga, no hagas `merge`/`rebase`/`push`/`stash`/`reset --hard`.
- Edita con las herramientas Read/Edit/Write. `git add` SIEMPRE por rutas explícitas (nunca `-A` ni `.`).
- Los archivos que no están en la lista de tu brief no se tocan; si los necesitas, descríbelos en el informe.
- **No edites `docs/macrofactor-parity/CAMBIOS_VISIBLES.md` ni el README** (los escribe el orquestador y chocarían entre pistas): tus filas de «cambios visibles» van en `docs/macrofactor-parity/cambios/<ID>.md` (una tabla: pantalla · qué cambia · antes → después · por qué).

## 2. Gradle (el cuello de botella de toda la máquina)
- UNA cola global, por orden de llegada, compartida con otras sesiones: una corrida puede esperar 20–60 min. **Esperar no es un fallo.** Nunca `./gradlew` directo, `--stop`, `clean`, `GRADLE_USER_HOME` ni matar procesos `java`.
- Comando (siempre con la salida a un log numerado):
  `bash /c/kw/tools/mfp_gradle.sh /c/kw/<worktree>/android-native <tareas…> > /c/kw/logs/mfp-<ID>-<n>.log 2>&1`
  - Tareas del flavor base: `:app:compileBaseDebugKotlin`, `:app:testBaseDebugUnitTest --tests "<filtro>"`.
- **Cómo esperar sin que se corte tu herramienta de shell (tope de ~10 min por llamada):** lanza el comando en SEGUNDO PLANO (`run_in_background`) o con `&`, y después espera con bucles acotados de ≤ 9 min, repitiéndolos hasta que termine:
  `timeout 540 bash -c 'until grep -qE "BUILD (SUCCESSFUL|FAILED)" /c/kw/logs/mfp-<ID>-<n>.log; do sleep 20; done'; tail -5 /c/kw/logs/mfp-<ID>-<n>.log`
  (si el bucle termina por el `timeout` y el log no dice BUILD, vuelve a lanzarlo; el log muestra «puesto N en la cola»).
- **Agrupa antes de compilar:** escribe el código Y sus tests completos, revisa a mano que compila (tipos, imports, nombres reales: léelos del repo, no los supongas) y SOLO ENTONCES entra en la cola. Cada vuelta cuesta 25–40 min de reloj: el plan es 3 vueltas por paquete como máximo (1.ª compilación + tests nuevos, 2.ª correcciones, 3.ª regresión final con los filtros del brief).
- Primera vuelta: `:app:compileBaseDebugKotlin` + SOLO tus tests nuevos (`--tests` con sus clases). La regresión de los filtros amplios solo en la vuelta final.
- Resultado de tests: Gradle no imprime el total si todo pasa. Usa `python C:\kw\tools\mfp_verify.py C:\kw\<worktree> --since <commit-de-partida> --baseline C:\kw\mfp\docs\macrofactor-parity\ROJOS_BASE.txt`: cuenta los XML, separa fallos HEREDADOS de NUEVOS y revisa constantes prohibidas, archivos tocados y pies de commit.
- **Fallos heredados:** master ya tiene 51 tests rojos (sesión en vivo, editor, catálogo, análisis de técnica por vídeo, anillos…), medidos con una corrida COMPLETA de master (12.032 tests, 21-oct). Un fallo que está en `ROJOS_BASE.txt` NO es tuyo: no lo toques ni lo «arregles». Uno que NO está en esa lista es NUEVO: si es de un área que no tocaste, anótalo (nombre y mensaje) y repite ese test solo (`--tests`) antes de darlo por intermitente; si es de tu área, es tuyo.
- **Arnés JVM (solo lógica pura, sin la cola de Android):** si tu paquete tiene lógica en `domain/**` (nutrición, cuerpo, entrenamiento, fuerza, energía…), itera y valida sus pruebas ahí ANTES de tu turno en la cola de Android y mientras esperas: `KPKN_SLOT_LANE=jvm bash /c/kw/tools/gradle_slot.sh /c/kw/mfp-jvm test --offline -PappRoot=C:/kw/<tu-worktree>/android-native/app "-PtestInclude=<globs relativos a src/test/java, separados por comas>" "-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m" > /c/kw/logs/mfpjvm-<ID>-<n>.log 2>&1` (una clase: añade `--tests "<FQN>"`; resultados en `C:\kw\mfp-jvm\build\test-results\test\`; guía y límites en `C:\kw\mfp-jvm\LEEME.md`: no cubre `screens.*`, Room, Robolectric ni Compose; cada cambio de `-PappRoot` recompila todo el main, 5–15 min). Es un carril aparte (una corrida a la vez, 5 GB libres): no sustituye a `:app:testBaseDebugUnitTest` y tu informe debe separar lo corrido en el arnés de lo corrido en Android. Escribe los tests de dominio con JUnit 4 y sin Android para que quepan en el arnés. Los errores de compilación (`grep -E "^e: "`) del arnés valen como aviso de que la compilación de Android también fallará.

## 3. Calidad: lo que más se rompe con modelos pequeños
- **Lee antes de escribir:** firmas, nombres y paquetes reales salen del código (grep/Read), no de la memoria. Una importación inventada cuesta una vuelta entera de cola.
- **Sin efectos colaterales:** antes de cambiar una función compartida, `grep` de todos sus consumidores. Los consumidores fuera de tu lista NO cambian de cifra: añade una API paralela. No reescribas la expectativa de un test que no es tuyo.
- **Datos del usuario:** nada de migraciones destructivas ni de reescribir registros históricos. Guardados durables: espera a Room y publica DESPUÉS.
- **Texto de la app en español**, tono breve; accesibilidad real (≥ 48 dp, descripciones); nada de color como única señal.
- **Clean-room:** los coeficientes prohibidos de COMUN.md no aparecen ni en código, ni en tests, ni en comentarios. Tus parámetros salen de tu propia validación y se documentan con su razón.
- **Arquitectura:** `domain/` sin `android.*`; `StateFlow` de solo lectura; I/O en `Dispatchers.IO`.
- **Tope de 3 intentos por problema.** Si no sale, PARA y descríbelo con el log (un informe honesto vale más que un «verificado» falso).

## 4. Commits
- Pequeños, en español (`feat(nutricion): …`, `fix(entreno): …`, `test(…): …`, `debug(mfp): …` para las actividades de vista previa).
- Última línea del mensaje según quién seas: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` · `Co-Authored-By: Claude Haiku 5.5 <noreply@anthropic.com>` · `Co-Authored-By: Gemini 3.8 Flash <noreply@google.com>` · `Co-Authored-By: DeepSeek v4.1 Flash <noreply@deepseek.com>`.
- Termina con `git status` limpio de lo tuyo.

## 5. Teléfono y emulador
Prohibido `adb`, el teléfono y el emulador. La QA con dispositivo la hace el orquestador. Para pantallas nuevas entregas la hoja de render (PNG a 360 dp, 100 % y 130 %, en `KPKN_RENDER_DIR=C:/kw/shots/mfp/<ID>`) y la vista previa `<Nombre>HarnessActivity` (en `app/src/debug/`, commit aparte) según COMUN.md.

## 6. Informe final (≤ 400 palabras, español, sin pegar código)
1. Commits (hash + título). 2. Archivos tocados fuera de tu lista y por qué. 3. Gradle: comandos, resultado y la cuenta de `mfp_verify.py` (suites/tests/fallos nuevos). 4. Decisiones y su razón. 5. Lo que NO verificaste o no pudiste hacer. 6. Para el orquestador: lo que debería mirar primero.

## 7. Si eres un modelo gratuito (DeepSeek/Gemini Flash)
Haces encargos acotados y mecánicos o revisiones de SOLO LECTURA. Un encargo de escritura tiene lista cerrada de archivos y se verifica con tests que corre el orquestador; no improvises fuera de ella. Una revisión devuelve hallazgos numerados (ruta:línea, antes → después con ejemplo, gravedad), sin editar nada.
