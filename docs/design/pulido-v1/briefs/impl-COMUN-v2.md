# Pulido v1 · Encargo común de IMPLEMENTACIÓN (desde el 30-oct-2026)

Sustituye a `impl-COMUN.md`: el usuario aprobó el lienzo y autorizó Gradle (por la cola) y el teléfono. Eres un agente de implementación Kotlin/Jetpack Compose para KPKN Fit; escribes y hablas en español.
`C:\kw\pulido\CLAUDE.md` (estilo y arquitectura: `domain/` sin `android.*`, `StateFlow` de solo lectura desde los ViewModel, E/S en `Dispatchers.IO`, composables sin estado). Código en `android-native/app/src/main/java/com/example/kpkn/`.

## La fuente de verdad es el lienzo
- Léelo en orden: `DECISIONES_APROBADAS.md` · `maps/<paquete>.md` (qué pieza de la app es cada escena y qué pide el motor) · `briefs/<paquete>.md` (las palabras literales del usuario) · las escenas del paquete.
  Mira cada escena como PNG: `cd C:\kw\pulido\docs\design\pulido-v1 && node build.cjs --out=shots/<prefijo>` y `node tools/shots.cjs shot <id> --build=shots/<prefijo>` (también `--size=360x740`, `--fs=1.3`; `--t=<ms>` congela la animación). Compáralas con tu render.
- **1:1 al pie de la letra.** Lo que el lienzo muestra, se construye igual (medidas en dp, colores, tipografía, textos, estados, orden); lo que la pantalla vieja tiene y el lienzo NO, se **elimina**. Nunca montes el diseño nuevo encima del viejo ni heredes elementos «por si acaso».
  Si algo no está claro, decide lo más fiel al lienzo y anótalo como DECISIÓN; no inventes diseño.
- **Matriz de paridad**: `docs/design/pulido-v1/paridad/<prefijo>.md` lista cada sombra, vidrio, degradado, animación, transición, transformación y movimiento en JS del paquete. No se borran filas. Cuando una fila queda hecha, la marcas
  `[x]` y pones en «Compose» el `archivo.kt:línea` real; si no se puede igualar, `[~]` + `DESV-nn` en `paridad/DESVIACIONES.md` con captura lado a lado. `node tools/paridad-check.cjs <prefijo> --repo=C:\kw\pulido` dice qué falta.
- **Cimientos**: las sombras, el vidrio, los resplandores, las curvas y las duraciones salen de `ui/design/depth` y `ui/design/motion` (presets del catálogo `paridad/cimientos.json`). Prohibido inventar una sombra, curva o duración suelta en una pantalla: si falta un preset, avisa en el informe.

## Reglas firmes
1. **Gradle SOLO por la cola** (`/c/kw/tools/gradle_slot.sh`, guía `/c/kw/tools/GRADLE_GUIA.md`): `bash /c/kw/tools/gradle_slot.sh <worktree>/android-native :app:compileBaseDebugKotlin` y, para pruebas, `:app:testBaseDebugUnitTest --tests "<clase concreta>"`
   (nunca un filtro de paquete entero si evitas); dominio puro por el carril JVM (`/c/kw/tools/kpkn_jvm.sh`). **En segundo plano** (`run_in_background`) y espera la notificación: una corrida de pantallas tarda 15–21 min. Nunca `./gradlew` directo, nunca un segundo Gradle,
   nunca mates procesos java ni toques ranuras o daemons de otras sesiones. Solo flavor `base`. Los PNG de las hojas de render van a la carpeta de la variable `KPKN_RENDER_DIR` (ponla en el entorno de la corrida).
2. **Teléfono**: solo lo usa el orquestador (puerta 4 de `PROTOCOLO_1A1.md`). Tú no uses adb ni emuladores.
3. **No hagas commit ni `git stash`/`checkout --`/`reset`.** El orquestador commitea por rutas. Tu informe lista cada archivo tocado y nuevo (rutas relativas).
4. Toca solo lo necesario para tu paquete; no reformatees código ajeno. Otros agentes trabajan a la vez en otros paquetes (en su propio worktree o en archivos distintos): si un archivo cambió desde que lo leíste, léelo de nuevo antes de editar.
5. Las mejoras son el sistema: nada de interruptores «beta» ni banderas de usuario. Refinar, no quitar funciones alcanzables salvo lo que `DECISIONES_APROBADAS.md` o el lienzo eliminan. Room congelado (v31): sin migraciones.
6. Textos en español (tú, tono cercano) exactamente como en el lienzo. Accesibilidad: `contentDescription`/semántica como en la app actual y objetivos táctiles ≥ 48 dp.
7. Si una acción queda bloqueada por permisos, NO la intentes por otra vía (otra shell, otro comando): para y cuéntalo en el informe.
8. Tests: actualiza los que tu cambio invalida y añade los de la lógica nueva (dominio puro). Corre los de tus clases por la cola. No dejes tests rotos.
9. **Antes de dar nada por hecho, mira tu render contra la escena** (`tools/compare.cjs`) y escribe en el informe los números; no declares «1:1» por haber compilado.

## Informe final (≤ 300 palabras)
Qué hiciste y qué filas de la matriz cerraste (IDs), archivos tocados, los números de comparación por escena, lo que queda abierto (filas pendientes, desviaciones, dudas) y los riesgos. Di con franqueza lo que no pudiste comprobar.
