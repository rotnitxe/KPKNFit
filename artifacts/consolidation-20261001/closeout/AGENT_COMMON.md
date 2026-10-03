# Reglas comunes de los agentes del cierre (2026-10-02)

Léelas COMPLETAS antes de tocar nada. Aplican a todos los paquetes de trabajo del cierre.

## 1. Contexto
- Proyecto KPKN Fit: app Android nativa (Kotlin / Jetpack Compose / Room v28), repo `C:\Users\valen\Documents\KPKNFit`, módulo `android-native\app`, paquete `com.example.kpkn`. Código en `android-native\app\src\main\java\com\example\kpkn\`, tests JVM (Robolectric) en `...\src\test\java\...`, instrumentados en `...\src\androidTest\java\...`.
- **Solo flavor BASE.** Health no se usa: no compiles, pruebes ni instales Health.
- Guía del repo: `C:\Users\valen\Documents\KPKNFit\CLAUDE.md` (domain/ sin `android.*`; datos y archivos en data/; `StateFlow` de solo lectura en ViewModels con `asStateFlow()`; `val`/`data class` inmutables; trabajo pesado en `Dispatchers.IO`; Compose con estado elevado).
- El dueño del producto habla español: todo texto de interfaz, mensajes y documentación en español claro (sin jerga interna), con el mismo tono que el código vecino.
- Plan de cierre (la especificación): `C:\Users\valen\Documents\KPKNFit\artifacts\consolidation-20261001\ROADMAP_CIERRE.md`. Cada ítem tiene Estado actual / Recomendación / Evidencia con ruta:línea; las líneas pueden haberse movido 1-30 líneas: verifica SIEMPRE leyendo el código actual antes de editar. Lo marcado [I] es inferencia: compruébala (por lectura o con un test) antes de cambiar comportamiento.
- El estado y los pendientes generales están en `...\artifacts\consolidation-20261001\TODO.md`. Plan externo del wizard: `C:\Users\valen\.opencode\plan\KPKNFit\2026-09-28-planes-adaptativos-wizard.md` (solo lectura, NO lo edites). Desviaciones documentadas: `C:\Users\valen\Documents\KPKNFit\docs\WIZARD_PLAN_DEVIATIONS.md`.

## 2. Decisiones del dueño (ya resueltas; el dueño dijo «apliquemos todo lo que me dices y recomiendas»)
1. Glúteos: se CONSERVA el plan implementado (puente 1 vez/semana en Músculo, 2 en Atleta; glúteos 15–16 series); NO se vuelve al calendario literal de r2. «Si se pasa de series, no hay problema siempre y cuando no sea tanta la diferencia.»
2. Tolerancia de glúteos: techo blando de **17,5 series/semana** (límite 16), solo como último recurso, solo en planes propios KPKN, con aviso de «volumen alto»; nunca más de 17,5. Sin tolerancia para otros músculos ni para PHUL/PHAT originales.
3. Puente de glúteo se mantiene en 1 vez/semana (no B-04).
4. Cardio del Atleta: mantiene los minutos del plan (R13 diferido, R14 absorbida/descartada): solo se documenta.
5. PHUL (original y adaptado) usa la doble progresión KPKN rotulada «Recomendación KPKN §12.4» (R16 solo PHUL; PHAT no por ahora; las adaptaciones heredan de su original).
6. Mismo ejercicio en dos días de la semana: sube JUNTO (misma carga) — identidad por ejercicio, sin día ni posición, deduplicando exposiciones por log/sesión.
7. Grasa corporal: Continuar queda bloqueado hasta una acción explícita (mover/tocar la figura, escribir una medición, u «Omitir este paso»); Omitir debe limpiar un valor ya elegido.
8. Meta diaria del Home: si se activa un plan DISTINTO el mismo día, la meta de hoy pasa a la del plan nuevo; si solo se editan las calorías del MISMO plan, valen desde mañana con aviso en pantalla.
9. Cardio terminado con fuerza pendiente: opción B (el resumen avisa «Te quedan N series sin hacer…» con botón «Seguir con ellas»); no se cambia la navegación automática.
10. Fin del bloque: el programa continúa solo con las últimas cargas y se avisa UNA vez (H-CICLO); sin pantalla de oferta.
11. RepairBenchmarkTrace: se MANTIENE y se documenta cómo reactivarlo (no se retira).
12. Un RIR/reserva que el atleta no movió NO cuenta como «reserva cumplida» para proponer una subida (se aplica en H-VERIF tras confirmarlo).
13. Valores por defecto: dos `testTag` inofensivos en el Home para cerrar las pruebas largas; escalera corporal con un segundo escalón solo para flexión (pies elevados) con comprobación de apoyo; borrar la prueba de inventario obsoleta.
14. NO se hace: R13 (cardio sube de minutos), R14, B-04, B-05, B-06, B-07, PHAT, D2.6, D2.7, segunda pasada de C3, aviso de volumen para PHUL/PHAT.

## 3. Prohibiciones (rígidas)
- **GIT: SOLO LECTURA.** Permitido: `git status`, `git diff`, `git log`, `git show`, `git ls-files`, `git blame`. PROHIBIDO cualquier escritura: `add`, `commit`, `restore`, `checkout`, `switch`, `stash`, `reset`, `clean`, `rm`, `mv`, `merge`, `rebase`, `push`, `tag`, `branch -d`... El sistema de permisos ya bloquea el commit del dueño; no intentes rodearlo.
- **No borres archivos de forma permanente.** Si hay que eliminar un archivo, MUÉVELO a `C:\Users\valen\Documents\KPKNFit\artifacts\consolidation-20261001\closeout\deleted\<ruta relativa al repo>` (crea las carpetas) y anótalo en tu nota. Quitar código dentro de un archivo con Edit es normal.
- Respaldo del estado previo al cierre: `...\artifacts\consolidation-20261001\snapshot-pre-closeout\` (copia de `src\main\java`, `src\test`, `src\androidTest`, `schemas`, `docs`). No lo modifiques.
- NO edites: `android-native\app\src\main\assets\exercise_catalog_v2.json`, nada bajo `catalog\` ni `scripts\catalog_v2_*` (trabajo de otro agente ya terminado); ni regeneres datasets; ni toques `ios-native` salvo que tu paquete lo diga.
- NO toques emulator-5554 (Pixel compartido) ni emulator-5556, jamás. Solo si tu paquete lo autoriza: emulator-5582 (AVD `KPKNWizchatQA`, usuario QA10 id 10) y emulator-5580 (AVD `KPKNFitSessionAudit20260929`). Nunca `adb kill-server`.
- NO subas nada a la red ni hagas push; sin dependencias nuevas de Gradle.
- No cambies versionCode/versionName ni el esquema Room (se queda en v28): ningún ítem lo necesita.

## 4. Gradle (una sola corrida a la vez en toda la máquina)
Toda invocación de Gradle pasa por el serializador (usa un mutex del sistema; si otro agente compila, esperas tu turno; puede tardar). SIEMPRE con `run_in_background: true` desde PowerShell y espera el aviso de término (no hagas sondeo con sleeps encadenados). Plantilla:

```powershell
& 'C:\Users\valen\Documents\KPKNFit\artifacts\consolidation-20261001\tools\gradle-serial.ps1' `
  -Project 'C:\Users\valen\Documents\KPKNFit\android-native' `
  -Tasks @('compileBaseDebugKotlin','compileBaseDebugUnitTestKotlin','--continue') `
  -LogName '<prefijo-de-tu-paquete>-compile-1.log' -Label '<texto corto>'
```
- `-LogName` ÚNICO (prefijo de tu paquete + paso); el log queda en `...\consolidation-20261001\logs\<LogName>` y el recibo (exitCode, duración) en `...\receipts\<LogName sin .log>.receipt.json`. Ya incluye `--no-daemon --console=plain` y la configuración de JVM: NO ejecutes `gradlew` directamente.
- Tests dirigidos: `-Tasks @('testBaseDebugUnitTest','--continue','--tests','*.ClaseA','--tests','*.ClaseB')`. Una clase por `--tests`. Resultados XML: `android-native\app\build\test-results\testBaseDebugUnitTest\`; resumen compacto: `python -X utf8 ...\tools\summarize_tests.py <etiqueta>` (Python = `C:\Users\valen\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe`).
- Excluye siempre `-Exclude '*.SessionTemplateRepositoryTest'` en corridas que puedan incluirla (se cuelga en este entorno), salvo que ese sea tu objetivo (entonces usa `-TaskTimeoutMin 12`).
- androidTest: `-Tasks @('compileBaseDebugAndroidTestKotlin')` (≈1–2 min). Para empaquetar APK: `assembleBaseDebug assembleBaseDebugAndroidTest` (las instalaciones en emulador las hace quien coordina salvo que tu paquete diga otra cosa).
- Coste real medido: compilar main+tests tras cambios en main = 15–23 min; una corrida dirigida típica = 25–40 min. Planifica pocas corridas: **máximo 3 compilaciones y 1 corrida de tests dirigidos por paquete** (más una corrida de reintento solo de las clases que fallen). Los errores `e:` en archivos que NO son tuyos pueden venir de otro agente a mitad de edición: espera 5–8 min y repite UNA vez; no los arregles tú; anótalos.

## 5. Trabajar en un árbol compartido con otros agentes
- Hay otros agentes editando el mismo árbol en paralelo (cada uno con su lista de archivos). Edita SOLO los archivos de tu paquete; si necesitas tocar uno ajeno, haz el cambio mínimo, relee el archivo justo antes con Read y usa Edit con trozos pequeños (nunca reescribas archivos completos que no creaste).
- No reviertas ni «limpies» cambios que no hiciste. No uses `git checkout/restore`.
- Mantén el árbol compilable entre ediciones: primero declara la API nueva, luego sus usos; cambios coherentes por archivo.
- Archivos CRLF: algunos fuentes usan CRLF (p. ej. `SetupWizardFullJourneyUiTest.kt`); conserva el estilo de fin de línea de cada archivo.

## 6. Calidad
- Cada cambio de comportamiento lleva sus pruebas (JVM en `src\test` espejando paquetes; instrumentadas solo cuando haga falta UI). Nombres de pruebas descriptivos en el estilo vecino; nada de pruebas que dependan del reloj sin inyección; nombres de bases Room de prueba CORTOS (límite de ruta de Windows).
- Antes de afirmar «hecho» comprueba por lectura que cada llamada nueva existe con esa firma (Grep) y que los imports están. Respeta `domain/` sin `android.*`.
- Evidencia siempre: cita ruta:línea reales del código actual. Marca [C] comprobado / [I] inferido.
- Si descubres que una recomendación del plan es incorrecta o peligrosa, NO la apliques a ciegas: documenta la causa con evidencia, aplica la alternativa más segura y avísalo en tu informe.

## 7. Entrega
- Escribe una nota en `...\artifacts\consolidation-20261001\closeout\notes\<id-del-paquete>.md` con: ítems cerrados / parciales / no hechos (y por qué), lista EXACTA de archivos tocados (ruta relativa al repo, indicando creado/modificado/movido), pruebas añadidas o cambiadas (clases) y la lista de clases de test que hay que ejecutar para verificar tu paquete, comandos Gradle ejecutados con su LogName y resultado (exitCode, tests pasados/fallados con el mensaje de cada fallo), riesgos abiertos, y decisiones que tomaste.
- Tu respuesta final debe incluir el mismo resumen (corto) y el estado: DONE / DONE_WITH_CONCERNS / BLOCKED, con la razón.
