# Blur KPKN — Efecto "Liquid Glass" oficial

> **LEE ESTO ENTERO ANTES DE TOCAR UNA SOLA LÍNEA DE BLUR.**
> Este documento es la **única fuente de verdad** del efecto vidrio esmerilado (Liquid Glass) en la
> app nativa de KPKN. No es una sugerencia: es un contrato. Si tu superficie con blur no sigue este
> patrón, **el blur no va a funcionar** y vas a entregar un círculo gris plano creyendo que hiciste
> vidrio.

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

---

## 2. Librería

Usamos [Haze](https://github.com/chrisbanes/haze) (`dev.chrisbanes.haze`), ya incluida.

- `Modifier.hazeSource(state)` → marca el **contenido que se verá borroso** por detrás.
- `Modifier.hazeEffect(state, style)` → dibuja la **versión borrosa** sobre esa superficie.
- `HazeState` → objeto compartido entre source y effect (`remember { HazeState() }`).

---

## 3. Valores canónicos (NO tocar)

Definidos en `ui/components/KpknGlass.kt`. Ya incluyen el pase **"20% más oscuro"** aprobado.

| Parámetro         | Valor                              | Rol                                                       |
|-------------------|------------------------------------|-----------------------------------------------------------|
| `blurRadius`      | `40.dp`                            | Fuerza del desenfoque. Alto = lectura clara de "vidrio".  |
| `tint`            | `Color.White` alpha `0.10`         | Brillo esmerilado sutil (bajó de 0.12 → 0.10).            |
| `backgroundColor` | `Color.Black` alpha `0.26`         | Scrim oscuro = la "oscuridad" (subió de 0.22 → 0.26).     |
| `noiseFactor`     | `0.05`                             | Grano fino, evita banding en degradados planos.           |
| borde             | `Color.White` alpha `0.16`, `1.dp` | Filo del vidrio (hairline).                               |

---

## 4. API lista para usar

`ui/components/KpknGlass.kt` expone:

- `kpknGlassStyle(): HazeStyle` — el estilo canónico.
- `Modifier.kpknGlass(hazeState, shape, withBorder = true)` — clip + blur + borde en un solo paso.
- `object KpknGlass` — constantes (`BlurRadius`, `Tint`, `Scrim`, `NoiseFactor`, `BorderColor`).

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
| Fuente del blur + overlays hermanos | `screens/sessioneditor/SessionEditorScreen.kt` (`Box` raíz → `hazeSource` → `HeroGlassFab` → `SessionContextNavigator`) |
| FAB de vidrio correcto | `screens/sessioneditor/components/SessionHeroParts.kt` → `HeroGlassFab` |
| Dock de vidrio correcto | `screens/sessioneditor/components/SessionContextNavigator.kt` (inline `clip` → `hazeEffect` → `border`, equivalente a `kpknGlass`) |

Si vas a añadir una nueva superficie de vidrio, **copia la estructura de
`SessionEditorScreen.kt`**. No improvises.
