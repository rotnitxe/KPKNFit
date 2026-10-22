# Plan completo de la ola MFP hasta el 100 % · prompt de continuación (22-oct-2026)

Este documento está pensado para pegarse como encargo a un agente que continúa la ola. No impone un rol: el usuario dirá si quiere que el agente implemente directamente o que oriente y delegue. Estado detallado en `RELEVO.md` y `README.md` de esta carpeta.

## PROMPT (copiar tal cual)

Continúa la «ola MFP» (paridad con MacroFactor) de la app KPKN Fit (Android, Kotlin/Compose, Room) hasta dejarla completa al 100 %. Responde SIEMPRE en español. Rama de integración `feat/macrofactor-parity`, worktree `C:\kw\mfp`. Tu forma de trabajar (escribir tú el código, o diagnosticar y delegar en subagentes) la decide el usuario: si no la ha dicho, pregúntasela en una línea antes de empezar a escribir código o lanzar agentes.

### Contexto y lecturas obligatorias (en este orden)
1. `C:\kw\mfp\docs\macrofactor-parity\RELEVO.md` (estado exacto por paquete, rutas, commits).
2. `README.md` de la misma carpeta (decisiones D0–D18, tabla de paquetes, «Estado», contrato de Room con la sesión de vídeo).
3. `briefs/COMUN.md`, `briefs/KIT_AGENTE.md` y el brief de cada paquete que toques (`briefs/<ID>.md`).
4. `C:\kw\tools\GRADLE_GUIA.md` (cola de Gradle v10) y, si delegas en Haiku, `C:\kw\tools\HAIKU_GUIA.md`.
5. `ROJOS_BASE.txt` (51 rojos heredados de master) y `data/DRI_ADULTOS.csv` + su LEEME.
Fuente del plan: `artifacts/macrofactor-reverse-engineering-20261020/INFORME_FINAL.md` §6.

### Reglas fijas (no negociables)
- Nunca `git push`; el usuario decide la fusión. Commits solo con ruta explícita (`git commit -- <rutas>`) y pie `Co-Authored-By`.
- **Room congelado**: 30 = calibración (screens-v2), 31 = nutrición, siguiente libre 32 solo con permiso del usuario. En esta rama la BD es v30 con `nutritionDayStateMigration(30)`; al fusionar tras screens-v2 hay que renumerar a 30→31 y actualizar 3 tests de cadena (`NutritionDayStateMigrationTest`, `WorkoutMediaMigration27To28FullRoomTest`, `VideoAnalysisMigrationValidateTest`). Avisar a la sesión de vídeo (VT) ANTES si el orden de fusión no se puede mantener.
- Teléfono (Galaxy Z Flip5): solo con la variante `com.example.kpkn.mfp` mediante `C:\kw\tools\mfp_phone.py`; nunca desbloquearlo ni tocar `.dbg`/`.vtqa`/producción ni `C:\kw\phone.lock`; `--user-ok` solo con autorización expresa del usuario en el chat.
- Gradle SOLO por la cola: Android `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:testBaseDebugUnitTest --tests "<clase concreta>"`; dominio puro `bash /c/kw/tools/kpkn_jvm.sh <wt> "-PtestInclude=com/example/kpkn/domain/<paquete>/**" [--tests FQN]`. No pedir `compileBaseDebugAndroidTestKotlin`. La cola no repite una corrida idéntica de menos de 2 h. Máximo 3 vueltas de Android por paquete: escribir completo, validar dominio en el arnés JVM, encolar una vez.
- «Refinar, no quitar»: nunca quitar ni esconder funciones. Clean-room: prohibidas las constantes de la lista de `briefs/COMUN.md`. Tope de 3 iteraciones por problema; documentar y dejar decidir al usuario.
- Hojas de render: guarda DOBLE de `KPKN_RENDER_DIR` (a nivel de clase y como primera línea de cada test; modelo `HomeCardsRenderSheetTest.onlyWhenAsked()`). Nombres de test con acentos graves: sin `;` ni caracteres ilegales.
- S11 (vocabulario de acciones articulares) y H-i (gobernanza de `catalogRevision`) solo con permiso del usuario.
- Higiene de disco y RAM: sin artefactos residuales al final. No tocar el `.gitignore` raíz sin permiso (su primera línea está corrupta: 2.312 caracteres).
- Si delegas: Haiku 5.5 con `effort: max`, un encargo por agente, contexto pegado, lista cerrada de archivos, criterio de hecho con el comando de la cola; revisa el diff y el log, no la frase «todos los tests pasan». Antes de aceptar un paquete: `python C:\kw\tools\mfp_verify.py <worktree> --since <base> --baseline C:\kw\mfp\docs\macrofactor-parity\ROJOS_BASE.txt` (0 rojos nuevos, Room intacto, sin constantes prohibidas, commits con pie).

### FASE 0 · Retomar (antes de escribir nada)
1. Mirar la cuota semanal (`get_usage`, «Weekly · all models»); se reinicia el 27-oct 07:00 UTC. Si está en 94 % o más, no lanzar trabajo y avisar al usuario.
2. `bash /c/kw/tools/gradle_cola.sh`: ver si siguen vivas las corridas de `mfp` (T4) y `mfpn` (N4 + correcciones de N3). Si la de `mfpn` no está, relanzarla UNA vez.
3. `git status` y `git log` de `C:\kw\mfp`, `mfpn`, `mfpd`, `mfpt5`, `mfph`; confirmar que coincide con `RELEVO.md`.

### FASE 1 · Cerrar lo ya escrito
1. **T4 objetivo automático** (en `feat/macrofactor-parity`, commits `609361f5c`, `6eb0f4c7e`, `25954360c`): leer `C:\kw\logs\mfp-T4-1.log`; `mfp_verify.py --since c696902b9`; corregir rojos nuevos; revisión de solo lectura (Gemini u otro) y revisión del diff; ver hojas de render; cerrar README y `cambios/T4.md`.
2. **N4 reutilización del diario + correcciones de N3** (`mfp/n`, `C:\kw\mfpn`, desde `7ca58599d`): vuelta de Android pendiente (compila las correcciones de N3 de pantalla que aún no se han compilado: permiso de cámara al reanudar, micronutrientes del alimento propio); `mfp_verify.py --since 7ca58599d`; revisión de solo lectura; ver hojas.
3. **Vuelta de regresión amplia de T5a** (`domain.training.*`, `programdetail.*`, `profile.*`), que no se corrió.
4. Actualizar README (filas T4, N4, N3, T5a) y commit de docs con ruta explícita.

### FASE 2 · Nutrición restante
1. **P2-Na** (brief `briefs/P2-Na.md`): recetas con rendimiento, favoritos y hora de la comida, copia de seguridad de preferencias, exportación robusta. Dependencias: N3, N4. Cuidado con Room (congelado: si hace falta esquema, pedir permiso).
2. **P2-Nb** (brief `briefs/P2-Nb.md`): estado de nutrientes, cobertura, contribuyentes y metas DRI desde `data/DRI_ADULTOS.csv` con un test que compare las 240 filas; aviso de exceso solo con los UL que aplican a la ingesta total (D18); sodio 2.300 como techo.
3. **P2-D bloque 2, CSV (D3)**: exportación CSV; depende de P2-Na.
4. **P2-D bloque 4, código huérfano documentado**: usar el inventario de `CODIGO_HUERFANO.md`; solo documentar, no borrar.
5. **P2-E** (brief `briefs/P2-E.md`): etiqueta nutricional por foto de galería, experimental, con OCR ya incluido y pantalla de revisión; nunca guarda sin confirmación (D17). Revisar el brief contra el código final de N3 antes de empezar.
6. **P2-F** (brief `briefs/P2-F.md`): medir p95 de la búsqueda de alimentos en el dispositivo (D16: `LIKE` no cambia sin esa medida).

### FASE 3 · Entreno restante
1. **T5b** progreso por ejercicio + récords (depende de T3 y T5a; atención a que el RIR sube los umbrales de e1RM/PR antiguos).
2. **Antes de T6, P2-Ta y P2-Tb**: comprobar si `feat/screens-v2` (worktree `C:\kw\u11`) ya está fusionado en master. Esos tres paquetes tocan los mismos archivos que esa ola (`SetExecutionCard.kt`, `SetCardTechniqueBack.kt`, `WorkoutSetRecorder.kt`, `ExercisePickerV2Catalog.kt`, `WorkoutStructureSheetsHost.kt`): si ya está fusionado, implementarlos sobre la interfaz nueva; si no, decidir con el usuario.
3. **T6** sustituciones inteligentes en la sesión en vivo.
4. **P2-Ta** myo-reps como preset de rest-pause con marca de variante, sin `TechniqueType` nuevo (D13), y discos por lado en la tarjeta.
5. **P2-Tb** verificación del calentamiento y de la descarga nativa, e interruptores de entrenamiento con UI.

### FASE 4 · Integración
1. Crear `C:\kw\mfpi`, rama `mfp/i`, desde `feat/macrofactor-parity`.
2. Fusionar `mfp/n`, `mfp/d`, `mfp/t5a`, `mfp/h` (y las ramas nuevas que surjan). Conflictos esperados solo de líneas añadidas en `Navigation.kt`, `MainActivity.kt`, `ProfileScreen.kt` (y `RingAdjustBody.kt` por T5a); resolver conservando ambos bloques.
3. Si screens-v2 ya está en master: renumerar la migración de nutrición 30→31, generar `31.json` con KSP, actualizar los 3 tests de cadena y avisar a la sesión VT.
4. Consolidar `cambios/<ID>.md` en `CAMBIOS_VISIBLES.md`.

### FASE 5 · Validación completa
1. Corrida completa de la rama integrada CON `KPKN_RENDER_DIR` puesto; comparar con `ROJOS_BASE.txt` (51 rojos heredados). Confirmar que los 21 rojos sospechosos de contaminación desaparecen o son heredados.
2. Cero rojos nuevos; clasificar cada rojo restante (heredado / contaminación / real) y corregir los reales.
3. Revisar las hojas de render de todos los paquetes a 100 % y 130 % de letra.

### FASE 6 · QA en el teléfono (solo variante `.mfp`)
Instalar la APK `.mfp` y probar por lotes: N3 escáner (aceptación de 2 fotogramas, permiso, entrada manual, no encontrado → crear → encontrado; `BarcodeSelfTestActivity` debe dar `SELFTEST OK 8/8`); N2a/N2b (check-in semanal; **Decline y Postpone sin probar nunca**); D1 tendencia en Cuerpo e Inicio; T3/T4 sugerencias de carga; T5a panel de volumen; D2 hábitos; N4 reutilización del diario; P2-* según se cierren. Tope de 3 iteraciones por defecto; lo que no cierre se documenta.

### FASE 7 · Pendientes menores conocidos
- «Meta 76 kg» tachada al 130 % de letra: elevación fija de 18 dp en `BodyTrendChart.kt` (anterior a la ola; refinar).
- D2: el calendario de hábitos no se recalcula si la app queda abierta de un día a otro sin datos nuevos.
- Planificador de previsión: la clave `inputs["eerKcal"]` no coincide con `result["eerKcal"]`.
- N3: no avisa de duplicados por código de barras ni ordena por formato en SQL; GTIN-14 no soportado (decisión).
- Verificar que la mini gráfica de Inicio (pesos diarios, no tendencia) es lo que se quiere.

### FASE 8 · Cierre y entrega
1. Limpieza: borrar los PNG de T1 en `src/test/.../render/equipment/`, `C:\kw\apk\mfp`, `C:\kw\ref\mf-wip`, capturas; `python C:\kw\tools\mfp_phone.py --uninstall`; worktrees `mfp*` y sus carpetas `build`.
2. README y `CAMBIOS_VISIBLES.md` finales; informe final en español al usuario con: qué se hizo, qué se verificó y cómo, qué no se verificó, decisiones tomadas y hallazgos (bug de snapshot WAL corregido; `.gitignore` raíz corrupto; clave del planificador; 51 rojos heredados de master; cambios visibles de T3).
3. Preguntar al usuario por la fusión y por S11/H-i. Si hay APK que entregar: `G:\Mi unidad\` (raíz) con un LEEME que diga cuál instalar.
