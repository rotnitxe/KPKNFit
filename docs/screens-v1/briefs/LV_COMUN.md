# LV · Común de la ronda «sesión en vivo perfecta» (2026-10-15, noche)

La ronda corrige la sesión en vivo con la lista del usuario (puntos 1–15). Cada paquete LVn tiene su brief. Lee también:
- `docs/screens-v1/briefs/LR1_COMUN.md`: la piel de la sesión en vivo, sus tokens y sus piezas KPKN;
- `docs/wizard-v3/BRIEF_COMUN.md`: entorno, Gradle, hojas de render y git.

## Reglas del usuario que mandan
- **Refinar, no quitar.** No desaparece ninguna función. Si algo se mueve, sigue a la vista.
- **Nada de botones gigantes.** Es un problema crónico de la app. Los controles se miden con los demás elementos: píldoras de
  32–40 dp de alto, texto de 13–15 sp y objetivos táctiles de 48 dp con el relleno incluido.
- **Textos en español de producto:**
  - sin signos de exclamación;
  - coma decimal;
  - espacio inseparable antes de «kg», «%», «s» y «min»;
  - «×» con espacios (`KpknSetFormat`).
- **Movimiento:**
  - las animaciones son lentas y prácticas, sin exagerar;
  - con «reducir movimiento», estado final estático;
  - ningún bucle corre fuera de pantalla ni con la app en segundo plano.

## Entorno
- Pantalla de referencia: el Z Flip5 del usuario, 1080 × 2640 a 480 dpi (360 × 880 dp), al 100 % y al 130 % de letra.
- Gradle SOLO por `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native …`. Nunca `--stop` ni matar procesos java.
- **Prohibido:**
  - el teléfono, adb y el emulador (los prueba el orquestador al integrar);
  - push;
  - otros worktrees y el checkout principal `C:\Users\valen\Documents\KPKNFit`.
- **Hojas de render:**
  - usa las que ya existen (`render/LiveSessionRefineRenderSheetTest`, `render/LiveSessionPrepRenderSheetTest`,
    `render/LiveSessionRelatorRenderSheetTest` y `render/EditarSeriesRenderSheetTest`);
  - añade escenas nuevas en ellas;
  - cada prueba llama a `onlyWhenAsked()` antes de dibujar (W2);
  - **mira tus PNG antes de cada nueva corrida.**
- **Git:**
  - commits por rutas (`git add <rutas>`), nunca `-A`;
  - sin `stash`, `reset --hard`, `rebase` ni push;
  - atribución al final del mensaje del commit:
    - agentes Claude: `Co-Authored-By: Claude <modelo> <noreply@anthropic.com>`;
    - Antigravity: `Co-Authored-By: Antigravity (Gemini 3.8 Flash) <noreply@google.com>`.

## Informe final
- Por punto: qué cambió y dónde.
- Las rutas de los PNG.
- Los hashes de los commits.
- Pruebas: número y resultado de cada corrida.
- Lo que no pudiste hacer, con el porqué.
