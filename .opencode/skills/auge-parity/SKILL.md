---
name: auge-parity
description: Preserve AUGE behavior across KPKN platforms
---

# AUGE Parity

## When To Use

Use for recovery, fatigue, readiness, TTC, interference, adaptive, or performance changes.

## Procedure

1. Start in `android-native/.../domain/auge/` and identify the pure Kotlin engine and tests.
2. Find the corresponding implementation or parity plan in `ios-native/` and `backend/engines/`.
3. Compare formulas, units, defaults, rounding, recovery profiles, and edge cases.
4. Update focused tests and parity documentation before broad builds.

## Pitfalls

Do not move Android dependencies into `domain/`. Do not change a formula on one platform without recording the intended parity behavior.
