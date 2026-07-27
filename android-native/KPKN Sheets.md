# KPKN Sheets — Lineamientos (Liquid Glass + contraste)

> **Contrato de producto para bottom sheets con blur KPKN.**
> Complementa [`Blur KPKN.md`](./Blur%20KPKN.md) (física del blur / Haze).
> Este documento define **cómo se construye, dimensiona y pinta** un sheet.
> Si un sheet no sigue esto, no es un sheet KPKN aunque compile.

**Referencia visual canónica:** Asistente de sesión  
(`screens/sessioneditor/components/sheets/AssistantSheet.kt` → `AssistantGlassOverlay`).

---

## 0. Resumen en una frase

Un sheet KPKN es un **bottom sheet in-composition** con blur vivo (portal + `kpknGlass`),
**altura proporcional al contenido**, texto suelto **blanco**, y controles
(chips / tabs / inputs / botones) **blancos con label negro**. Nunca `ModalBottomSheet`.
Nunca tabs amarillos/primary. Nunca pozos grises en controles.

---

## 1. API canónica (obligatoria)

| Pieza | Archivo | Uso |
|-------|---------|-----|
| Shell del sheet | `ui/components/KpknSheet.kt` → `KpknSheet` | Única forma de abrir un bottom sheet |
| Tema de contenido | `ui/components/KpknSheetTheme.kt` → `KpknSheetContentTheme` | Se aplica **dentro** de `KpknSheet` (no hace falta envolver a mano) |
| Tokens | `KpknSheetTokens` | Colores / radios / paddings del contenido |
| Chip / tab | `KpknSheetLightChip` | Tabs y chips de selección |
| CTA | `KpknSheetWhiteButton` | Botón primario full-width |
| Inputs | `kpknSheetWhiteFieldColors()` | `OutlinedTextField` / campos rellenos |
| Tonal | `kpknSheetWhiteTonalButtonColors()` | `FilledTonalButton` dentro del sheet |
| FilterChip | `kpknSheetWhiteFilterChipColors()` | `FilterChip` dentro del sheet |
| Portal | `KpknPortal` + `LocalKpknOverlayHost` | Lo usa `KpknSheet` internamente |

```kotlin
// ✅ Receta mínima
if (showSheet) {
    KpknSheet(onDismissRequest = { showSheet = false }) {
        // contenido — NO fillMaxHeight() salvo necesidad justificada de lista acotada
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KpknSheetTokens.ContentPaddingHorizontal)
                .padding(
                    top = KpknSheetTokens.ContentPaddingTop,
                    bottom = KpknSheetTokens.ContentPaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(KpknSheetTokens.SectionGap),
        ) {
            Text("TÍTULO", color = Color.White, fontWeight = FontWeight.Black)
            KpknSheetLightChip(label = "OPCIÓN", selected = true, onClick = { })
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                colors = kpknSheetWhiteFieldColors(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = KpknSheetTokens.ControlLabel,
                ),
            )
            KpknSheetWhiteButton(text = "Guardar", onClick = { /* … */ })
        }
    }
}
```

```kotlin
// ❌ Prohibido
ModalBottomSheet(...) { ... }
AlertDialog(...) // usar KpknAlertDialog
Dialog(...)      // usar KpknGlassDialog
```

---

## 2. Blur y arquitectura (no negociable)

Detalle completo en [`Blur KPKN.md`](./Blur%20KPKN.md). Para sheets, lo crítico es:

1. **`KpknSheet` portalea** al host de `MainActivity` (`LocalKpknOverlayHost`), hermano del
   `hazeSource` raíz → blur vivo.
2. El call site puede vivir **dentro** del NavGraph (anidado en `hazeSource`); el portal lo
   saca. No pases un `hazeState` local de pantalla: `KpknSheet` usa `LocalHazeState`.
3. **Nunca** `ModalBottomSheet` / `Dialog` window: otra ventana Android = blur muerto.
4. Forma: **full-bleed inferior**, solo esquinas **superiores** redondeadas
   (`KpknGlass.SheetCornerRadius`), handle + drag. No card centrado con 4 radios.

```
MainActivity
└── Box
    ├── hazeSource (NavHost / pantallas)     ← contenido que se difumina
    └── KpknOverlayHostContent               ← sheets/dialogs (hermanos ENCIMA)
            └── KpknSheet → kpknGlassOrFallback(LocalHazeState)
```

---

## 3. Altura: proporcional al contenido

### Regla

- El sheet **envuelve** su contenido (`wrapContentHeight`).
- Existe un **tope** (`maxHeightFraction`, default `0.92f`) para que listas largas no
  invadan toda la pantalla de forma agresiva, pero **no fuerza** a rellenar ese tope.
- Contenido corto → sheet corto. Contenido largo → crece hasta el tope y scrollea.

### Parámetros de `KpknSheet`

| Param | Default | Significado |
|-------|---------|-------------|
| `maxHeightFraction` | `0.92f` | Máximo de pantalla que puede ocupar (cap, no fill) |
| `heightFraction` | `null` | Alias legacy: se trata igual que `maxHeightFraction` |
| `dismissible` | `true` | Scrim + back + drag para cerrar |
| `showDragHandle` | `true` | Handle superior |

### Anti-patrones de layout

```kotlin
// ❌ Fuerza el sheet a ocupar todo el cap aunque el contenido sea corto
KpknSheet(...) {
    Column(Modifier.fillMaxHeight()) { ... }
}

// ❌ LazyColumn / weight sin altura acotada en un Column wrap
Column { // wrap
    LazyColumn(Modifier.weight(1f)) { ... } // weight requiere altura fija → 0 o crash
}

// ✅ Contenido corto
Column(Modifier.fillMaxWidth().verticalScroll(...)) { ... }

// ✅ Lista larga: acota la lista, no el sheet entero a fillMaxHeight
LazyColumn(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 480.dp), // o similar, según el sheet
) { ... }
```

**Dismiss con scroll:** `KpknSheet` ya conecta nested-scroll + drag. No reinventes el gesto.

---

## 4. Contraste y color (contrato visual)

Inspirado 1:1 en el Asistente de sesión.

### 4.1 Texto suelto → blanco

Títulos, subtítulos, labels de sección, párrafos, hints fuera de un control:

| Rol | Token / color |
|-----|----------------|
| Título fuerte | `Color.White` / `KpknSheetTokens.TitleStrong` |
| Cuerpo | `KpknSheetTokens.Body` (`Color.White`) |
| Secundario | `KpknSheetTokens.Muted` / `MutedStrong` (blanco con alpha) |
| Eyebrow | `KpknSheetEyebrow` |

`KpknSheetContentTheme` ya pone `LocalContentColor` / `onSurface` en blanco. Aun así,
**no asumas** que Material pintará bien: en títulos importantes usa `color = Color.White`
explícito.

### 4.2 Controles → blanco + texto negro

Todo “espacio de color” interactivo (chip, tab, input, botón, switch track container, etc.):

| Pieza | Fill | Label / valor |
|-------|------|----------------|
| Chip / tab | `ChipIdle` (0.78) / `ChipSelected` (1.0) | Negro |
| Input / TextField | `ControlFill` blanco | Negro (`ControlLabel`) |
| Placeholder | — | `ControlPlaceholder` (negro 0.40) |
| Botón CTA / tonal confirm | Blanco | Negro |
| FilterChip / tonal | Blanco | Negro |
| TextButton dismiss en glass | Transparente | Blanco |

**Trampa de Compose:** nunca pongas `color = White` en `LocalTextStyle` del tema del sheet.
`Text` prioriza `style.color` sobre `LocalContentColor`; si el estilo es blanco, el label
dentro de un botón blanco **desaparece**. El tema usa `LocalContentColor` blanco y
`LocalTextStyle` **sin** color; los botones rellenados fijan label negro explícito
(`KpknAlertConfirmButton` / `kpknSheetWhiteTonalButtonColors()`).

```kotlin
// ✅
KpknSheetLightChip(label = "REGLAS", selected = active == 0, onClick = { ... })
OutlinedTextField(..., colors = kpknSheetWhiteFieldColors())
KpknSheetWhiteButton(text = "Aplicar", onClick = { ... })
KpknAlertConfirmButton(text = "Eliminar", onClick = { ... })
KpknAlertDismissButton(text = "Cancelar", onClick = { ... })

// ❌ Gris / primary / yellow / texto heredado blanco sobre botón blanco
TabRow + Tab(...)                          // indicator primary = “amarillo KPKN”
FilterChip(colors = … primary …)
DarkEditorChip / surfaceVariant.copy(...)  // pozo gris
FilledTonalButton { Text("Eliminar") }     // sin colors → riesgo de label invisible
```

### 4.3 Paneles de agrupación (no son controles)

Agrupar bloques con vidrio sutil, **no** con gris opaco:

- Usar `KpknSheetTokens.Panel` (`White` alpha `0.06`).
- Evitar `DarkEditorSurface` / `DarkEditorChip` / `surfaceVariant` como pozo de control.

### 4.4 Tabs entre “ventanas” del sheet

**Siempre** el patrón del Asistente: fila de `KpknSheetLightChip` (o equivalente blanco/negro).

```kotlin
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    tabs.forEachIndexed { index, title ->
        KpknSheetLightChip(
            label = title.uppercase(),
            selected = selectedTab == index,
            modifier = Modifier.weight(1f),
            onClick = { selectedTab = index },
        )
    }
}
```

**Prohibido** para tabs de sheet: `TabRow` / `ScrollableTabRow` con indicator `primary`
(el amarillo/neon de la marca **no** es chrome de tabs en sheets).

---

## 5. Anatomía recomendada del contenido

Orden típico (de arriba a abajo):

1. **Handle** — lo pone `KpknSheet` (no lo dupliques).
2. **Eyebrow / título** — blanco, bold/black.
3. **Subtítulo** opcional — blanco muted.
4. **Tabs** (si hay) — `KpknSheetLightChip`.
5. **Cuerpo** — secciones con `Panel` opcional; chips/inputs blancos.
6. **CTA** — `KpknSheetWhiteButton` al final de la sección activa.
7. Padding inferior suficiente (`ContentPaddingBottom` / nav bars ya en el shell).

Paddings de referencia (`KpknSheetTokens`):

- Horizontal: `20.dp`
- Top contenido: `14.dp`
- Bottom: `24.dp`
- Gap entre secciones: `16.dp`
- Radio controles: `14.dp`
- Alto chip: `32.dp`

---

## 6. Checklist antes de mergear un sheet

**No declares el sheet listo sin marcar esto.**

### Blur / shell
- [ ] 1. Usa `KpknSheet` (no `ModalBottomSheet` / `Dialog`).
- [ ] 2. No anida otro `kpknGlass` opaco encima del panel del sheet.
- [ ] 3. Forma bottom-sheet (solo top redondeado), no card de 4 esquinas.
- [ ] 4. Verificación visual: se ve blur del fondo al abrir (reinstalar APK).

### Altura
- [ ] 5. No hay `fillMaxHeight()` / `Modifier = Modifier.fillMaxHeight()` en el root del contenido
       salvo lista con `heightIn(max = …)` justificada.
- [ ] 6. Contenido corto → sheet corto en dispositivo (no tapa casi toda la pantalla vacía).
- [ ] 7. Listas usan `heightIn(max = …)` o scroll interno, no `weight(1f)` en Column wrap.

### Contraste
- [ ] 8. Texto suelto blanco; sin labels negros flotando sobre el vidrio.
- [ ] 9. Chips / tabs / inputs / botones blancos con texto negro.
- [ ] 10. Cero tabs `TabRow` primary/amarillo; cero pozos grises (`DarkEditorChip`, etc.) en controles.
- [ ] 11. CTA principal es `KpknSheetWhiteButton` (o botón blanco equivalente).

### UX
- [ ] 12. Drag handle + dismiss por scrim/back funcionan (si `dismissible = true`).
- [ ] 13. Con scroll, el overscroll hacia abajo sigue pudiendo dismissar el sheet.

---

## 7. Anti-patrones ya cometidos (no repetir)

| # | Error | Por qué falla | Qué hacer |
|---|-------|---------------|-----------|
| 1 | `ModalBottomSheet` + `kpknWindowGlass` | Otra ventana → blur muerto; se ve “ventana cuadrada” | `KpknSheet` |
| 2 | Host overlay siempre montado con `fillMaxSize` | Traga todos los toques → sheet “invisible” | Host solo si `entries.isNotEmpty()` |
| 3 | Glass anidado dentro de `hazeSource` | Ley de hermandad | Portal / hermano |
| 4 | `fillMaxHeight()` en contenido corto | Sheet tapa toda la pantalla | Wrap + `heightIn` en listas |
| 5 | `TabRow` primary | Tabs amarillos, no estilo Asistente | `KpknSheetLightChip` |
| 6 | Inputs `0xFF2E2E35` / chips grises | Controles grises ilegibles / fuera de marca sheet | Tokens blancos |
| 7 | Texto negro suelto sobre blur | Ilegible | Texto suelto blanco |
| 8 | `weight(1f)` en Column wrap | Altura 0 o medición rota | `heightIn(max)` |

---

## 8. Relación con otros overlays

| Necesitas | Usa |
|-----------|-----|
| Bottom sheet | `KpknSheet` |
| Diálogo Material (título / actions) | `KpknAlertDialog` |
| Diálogo custom centrado | `KpknGlassDialog` |
| Dropdown / Popup | `kpknWindowGlass` (única excepción opaca documentada) |
| Dock / FAB / header chrome | Hermanos de `hazeSource` + `kpknGlass` (ver Blur KPKN) — **no** son sheets |

Mismos tokens de contraste cuando el contenido del diálogo es “tipo sheet”
(`KpknSheetContentTheme` / campos blancos).

---

## 9. Referencias en código

| Qué | Dónde |
|-----|-------|
| Shell + dismiss + altura | `ui/components/KpknSheet.kt` |
| Tokens / chips / fields / botones | `ui/components/KpknSheetTheme.kt` |
| Portal overlay | `ui/components/KpknOverlayHost.kt` |
| Blur canónico | `ui/components/KpknGlass.kt` + `Blur KPKN.md` |
| Host raíz | `MainActivity.kt` (`LocalHazeState`, `LocalKpknOverlayHost`) |
| Referencia UI | `AssistantSheet.kt` / `AssistantGlassOverlay` |
| Ejemplo tabs + campos | `RulesSheet.kt` (“Reglas y tiempo”) |
| Ejemplo tabs programa | `CreateProgramTemplateSheet.kt` |

---

## 10. Cambios de diseño

Si hay que retocar el look de **todos** los sheets:

1. Tokens / helpers → `KpknSheetTheme.kt`
2. Shell / forma / blur del panel → `KpknSheet.kt` + `KpknGlass.kt`
3. Física Haze / hermandad → `Blur KPKN.md` + `MainActivity` / portal

**No** tunees colores o `blurRadius` en un sheet individual “porque aquí se ve mejor”.
