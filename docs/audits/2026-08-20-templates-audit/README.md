# Auditoría formal de plantillas / splits / protocolos — 2026-08-20

Rúbrica de experto (Fase A): descansos por objetivo (fuerza ≥180s compuestos pesados, hipertrofia 60–120s); orden compuesto→aislamiento; sin volumen basura (>25 series directas/semana/músculo); deload = ↓volumen y ↓intensidad; plantillas PL con main lift del día; prohibido squat pesado + deadlift pesado mismo día salvo AVANZADO/PEAK.

## Resumen ejecutivo

| Área | Estado | Notas |
|---|---|---|
| Session templates (`SESSION_TEMPLATES_SYSTEM`) | Gates ejecutables | `SessionTemplateAuditTest` + `SessionTemplateQualityRules` P0+P1; backlog P1 fijado por baseline v4 (P0==0 && P1==0 para publicado) |
| Splits (`SPLIT_TEMPLATES`) | Publicación fail-closed | Pattern 7d y `suggestWeek`; niches sin receta exacta permanecen ocultos y no se sugieren |
| Protocolos (`PROTOCOL_LIBRARY`) | Definiciones auditadas, publicación fail-closed | Texas/Smolov/Sheiko/Bulgarian/Westside y demás terceros siguen ocultos; sólo `kpkn-native-sbd-4` publica con recetas S/B/D explícitas, descansos ≥180s y fases Base/Intensificación/Peak/Taper |
| Hueco estructural (progresión semanal) | Implementado | `BlockProgressionEngine` + `BlockTransitionEngine` materializan y validan bloques/mesos/semanas |

## Hallazgos P0 (ridículo / erróneo)

| ID | Severidad | Hallazgo | Acción Fase B |
|---|---|---|---|
| `westside-base` | P0 | Bloque único "Rotación ME/DE" 50–100% demasiado grueso; DE real ≈50–65% y ME ≈90%+ | Separar bloques ME y DE con rangos propios |
| `ppl-hypertrophy` bloque 3 | P0 conceptual | "Especialización / Pico 78–88%" no tiene sentido en hipertrofia pura | Renombrar a densidad/metabolitos (Acumulación/Intensificación), no "Pico" de intensidad |
| Inventario vacío iOS | P0 paridad | `SessionTemplates.swift = []` | Fase D (port auditado) |

## Hallazgos P1 (mejorable)

| ID | Severidad | Hallazgo | Acción Fase B |
|---|---|---|---|
| `smolov_base` | P1 | Especialización brutal ofrecida como split "general" | Reforzar descripción disuasoria + cons; ya tiene `ALTA_TOLERANCIA`+`POWERLIFTING` |
| `bulgarian_lite` | P1 | Método búlgaro es autoregulado (máx del día); % fijos contradicen | Descripción + cons de autorregulación; fuera de sugerencias default vía tags |
| `phul-base` / `phat-base` | P1 | Catalogados como protocolos cuando son splits con 4 bloques artificiales | Tags `split-like` + descripción de duplicidad con UL/PPL |
| `candito-6week` Test/Taper | P1 | `intensityMax=100` en bloque descarga diluye la señal de taper | Ajustar max a ≤70 en descarga |
| Plantillas sesión (mitad inferior archivo) | P1 | Inventario formal vía tests; P1 de calidad se listan por `SessionTemplateQualityRules` | Fixes data-only si P0 en tests |

## Hallazgos P2 (naming / etiquetas)

| ID | Nota |
|---|---|
| `smolov_base` nombre "Alta Frecuencia Base" | Preferible dejar claro "Smolov" en nombre/descripción |
| PHUL/PHAT vs splits `ul_x4` / `ppl_arnold` | Documentar en UI como "protocolo de mesociclo sobre split", no como split distinto |
| `tab=` muerto / sub-tab `SPLIT` huérfano | Deuda documentada; **no tocar** en este plan |

## Tabla de cobertura de tests

| Test | Qué garantiza |
|---|---|
| `SessionTemplateAuditTest` | IDs únicos, sin volumen absurdo / RPE fuera de rango, cero P0 quality, rúbrica descanso compuestos fuerza |
| `SplitAuditTest` | Pattern 7 días, sin ≥3 días consecutivos mismo bucket, `suggestWeek` sin huecos (whitelist niches), cons en splits de alta tolerancia |
| `ProtocolAuditTest` | `intensityMin < max`, `volumeModifier ∈ [0.25,1.6]`, `defaultSplit` existe, tendencia a pico+deload (whitelist), lifts → configurationId v2 |

## Volumen semanal vs MEV/MAV/MRV

Los rangos objetivo viven en `SessionTemplateCatalogPolicy.WEEKLY_VOLUME_RANGES` y en `Program.volumeRecommendations` por programa. La auditoría estructural de plantillas usa `SessionTemplateAudit.primaryMuscleSets` (tope 20 series primarias/sesión) y `SessionTemplateQualityRules` (cap de volumen directo). Un informe fila-a-fila de las ~100+ plantillas se obtiene ejecutando los tests; no se duplica aquí para evitar drift.

## Deuda explícita (no Fase B)

- Progresión semanal / transición de bloque → **Fase C** (`BlockProgressionEngine`, `BlockTransitionEngine`) — **implementado y cubierto por tests**.
- Paridad iOS SessionTemplates completo → **Fase D** (stub + `TEMPLATE_CATALOG_REVISION`; export masivo queda como deuda documentada en `parity-matrix.md`).
- Backend campos de bloque → **Fase D** — **implementado** (`common.py`).
- Navegación: parámetro `tab=` muerto y sub-tab `SPLIT` huérfano — **no tocados** (deuda documentada).
