# Cierre 2026-10-02 — registro de avance

Autorización del dueño: «apliquemos todo lo que me dices y recomiendas» (2026-10-02). Especificación: `ROADMAP_CIERRE.md`. Reglas: `closeout/AGENT_COMMON.md`. Paquetes: `closeout/specs/*.md`. Notas por paquete: `closeout/notes/`.

## Git
- Rama local `consolidation/2026-10-02-wizard-session-repair` (desde `master` e520812e6). Un solo commit hecho: `e05743908` (deja de rastrear `android-native/hs_err_pid62254.log`).
- El resto de commits quedó BLOQUEADO por el sistema de permisos («Git Destructive»). No se reintenta. Pendiente: que el dueño lo ejecute o lo autorice de forma explícita.
- Respaldo de fuentes previo al cierre: `snapshot-pre-closeout/` (1.509 archivos).

## Oleada 1 (en paralelo)
| Paquete | Ítems | Estado |
|---|---|---|
| A-docs-tools | B-01, D1.4, D1.5, D1.3, D1.6, D1.2 | HECHO (nota A-docs-tools.md; SessionTemplateRepositoryTest 2/2 verde -> quitar de -Exclude; 189 pruebas offline OK) · agente ab209fcb5b80a8dda |
| N-nutrition | C5, C2, D1.1 | HECHO con reservas (93+1 pruebas verdes; androidTest solo compila; assertCommittedRoom pendiente de emulador) · agente a1f66cd60e92162fb |
| W1-wizard-ui | C3, C1 | HECHO (197 pruebas verdes) · agente a9f7e01e21e990be0 -> W2 HECHO (D2.5, D2.4, C4, B-03; compila; tests dirigidos verdes tras reintento; androidTest sin ejecutar) |
| P1-program-detail | D2.3, D2.8, D2.9, H-UI | HECHO (154 pruebas verdes, 9 clases) · agente ab3d19f436482b051 -> P2 HECHO (H-IDENT, H-DESCARGA, R16-PHUL, H-CICLO, H-BW, H-VERIF código; pruebas verdes; falta emulador) |
| K-workout-ui | D2.2, D2.1 | HECHO (compila; 13 clases/54 pruebas verdes; falta pasada de emulador, pasos en notes/K-workout-ui.md) · agente a120a6c22bc807051 |
| G-glutes-tolerance | B-02 | HECHO (151 pruebas verdes; Q1/Q2/Q3/matriz idénticos; pasada calibrada: 0 filas cambian) · agente a4d2ca8c235356a90 |

## Oleada 2 (pendiente)
- P2: H-IDENT, H-DESCARGA, R16 (PHUL), H-CICLO, H-BW, H-VERIF (+ RIR no movido ≠ reserva cumplida).
- W2: B-03 (aviso de volumen alto en la revisión), D2.5 (código muerto de inventario), D2.4 (android.* en domain), C4 (instrumentar tiempo de búsqueda).
- Ronda de emulador (5582): pruebas largas del wizard, WizardGate, warmup-qa10, D2.1/D2.2/H-UI, H-VERIF.
- X-FINAL: suite completa JVM, wizard-ui + workout-editor-ui en emulador, regenerar evidencia Q2/Q3/matriz, APK release nuevo en el escritorio, TODO.md.

## No se hace (decisiones del dueño)
R13, R14, B-04, B-05, B-06, B-07, PHAT, D2.6, D2.7, segunda pasada de C3, aviso de volumen para PHUL/PHAT.

## Manual del dueño (no automatizable aquí)
- iOS (D1.6): correr las 15 pruebas XCTest en el iMac (ver `closeout/IOS_RUN_GUIDE.md`, cuando esté).
- C4: medir el paso PLAN en un teléfono real con el APK release.

## Notas
- Release smoke (APK desktop): PASS_WITH_LIMITATIONS; añadida regla keep ML Kit en proguard-rules.pro (sin compilar aún). Reconstruir release al final.

## Integración (en curso)
- X-assemble-1: APK debug + androidTest con todos los cambios.
- Siguiente: suite completa JVM (X-full) + ronda de emulador 5582.
- X-assemble-1: OK (APK debug+androidTest 17:58). X-full-1 (suite completa JVM, sin SessionTemplateRepositoryTest) en curso. Ronda de emulador: agente acf8f91a042675a36 (nota E-emulator-round.md).
- X-full-1: 4.262 pruebas / 613 clases / 0 fallos / 2 omitidas (voz). ✅
- Ronda de emulador: wizard-ui 40/40 (FullJourney 7/7 con assertCommittedRoom por primera vez), warmup 3/3, ManagedLoad 1/1, manual D2.2/D2.1/C2 PASS, progresión PASS parcial. Defecto medio: diálogo 'The coroutine scope left the composition' al terminar entreno -> K2 (agente a120a6c22bc807051). Defecto bajo: texto ≈25% tras Omitido -> W3 (agente a9f7e01e21e990be0).
- Pendiente tras K2/W3: recompilar, tests dirigidos, repetir verificación, APK release (con regla ML Kit), TODO.md, REPO_STRUCTURE.md.
- K2 HECHO: causa real = produceState del resumen de cierre se reiniciaba cada ~1 s (WorkoutScreen.kt); arreglado + WorkoutFinishController; verificado en dispositivo (3x Terminar hasta acá, guardar, salir con guardado en curso: sin diálogo). W3 HECHO.
- Siguiente: X-release-2 (APK release con regla ML Kit) -> escritorio; luego suite completa final X-full-2; TODO.md, REPO_STRUCTURE.md.
