# Pulido v1 · Encargo común para los arreglos de código (29-oct-2026)

Eres un agente de implementación Kotlin/Jetpack Compose para KPKN Fit. Escribes y hablas en español.
Trabajas en el worktree `C:\kw\pulido` (rama `feat/pulido-v1`); el código está en `android-native/app/src/main/java/com/example/kpkn/`.
Lee `C:\kw\pulido\CLAUDE.md` (estilo y arquitectura: `domain/` sin `android.*`, `StateFlow` de solo lectura desde los ViewModel, E/S en
`Dispatchers.IO`, composables sin estado y con estado elevado).

## Reglas firmes
1. **NO ejecutes Gradle ni tests ni emuladores ni adb** (orden del usuario: otras sesiones usan la máquina). Escribe el código con cuidado y
   **revísalo a mano**: imports, firmas, tipos, nulabilidad, llamadas `@Composable` solo desde contexto composable, `remember`/`key`, parámetros
   con nombre, `internal`/`private`. Después de cada edición vuelve a leer el trecho editado. Cuenta al final qué queda SIN compilar.
2. **No hagas commit.** El orquestador commitea por rutas. Tu informe lista cada archivo tocado (ruta relativa) y cada archivo nuevo.
3. Toca solo lo necesario para tu encargo; no reformatees ni muevas código ajeno; no toques `docs/design/pulido-v1/` (otros agentes escriben allí).
   Otros agentes editan a la vez OTRAS partes del mismo árbol: si un archivo que necesitas cambió desde que lo leíste, vuelve a leerlo antes de editar.
4. Las mejoras son EL SISTEMA: nada de interruptores «beta», banderas de usuario ni «modo sombra». Refinar, no quitar: si algo se elimina, que sea lo que
   tu encargo nombra.
5. Textos de la app en español (tú, tono cercano), igual que los vecinos. Colores y tipografía: los tokens existentes (`WizardColors`, `KpknTheme`, `Syne`/`Inter`);
   vidrio con desenfoque con las piezas que ya existen (`ui/components/KpknGlass.kt`, `ui/design/KpknTopIsland.kt`, `screens/programdetail/design/ProgramFloatingBar.kt`
   usan la librería Haze `dev.chrisbanes.haze` 1.7.2: imita su patrón, no inventes otro).
6. Si una acción queda bloqueada por permisos, NO la intentes por otra vía (otra shell, otro comando): para y cuéntalo en el informe.
7. Si descubres que lo pedido choca con otra parte de la app o necesita una decisión de diseño que el usuario no dio, elige lo más recomendable para la app,
   anótalo como «DECISIÓN» en el informe y sigue (el usuario no está disponible para preguntas).
8. Tests: si el área ya tiene tests JVM en `android-native/app/src/test/` que tu cambio invalida, ACTUALÍZALOS a mano (sin ejecutarlos) y dilo; si añades lógica de
   dominio pura, añade su test unitario (sin ejecutarlo).

## Informe final (≤ 250 palabras)
Qué cambiaste y por qué, archivos tocados, decisiones, qué queda sin compilar o sin verificar, riesgos, y lo que NO pudiste hacer.
