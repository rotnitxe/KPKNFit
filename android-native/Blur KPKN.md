# Blur KPKN — Efecto "DarkMica" oficial

> **LEE ESTO ENTERO ANTES DE TOCAR UNA SOLA LÍNEA DE BLUR.**
> Este documento es la **única fuente de verdad** del efecto mica oscura (DarkMica; evolucionado
> desde Liquid Glass) en la app nativa de KPKN. No es una sugerencia: es un contrato. Si tu
> superficie con blur no sigue este patrón, **el blur no va a funcionar** y vas a entregar un
> círculo gris plano creyendo que hiciste mica.

El look validado es el del **roadmap dock inferior del editor de sesiones** y el **FAB del asistente**.

**Regla de oro:** no inventes valores nuevos ni tunees el blur por superficie. Reutiliza siempre
`kpknGlassStyle()` y `Modifier.kpknGlass(...)` de `ui/components/KpknGlass.kt`.

---

## 0. LA LEY DE HERMANDAD (si sólo lees una cosa, que sea esta)

**Haze sólo difumina lo que está DEBAJO en el orden de dibujo, y sólo si la superficie de vidrio es
un HERMANO dibujado DESPUÉS del `hazeSource`.**

```
Box(fillMaxSize)                     ← raíz común
├── Box.hazeSource(hazeState)        ← 1º: TODO el contenido que se verá borroso
│   └── Scaffold { LazyColumn ... }
├── HeroGlassFab(...).align(BottomEnd)   ← 2º: hermano ENCIMA (kpknGlass)
└── SessionContextNavigator(...)         ← 3º: hermano ENCIMA (hazeEffect)
```

Consecuencias que NO son negociables:

- Si el nodo con `hazeEffect`/`kpknGlass` está **anidado dentro** del subtree marcado con
  `hazeSource`, Haze no muestrea nada → **blur muerto**.
- Si el nodo de vidrio se dibuja **antes** que el `hazeSource`, tampoco hay nada que muestrear →
  **blur muerto**.
- No hay workaround, no hay flag, no hay parámetro que lo arregle. **O es hermano encima, o no hay
  blur.**

---

## 1. ANTI-PATRONES — errores que YA se cometieron en este repo

### ❌ ANTI-PATRÓN #1 — FAB en el slot `Scaffold.floatingActionButton`

**Este es el bug real que rompió el FAB del asistente.** El slot `floatingActionButton` vive
*dentro* del Scaffold. Si el Scaffold está dentro de `hazeSource` (que es el patrón correcto de
pantalla), el FAB queda **anidado** dentro del source → el blur muere.

```kotlin
// ❌ NUNCA. El FAB queda anidado dentro de hazeSource → círculo plano sin blur.
Box(Modifier.fillMaxSize().hazeSource(state = hazeState)) {
    Scaffold(
        floatingActionButton = {
            Box(Modifier.size(56.dp).kpknGlass(hazeState, CircleShape)) { ... }
        },
    ) { padding -> ... }
}
```

```kotlin
// ✅ CORRECTO. Overlay hermano dibujado DESPUÉS del hazeSource.
Box(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
        Scaffold(
            snackbarHost = { ... },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // SIN floatingActionButton: el FAB de vidrio vive fuera.
        ) { padding -> LazyColumn(...) { ... } }
    }

    HeroGlassFab(
        summary = uiState.augeSummary,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = fabBottomPadding)
            .zIndex(260f),
        hazeState = hazeState,
        onClick = { viewModel.openSheet(SessionEditorSheet.AUGE) },
    )
}
```

### ❌ ANTI-PATRÓN #2 — `FloatingActionButton` de Material3 para el vidrio

`FloatingActionButton` pinta su propia `Surface` con `containerColor` y elevación (tonal + sombra).
Eso **tapa el blur** aunque el árbol esté bien construido. No existe combinación de colores que lo
salve de forma fiable: no lo uses.

```kotlin
// ❌ NUNCA para glass.
FloatingActionButton(
    onClick = onClick,
    containerColor = Color.Transparent,   // ni así: sigue habiendo Surface + elevación
    modifier = Modifier.kpknGlass(hazeState, CircleShape),
) { Icon(...) }
```

```kotlin
// ✅ CORRECTO: Box + kpknGlass + clickable + Icon. Nada más.
Box(
    modifier = modifier
        .size(56.dp)
        .kpknGlass(hazeState, CircleShape)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Icon(
        imageVector = Icons.Default.Visibility,
        contentDescription = "Abrir Asistente de sesión",
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(24.dp),
    )
}
```

### ❌ ANTI-PATRÓN #3 — fondo sólido encima del vidrio

Cualquier `background(...)`, `containerColor`, `Surface(color = ...)` o `Card` con color opaco sobre
la superficie de vidrio **tapa el desenfoque**. La superficie de vidrio no lleva fondo propio: el
scrim oscuro ya viene dentro de `kpknGlassStyle()`.

```kotlin
// ❌
Modifier.background(DarkEditorChip, CircleShape).kpknGlass(hazeState, CircleShape)
// ✅ (fallback SOLO cuando hazeState == null)
val glassModifier = if (hazeState != null) Modifier.kpknGlass(hazeState, CircleShape)
                    else Modifier.background(DarkEditorChip, CircleShape)
```

### ❌ ANTI-PATRÓN #4 — reordenar o duplicar modifiers

El orden es `clip(shape)` → `hazeEffect(...)` → `border(...)`. `Modifier.kpknGlass(...)` ya lo hace
en ese orden. No lo repliques a mano ni metas `clip` después del `hazeEffect` (las esquinas se
rompen).

### ❌ ANTI-PATRÓN #5 — un `HazeState` por componente

Un único `val hazeState = remember { HazeState() }` **por pantalla**, compartido por todas las
superficies de vidrio. Estados separados = superficies que muestrean fuentes distintas o vacías.

### ❌ ANTI-PATRÓN #6 — inventar valores "sólo para este caso"

Nada de `blurRadius = 24.dp` porque "se ve mejor aquí". Todas las superficies deben ser idénticas.
Si de verdad hay que cambiar el look, se cambia en `KpknGlass.kt` y afecta a todo.

### ❌ ANTI-PATRÓN #7 — `ModalBottomSheet` / `AlertDialog` / `Dialog` window

Estas APIs de Material crean **otra ventana Android**. Haze no puede muestrear el
`hazeSource` de MainActivity cross-window → blur muerto. El "arreglo" con
`kpknWindowGlass` (scrim opaco) **no es KPKN Blur**: es un panel sólido que parece
ventana cuadrada.

```kotlin
// ❌ NUNCA para glass vivo.
ModalBottomSheet(containerColor = Color.Transparent) {
    Box(Modifier.kpknWindowGlass(shape)) { ... }  // fallback opaco, no blur
}
AlertDialog(modifier = Modifier.kpknWindowGlass(shape), ...)
Dialog { Surface(Modifier.kpknWindowGlass(shape)) { ... } }
```

```kotlin
// ✅ CORRECTO: wrappers canónicos (portalean fuera del hazeSource).
KpknSheet(onDismissRequest = onDismiss) { /* bottom sheet real + kpknGlass */ }
KpknAlertDialog(onDismissRequest = ..., confirmButton = { ... }, ...)
KpknGlassDialog(onDismissRequest = ...) { /* dialog custom centrado */ }
```

`kpknWindowGlass` queda **solo** para Popups/DropdownMenus (limitación de Haze).

Para una superficie concreta que necesite más contraste, `KpknSheet` permite un
`additionalGlassScrim` semitransparente. Se dibuja sobre el blur, antes del contenido, y no debe
reemplazarse por un fondo opaco ni convertirse en un valor global.

### ❌ ANTI-PATRÓN #8 — sheet que no es sheet

Un bottom sheet KPKN es full-bleed inferior, solo esquinas superiores redondeadas,
handle + drag. Nunca un card centrado con 4 esquinas para un sheet.

---

## 2. Librería

Usamos [Haze](https://github.com/chrisbanes/haze) (`dev.chrisbanes.haze`), ya incluida.

- `Modifier.hazeSource(state)` → marca el **contenido que se verá borroso** por detrás.
- `Modifier.hazeEffect(state, style)` → dibuja la **versión borrosa** sobre esa superficie.
- `HazeState` → objeto compartido entre source y effect (`remember { HazeState() }`).

---

## 3. Valores canónicos (NO tocar)

Definidos en `ui/components/KpknGlass.kt`. Look **DarkMica**: gris oscuro translúcido +
blur leve (ni Liquid Glass cristalino ni panel 100% sólido).

| Parámetro     | Valor                              | Rol                                                         |
|---------------|------------------------------------|-------------------------------------------------------------|
| `Base`        | `Color(0xFF0E0E0E)` alpha `0.94`   | Underlay gris neutro (menos transparente).                  |
| `EffectAlpha` | `0.42`                             | Opacidad de la capa blur. **No subir** para “más blur”.     |
| `blurRadius`  | `100.dp`                           | Desenfoque fuerte (rings/fondo ilegibles).                  |
| `tint`        | `Color(0xFF0E0E0E)` alpha `0.58`   | Tinte neutro sobre el sample.                               |
| `noiseFactor` | `0.02`                             | Grano discreto.                                             |
| borde         | `White` @ `0.05`, `1.dp`           | Filo sobrio.                                                |
| fallbacks     | `#0E0E0E` ~opaco                   | Sin Haze / cross-window.                                    |

API: `Modifier.kpknGlass` / `Modifier.kpknHazeEffect` = `background(Base)` + `hazeEffect { alpha }`.
No uses `hazeEffect(kpknGlassStyle())` a pelo.

---

## 4. API lista para usar

`ui/components/KpknGlass.kt` expone:

- `kpknGlassStyle(): HazeStyle` — el estilo canónico.
- `Modifier.kpknGlass(hazeState, shape, withBorder = true)` — clip + blur + borde en un solo paso.
- `object KpknGlass` — constantes (`BlurRadius`, `Tint`, `Scrim`, `NoiseFactor`, `BorderColor`).

Overlays globales (`ui/components/`):

- `KpknSheet` — bottom sheet in-composition + portal + blur vivo.
- `KpknSheetContentTheme` / `KpknSheetTokens` — contraste del Asistente (texto blanco,
  superficies oscuras para que `Surface`/`Card` no pinten negro, paneles 0.06).
- `KpknAlertDialog` / `KpknGlassDialog` — diálogos centrados con el mismo contrato.
- `KpknPortal` + `LocalKpknOverlayHost` — monta el overlay como hermano del `hazeSource`
  de MainActivity (obligatorio si el call site vive dentro del NavGraph).

**Lineamientos de sheets (altura, contraste, tabs, checklist):** ver
[`KPKN Sheets.md`](./KPKN%20Sheets.md) — contrato de contenido y UX encima de este blur.

Dismiss del sheet: handle + drag en sheets wrap-content + nested-scroll overscroll
(hacia abajo arriba del scroll) en sheets con `LazyColumn`/`verticalScroll`.

### Receta completa (copia esto)

```kotlin
// 1) Un HazeState por pantalla
val hazeState = remember { HazeState() }

// 2) Raíz Box + hazeSource con TODO el contenido de fondo
Box(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
        Scaffold(...) { padding -> /* contenido real que se verá difuminado */ }
    }

    // 3) Superficies de vidrio: HERMANAS, después del hazeSource
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .kpknGlass(hazeState, shape = RoundedCornerShape(28.dp)),
    ) { /* contenido del dock */ }
}
```

### Receta sheets / diálogos (desde cualquier pantalla)

```kotlin
// El call site puede estar dentro del NavGraph (anidado en hazeSource).
// KpknSheet/KpknAlertDialog portalean solos al host de MainActivity.
// Altura = wrap content (tope maxHeightFraction). Ver KPKN Sheets.md.
if (showSheet) {
    KpknSheet(onDismissRequest = { showSheet = false }) {
        /* contenido — sheet anclado abajo, solo top redondeado, drag handle */
    }
}
if (showDialog) {
    KpknAlertDialog(
        onDismissRequest = { showDialog = false },
        title = "…",
        text = "…",
        confirmLabel = "OK",
        onConfirm = { showDialog = false },
    )
}
```

---

## 5. CHECKLIST OBLIGATORIO — antes de decir "el blur está listo"

**No declares el blur terminado sin marcar los 10 puntos.** Si alguno falla, el blur está roto
aunque compile.

- [ ] 1. Existe **un solo** `remember { HazeState() }` en la pantalla.
- [ ] 2. El contenido de fondo está envuelto en `Modifier.hazeSource(state = hazeState)`.
- [ ] 3. Cada superficie de vidrio es **hermana** del `hazeSource`, no descendiente. Lo verifiqué
       leyendo el árbol de composables, no asumiéndolo.
- [ ] 4. Cada superficie de vidrio se declara **después** del `hazeSource` en el mismo `Box` padre.
- [ ] 5. **Cero** superficies de vidrio en `Scaffold.floatingActionButton`, `bottomBar`, `topBar` o
       cualquier slot del Scaffold que esté dentro del `hazeSource`.
- [ ] 6. **Cero** `FloatingActionButton` / `Card` / `Surface` de Material envolviendo el vidrio.
       Sólo `Box` + `kpknGlass` + `clickable`.
- [ ] 7. Ningún `background(...)` opaco ni `containerColor` sólido sobre la superficie de vidrio.
- [ ] 8. Uso `Modifier.kpknGlass(...)` (o exactamente `clip` → `hazeEffect(kpknGlassStyle())` →
       `border`). No hay valores de blur inventados.
- [ ] 9. Hay fallback definido para `hazeState == null` (fondo sólido oscuro:
       `Color.Black.copy(alpha = 0.55f)` / `DarkEditorChip`).
- [ ] 10. **Verificación visual en dispositivo/emulador:** el contenido de detrás se ve difuminado y
       se deforma al hacer scroll. Un color plano uniforme = blur muerto, aunque el diff se vea bien.

> El punto 10 no es opcional. Los bugs de blur **siempre** compilan. Si tocaste Kotlin, reinstala
> (`./gradlew installBaseDebug`) y mira la pantalla antes de reportar.

---

## 6. Referencias reales en el código

| Qué | Dónde |
|-----|-------|
| Estilo y modifier canónicos | `ui/components/KpknGlass.kt` |
| hazeSource raíz + portal de overlays | `MainActivity.kt` (`LocalHazeState` + `LocalKpknOverlayHost` + tabbar/header) |
| Tabbar / header (referencia visual) | `MainActivity.kt` bottom bar · `HomeScreen.kt` `HomeTopBar` |
| Sheet canónico | `ui/components/KpknSheet.kt` |
| Lineamientos de sheets (UX/contraste/altura) | [`KPKN Sheets.md`](./KPKN%20Sheets.md) |
| Dialog canónico | `ui/components/KpknAlertDialog.kt` · `KpknGlassDialog.kt` |
| Fuente del blur + overlays hermanos (editor) | `screens/sessioneditor/SessionEditorScreen.kt` (`Box` raíz → `hazeSource` → `HeroGlassFab` → `SessionContextNavigator`) |
| FAB de vidrio correcto | `screens/sessioneditor/components/SessionHeroParts.kt` → `HeroGlassFab` |
| Dock de vidrio correcto | `screens/sessioneditor/components/SessionContextNavigator.kt` |
| Assistant sheet (usa `KpknSheet`) | `screens/sessioneditor/components/sheets/AssistantSheet.kt` → `AssistantGlassOverlay` |

Si vas a añadir una nueva superficie de vidrio **chrome** (dock/FAB/header), **copia la
estructura de `SessionEditorScreen.kt` / tabbar**. Si es sheet o modal, usa `KpknSheet` /
`KpknAlertDialog` / `KpknGlassDialog`. No improvises.
