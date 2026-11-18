# Brief común · ola «Bienvenida y guía v1» (30-oct-2026)

Aplica a todos los paquetes. El plan aprobado está en `C:\kw\guia\docs\design\guia-v1\PLAN.md`: lee la fase de tu paquete (diagnóstico con rutas y líneas). Tu brief propio está en `briefs/<paquete>.md`.

## Entorno
- Repo KPKN Fit (Android, Kotlin, Compose). Trabaja SOLO en tu worktree (`C:\kw\<nombre>`, checkout parcial) y tu rama. Nunca toques `C:\Users\valen\Documents\KPKNFit` ni otro worktree (otra ola, Pulido v1, trabaja en `C:\kw\pu-*` y `C:\kw\pulido`).
- App en `android-native/app/src/main/java/com/example/kpkn/`, pruebas en `android-native/app/src/test/java/com/example/kpkn/`. Lee `CLAUDE.md`. `domain/` sin `android.*`.
- Idioma de UI, comentarios y KDoc: español, breve; imita la densidad de comentarios del código vecino.
- Lee el archivo (o la función entera) antes de editarlo. No inventes APIs: busca la firma real. Si el brief no cuadra con el código, elige lo más conservador y anótalo.
- Sin cambios de Room (esquema congelado). Sin tocar archivos fuera de tu lista: si hace falta, descríbelo en el informe.
- Nunca quites funciones al usuario; solo bugs y código muerto que tu brief nombre.

## Gradle (por la cola, siempre en segundo plano con `run_in_background`, un solo Gradle tuyo a la vez)
- Lógica pura (`domain/**`, modelos): `bash /c/kw/tools/kpkn_jvm.sh <worktree> "-PtestInclude=<globs>" --tests "<clase>" > /c/kw/logs/guia-<paquete>-<n>.log 2>&1`.
- Pantallas, Robolectric, Room, render: `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:testBaseDebugUnitTest --tests "<clase>" [--tests ...] > /c/kw/logs/guia-<paquete>-<n>.log 2>&1`. Solo compilar: `:app:compileBaseDebugKotlin`.
- El carril Android está muy cargado (15–21 min por corrida más espera): agrupa todo antes de lanzar, pide solo clases concretas, nunca la suite entera, nunca `androidTest`, `clean` ni `--stop`. Estado: `bash /c/kw/tools/gradle_cola.sh`. Mira el final del log; no sondees en bucle con `sleep`.
- Pruebas Compose: copia los imports de una prueba existente. `onNodeWithTag`, `performClick`, `assert`, `click()`… son extensiones y SE importan; `assertExists`, `onNode` son miembros y NO.
- Si falla una prueba ajena a tu cambio, no la persigas: anótala.
- Teléfono y emulador: no, salvo que tu brief lo pida.

## Git
- Commits pequeños en español (`fix(wizard): …`, `feat(tour): …`), por rutas explícitas (nunca `git add -A`), terminados con `Co-Authored-By: <tu modelo> <noreply@anthropic.com>`. Sin `stash`, `reset --hard`, `rebase` ni `push`.

## Informe final (≤ 400 palabras)
1. Commits. 2. Archivos tocados y firmas públicas nuevas o cambiadas. 3. Pruebas: comando y línea de resultado. 4. Cambios necesarios fuera de tu lista. 5. Riesgos, dudas y lo no hecho. Sin pegar código.
