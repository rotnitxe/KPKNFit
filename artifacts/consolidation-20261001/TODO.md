# KPKN Fit — Consolidación Wizard + Reparación de sesión/editor + Cierre (solo flavor **Base**)

Estado final al **2026-10-02 ~22:30** · Árbol `C:\Users\valen\Documents\KPKNFit` · Rama local `consolidation/2026-10-02-wizard-session-repair` · Evidencias en `artifacts/consolidation-20261001/`

Leyenda: ✅ hecho y verificado · ⚠️ limitación/decisión · ⬜ abierto (quién y cuánto)

> Health queda **fuera de alcance** por indicación del usuario. Todo lo de abajo es Base.

---

## 0. Semáforo

| Frente | Estado |
|---|---|
| Reparaciones de sesión en vivo / editor (Room v28) | ✅ |
| Wizard: planes adaptativos, preview == activado, razones tipadas | ✅ |
| Suite completa JVM Base sobre el árbol final (**X-full-2**) | ✅ **4.270 pruebas · 615 clases · 0 fallos · 2 omitidas** (voz, ajenas); incluye `SessionTemplateRepositoryTest` |
| Wizard en dispositivo (emulator-5582 / QA10) | ✅ **40/40**; recorridos largos 7/7 con `assertCommittedRoom` (lo previsualizado == lo guardado) |
| Pruebas de calentamiento (antes omitidas) | ✅ 3/3 ejecutadas y verdes |
| Cierre del plan de asesoramiento (tandas 1–4) | ✅ salvo lo manual (§3) |
| APK release en el escritorio | ✅ `KPKN Beta 15 base release v2.apk` (versionCode 34, firma debug, R8 + regla ML Kit) |
| Commit del estado consolidado | ✅ hecho (4 commits en la rama local, sin push) |

---

## 1. Cierre aplicado (ROADMAP_CIERRE.md)

| Ítem | Resultado |
|---|---|
| B-01 gobierno del plan | ✅ `docs/WIZARD_PLAN_DEVIATIONS.md`: DEV-r2-01 aceptada (cita del dueño), tabla literal vs aceptado, DEV-r2-02 (tolerancia) **implementada**, DEV-r2-03 (R13 diferido, R14 absorbida), DEV-r2-04 (R16 parcial: PHUL sí, PHAT no), DEV-r2-05 (fin de bloque continúa solo con aviso) |
| B-02 tolerancia de glúteos | ✅ techo blando 17,5 (último recurso, planes propios, aviso). Q1/Q2/Q3/matriz idénticos; pasada Q2 calibrada: **0 filas viables cambian** |
| B-03 aviso de volumen alto + notas sin jerga en la revisión | ✅ (no se vio en pantalla: ningún recorrido generó exceso) |
| C1 grasa corporal con acción explícita | ✅ (+ selector atenuado tras «Omitido») |
| C2 meta diaria del Home al cambiar de plan | ✅ verificado en dispositivo (4 planes el mismo día → meta del último) |
| C3 singular («1 día») | ✅ pasada 1 (wizard, catálogo y revisión). Pasada 2 (resto de pantallas) **no se hizo** |
| C4 tiempo del paso PLAN | ✅ instrumentado (`SetupPlanSweep`); medición en emulador: frío ~12–14 s (9–11 s son carga del catálogo), después 0,9–4 s. **Falta medir en teléfono real** |
| C5 plan activo único | ✅ la columna manda; sin reserva en el Home |
| D1.1 recorridos largos | ✅ 7/7 en dispositivo |
| D1.2 `SessionTemplateRepositoryTest` | ✅ reescrita con base en memoria; ya no se excluye |
| D1.3 calentamiento QA10 | ✅ `suites/warmup-qa10.txt`; 3/3 verdes |
| D1.4 / D2.5 inventario obsoleto | ✅ UI y pruebas retiradas (movidas a `closeout/deleted/`); enums y datos persistidos intactos |
| D1.5 hooks de benchmark | ✅ mantenidos y documentados (`docs/REPAIR_BENCHMARK_HOOKS.md`) |
| D1.6 iOS | ⚠️ error de compilación corregido a ojo en `KPKNFitTests.swift`; **sin ejecutar** (no hay macOS) |
| D2.1 aviso de fuerza pendiente tras cardio | ✅ verificado en dispositivo |
| D2.2 botón tapado por el teclado | ✅ verificado en dispositivo |
| D2.3/D2.8/D2.9 «Reemplazar todo» con plantilla | ✅ sin cierre de app, con guardia de sesión en curso y de doble toque |
| D2.4 `android.*` fuera de `domain/` | ✅ `KeyValueStore` + adaptador; archivos y claves de preferencias idénticos; prueba guardián |
| H-UI propuestas legibles | ✅ nombre del ejercicio, texto llano, confirmación, caducidad, insignia |
| H-IDENT / H-DESCARGA | ✅ el mismo ejercicio sube junto; una subida no cae en la semana de descarga |
| R16 (solo PHUL) | ✅ doble progresión KPKN; PHAT sin cambios |
| H-CICLO | ✅ aviso único al entrar en el nuevo bloque |
| H-BW | ✅ flexión: rodillas → estándar → pies elevados (requiere apoyo) |
| H-VERIF | ✅ la reserva no movida ya no cuenta como cumplida; verificado en dispositivo (propuesta, aplicar, carga siguiente) |
| Defecto hallado en la ronda de emulador: diálogo en inglés al terminar entreno | ✅ **corregido** (el cálculo del resumen se reiniciaba cada ~1 s y cancelaba el productor); verificado 3× en dispositivo |

Notas por paquete: `closeout/notes/*.md` · registro: `closeout/PROGRESS.md`.

---

## 2. APK release (`KPKN Beta 15 base release v2.apk`)
- Firmado con la clave debug de Android (no hay `keystore.properties` en este repo; la 14.8 usó una clave «KPKN Fit» que no está aquí). Instala como **actualización** sobre builds debug; si el teléfono tiene una build con otra firma, Android la rechazará (no desinstales sin respaldar).
- 362 MiB: ~240 MB son PNG de ilustraciones de ejercicios (`res/drawable-nodpi`). Pasarlas a WebP lo reduciría mucho (⬜ opcional).
- Prueba de arranque del primer release (R8): PASS con una salvedad (ML Kit «Invalid component registrar»); la regla de conservación ya está en `proguard-rules.pro` y este v2 la incluye, pero **no se re-probó en emulador**.

---

## 3. Abierto

1. ✅ **Commit** hecho el 2026-10-03 con tu autorización, en la rama local `consolidation/2026-10-02-wizard-session-repair` (HEAD `5c281a02c`, sin push): tooling/guías, iOS, backend y el estado consolidado (465 archivos). NO incluye lo que otras sesiones editan en paralelo (nutrición/alimentos: `FoodImporter`, `NutritionScreen`, `build.gradle.kts`, `.gitignore`, `docs/audits`…); en `NutritionRepository.kt`, `NutritionViewModelTest.kt` y `MainActivity.kt` solo entraron mis hunks. Tampoco los APK, `device-evidence/`, `logs/`, `snapshot-*` ni los `.db` de fixtures. El push sigue sin hacerse.
2. ⬜ **iOS** (dueño, 20–40 min): correr las 15 pruebas XCTest en el iMac siguiendo `closeout/IOS_RUN_GUIDE.md`.
3. ⬜ **Teléfono real** (dueño, 20–30 min): medir el paso PLAN con el APK v2 (log `SetupPlanSweep`, frío y caliente, 3 y 5 días). Si el barrido en frío pasa de ~8–10 s: añadir «Evaluando plan N de M…» (45–60 min).
4. ⬜ Baja prioridad: fecha de Nutrición mezcla inglés y español en el emulador (locale); el botón de registrar solapa un poco la rueda RIR en pantallas pequeñas; aviso rojo «La receta fija programa Domingo.» muy escueto; pasada 2 de singulares; WebP de ilustraciones.
5. ⚠️ No verificado en pantalla: aviso de volumen alto en revisión, RECHAZAR propuesta, insignia en pestañas Estructura/Volumen, estado «Omitido» atenuado (compila y tiene pruebas JVM).

## 4. Decisiones cerradas por el dueño (2026-10-02, «apliquemos todo lo que me dices y recomiendas»)
Glúteos: se conserva el plan implementado y se acepta tolerancia hasta 17,5 · puente 1 vez/semana · cardio del Atleta mantiene minutos (R13 diferido, R14 descartada) · PHUL con progresión KPKN (PHAT no) · mismo ejercicio en dos días sube junto · grasa corporal con acción explícita · meta diaria cambia al activar otro plan · cardio+fuerza pendiente: aviso con botón · fin de bloque continúa con aviso único · hooks de benchmark se mantienen · reserva no movida no cuenta como cumplida.

## 5. No se hizo (decidido)
R13 · R14 · B-04 · B-05/B-06/B-07 · PHAT · D2.6 · D2.7 · C3 pasada 2 · aviso de volumen para PHUL/PHAT · workflow de CI en GitHub Actions.

## 6. Limitaciones heredadas (siguen vigentes)
- §12.4 R13/R14 (cardio) y PHAT sin progresión nativa (ver DEV-r2-03/04).
- iOS: XCTest sin ejecutar.
- Perfetto A/B de 24 etapas descartado.
- `SetupInventoryTypingUiTest` retirado (obsoleto).

## 7. Métricas
| Corrida | Alcance | Resultado |
|---|---|---|
| g24 (antes del cierre) | Base completa | 595 · 4.103 · 1 (fila F, arreglada en g27) |
| X-full-1 | Base completa (sin SessionTemplate) | 613 · 4.262 · **0** |
| **X-full-2** | Base completa (todo) | **615 · 4.270 · 0 fallos · 2 omitidas** |
| Dispositivo (5582) | wizard-ui 40 + calentamiento 3 + ManagedLoad 1 + manual D2.1/D2.2/C2/H-VERIF | todo PASS; verificación de K2 PASS |
| Release | `assembleBaseRelease` | OK (10 min), firma verificada |
