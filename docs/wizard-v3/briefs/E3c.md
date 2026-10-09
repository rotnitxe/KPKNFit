# E3c · Intro interactiva de los RINGS con el simulador real (punto 20)

Worktree indicado en tu encargo, rama `v3/e3c` (sale de `feat/wizard-v3` con E1, E3a y E3b integrados). Lee antes
`docs/wizard-v3/BRIEF_COMUN.md` y, del plan, «Punto 20» y «§3‑bis C». La torre `KpknTowerRings` (E1, `ui/components/rings/`) y el paso
`RINGS_INTRO` en la ruta (E3b) ya existen.

## 1. `RingsIntroSimulator` (puro, `domain/auge/RingsIntroSimulator.kt`)
- Estado: `heavySession: Boolean`, `hoursAfter: Float` (0–72), `manual: Map<RingChannelId, Int>` (ajustes de la persona) — usa un enum de
  dominio propio de 3 canales (no importes el de la UI).
- `valuesAt(state): Triple/obj (columna, musculos, energia)` con la MISMA ley del motor que usa E3a: sesión `B(t) = 100 · exp(−s · d(Δt) / C)`
  y ajuste manual `B(t) = 100 · (B₀ / 100)^d(Δt)`, con C = 90 / 75 / 70 y las curvas de `AugeUtils` (reutiliza las funciones que E3a dejó
  en `InitialRecoveryEvidencePolicy` o en un ayudante compartido; no copies fórmulas).
- La sesión pesada de la demostración (sentadilla y peso muerto pesados): cargas fijas tales que, recién terminada, Músculos ≈ 45, Columna ≈ 55
  y Energía ≈ 70 (documenta los números). Sin sesión, todo 100.
- Determinista; sin reloj del sistema.

## 2. UI del paso `RINGS_INTRO` (archivo nuevo `screens/onboarding/rings/RingsIntroStep.kt` + la rama en `SetupRingsSteps.kt`)
- Arriba, `KpknTowerRings` de 200 dp centrada, que se llena escalonada al entrar.
- Tocar un anillo lo destaca (`highlighted`) y cambia la línea de debajo (`WizardTypography.note`, `textMuted`, fundido 160 ms):
  - Músculos: «Cuánto se han recuperado tus músculos.»
  - Energía: «Tu reserva para entrenar fuerte.»
  - Columna: «La carga que acumula tu espalda.»
  - Sin toque: «Toca un anillo para ver qué mide.»
- Demostración (debajo, sin cajas; separadores de 1 dp):
  1. Acción de texto con icono (48 dp) **«Simular un entreno pesado»**: los anillos bajan con animación (600 ms) a los valores de la sesión.
     Tras pulsarla pasa a «Quitar el entreno».
  2. **Línea de tiempo** «Después del entreno» de 0 a 72 h: una regla horizontal con marcas cada 12 h y etiquetas «0 h», «24 h», «48 h»,
     «72 h» (reutiliza el dibujo de `CountRuler`/`MarkRuler` si encaja; si no, uno sencillo con el mismo lenguaje). Arrastrar mueve
     `hoursAfter` y los anillos siguen sus curvas en vivo (sin animación extra: el dedo manda). Solo activa con la sesión simulada.
  3. **«¿Te sientes distinto? Ajústalo»**: con un anillo destacado, un deslizador 0–100 de ese canal (reutiliza `AdjustableRingCompact` o
     `MinimalMuscleSlider` de la app si se pueden usar aquí sin arrastrar su pantalla; búscalos con Grep). Al soltar, el anillo salta a ese
     valor y la curva sigue desde ahí (ley de ancla). Línea bajo el deslizador: «Si no coincide con cómo te sientes, ajústalo: KPKN usa tus
     ajustes para afinar tu ritmo de recuperación con cada entreno.»
- Cierre: «Con unas preguntas rápidas calculamos cómo llegas hoy.» (`WizardTypography.stepSubtitle`). El botón del paso es el del anfitrión;
  si el anfitrión permite una etiqueta por paso, «Empezar».
- Nada de lo que se toca aquí se guarda (es una demostración): el paso se confirma sin dato. No menciones el sueño (el motor no lo usa).
- Movimiento reducido: sin llenado escalonado ni animación de bajada; la línea de tiempo y el ajuste siguen funcionando.
- Accesibilidad: la torre con su descripción; la acción con `Role.Button`; la línea de tiempo con `progressBarRangeInfo` y acciones
  «Más tarde»/«Antes» (12 h).

## Archivos permitidos
Nuevos `domain/auge/RingsIntroSimulator.kt` y `screens/onboarding/rings/RingsIntroStep.kt`; `SetupRingsSteps.kt` (solo la rama
`RINGS_INTRO`); `domain/auge/InitialRecoveryEvidencePolicy.kt` solo si hay que exponer como `internal` la ley que ya existe; y pruebas.

## Pruebas
- Nueva `RingsIntroSimulatorTest` (JVM): sin sesión, 100; con sesión a 0 h, ≈ 45/55/70 (±2); monótono con las horas; a 72 h todos ≥ 90;
  el ajuste manual fija el valor en Δt = 0 y luego sube; determinista.
- Nueva `RingsIntroStepTest` (Compose): tocar Columna cambia la línea; «Simular un entreno pesado» baja los anillos; la línea de tiempo existe solo
  con la sesión; el paso no escribe nada en el borrador.
- Hoja de render (`KPKN_RENDER_DIR=C:/kw/shots/v3/e3c`): el paso al entrar, con la sesión simulada a 0 h y a 36 h, y con Columna destacada.
- Filtro: `*RingsIntro*`, `*KpknTowerRings*`. Antes, `:app:compileDebugKotlin`.
