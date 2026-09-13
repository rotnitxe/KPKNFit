# Auditoría AUGE/RINGS 2026-09-12 — evidencia

Implementación F0–F6 del plan de fiabilidad y proactividad (Energía, Columna, Axial).

## Tests

`testBaseDebugUnitTest` con filtros `com.example.kpkn.domain.auge.*`, `CatalogAxialSscConsistencyTest`, `AugeRingsLayoutTest`:

- 157 tests, 0 fallos (ver `unit-test-summary.json`).

Contratos nuevos cubiertos: warp espinal continuo, round-trip de τ de sistema, compuerta axial (ring **y** editor), piso espinal solo con axial, cardio en Energía/Columna, migración schema 3, Columna blended, override que ancla, cold-start, tope ±25 % por observación, skip de progresión, bandas únicas, histéresis de avisos, sugerencia axial con ejercicios de la próxima sesión, catálogo sin pares `ssc`/`axial` contradictorios.

## Tabla pinneada F0 (`AugeRingCharacterizationTest`)

Sesión dura = sentadilla 5×5 @140 + banca 4×6 @90. Ligera = banca 3×10 @60. Reloj fijo `2026-09-12T18:00Z`. Tras F1 (decay 95 %).

| t (h) | Energía dura | Columna dura | Pecho V2 | Energía ligera | Pecho ligera |
|------:|-------------:|-------------:|---------:|---------------:|-------------:|
| 0 | 80 | 93 | 72 | 90 | 92 |
| 12 | 92 | 96 | 80 | 96 | 94 |
| 24 | 97 | 99 | 85 | 98 | 96 |
| 48 | 99 | 99 | 97 | 99 | 99 |
| 72 | 99 | 99 | 99 | 99 | 99 |

Frecuencia (última sesión hace 6 h, luego días consecutivos): Energía **1× = 87**, **3× = 86**, **5× = 85**. Con τ 95 % el ring no llega al ~67 que estimaba B2 con e-folding; la acumulación crónica vive en los monitores, no en el % de Energía.

## QA dispositivo

No había emulador en estado `device` al cerrar. No se instaló APK. Capturas pendientes cuando haya emulador.

## Paridad iOS / backend (deuda, fuera de alcance)

El plan no reabre el port. Android es la fuente de verdad tras F0–F6. Hasta que iOS y el backend se alineen, esas superficies **no** deben copiar curvas ni τ de sistema de 2026-08.

**iOS (`ios-native/KPKNFit/`):** `AugeRecoveryEngine`, `AugeViewModel` y `AugeModels` siguen el modelo previo (τ de sistema con e-folding, Columna sin `spinalAxialGate`, sin `RecoveryBands`, sin `AxialLoadMonitor` / `LoadAdvisoryEngine`, sin `schemaVersion` 3). Rings, readiness y finish iOS pueden mostrar % distintos y presses de pecho seguirán drenando “columna” hasta el port.

**Backend (`backend/engines/adaptive_engine.py`, `banister_model.py`, `routers/adaptive.py`):** Banister de 3 sistemas y deltas de calibración no incorporan gate axial, `TAU_K_95` unificado ni monitores ACWR. Cualquier análisis remoto de Energía/Columna queda desfasado respecto a la app.

No portar estas piezas en un hotfix de rings: es un trabajo de paridad explícito.

## Fuera de alcance (decisión del plan)

Cuestionario 24 h, Sleep AUGE, sliders, Health Connect. Paridad iOS/backend: documentada arriba, no implementada.
