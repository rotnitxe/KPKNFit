---
flags: [nutrition]
---

# Nutrición: porciones LATAM, matching y sheet

**Fecha:** 2026-09-03  
**Estado:** en construcción

## Rutas

- Dominio: `android-native/app/src/main/java/com/example/kpkn/domain/nutrition/`
- Catálogo: `android-native/app/src/main/java/com/example/kpkn/data/food/`
- Assets: `android-native/app/src/main/assets/food_data/`
- UI: `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/components/FoodLoggerDrawer.kt`
- Tests: `android-native/app/src/test/java/com/example/kpkn/`

## Impacto

- Léxico de porciones subjetivas hispanoamericanas (lámina/feta/tajada/cuadrito × cantidad × clase).
- Matching por word-boundary: chocolate ≠ té; galleta ≠ pan; familyFor salta unidades.
- Catálogo curado de snacks LATAM (8–12 por país + caseros).
- Sheet: sin chips vacíos ni “Asumí…”, corrección avanzada discreta, overlay (i).

## Pruebas

- `láminas de queso gouda` ~35–50 g, no 340 g / 1200 kcal.
- `galletas de chocolate` no es bebida; gramos snack.
- Integridad del catálogo branded snacks.
- Relanzar APK en emulador.

## Riesgos

- Feta (lonja AR/UY) vs queso feta: desambiguar por `fetas de` vs `queso feta`.
- `galleta` como alimento, no como unidad de “de chocolate” (barra).
- Substring corto (`te`, `agua`) no debe volver a `contains`.
