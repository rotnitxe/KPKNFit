# AUGE Validation Ladder

Use this reference to choose the smallest useful validation for an AUGE task.

## Level 1: Surface and ownership check

Use when:

- scoping a parity audit
- checking whether Kotlin already implements a PWA surface
- reviewing a proposed landing zone

Do:

- run `scripts/compare_auge_surface.py`
- inspect [auge-surface-map.md](auge-surface-map.md)

## Level 2: Targeted behavior review

Use when:

- editing one engine function
- changing thresholds, weights, or helper logic
- fixing one readiness/recovery/fatigue bug

Do:

- compare representative PWA inputs and expected outputs manually
- run `cd android-native && .\gradlew.bat :app:testDebugUnitTest`

## Level 3: Engine + integration confidence

Use when:

- changing multiple engine functions
- touching repository/viewmodel wiring
- changing stored AUGE inputs such as wellbeing, sleep, or feedback persistence

Do:

- Level 2 checks
- `cd android-native && .\gradlew.bat :app:assembleDebug`
- manual walkthrough of the affected Android screen or flow

## Level 4: High-risk parity work

Use when:

- changing global batteries, readiness, systemic fatigue, or per-muscle battery behavior
- changing exercise metadata wiring
- changing any logic that feeds multiple AUGE screens

Do:

- Level 3 checks
- source-side spot checks against the PWA
- explicit note of parity evidence in the summary

## Level 5: Cross-cutting or cutover-sensitive work

Use when:

- changing multiple AUGE subsystems at once
- changing shared volume/muscle normalization behavior
- preparing a feature slice for promotion as the new Kotlin source of truth

Do:

- Level 4 checks
- broaden Android validation as needed
- document remaining known gaps such as missing metadata, adaptive-cache differences, or deferred articular behavior

## Validation principles

- Prefer proof of behavior over proof of compilation.
- If only UI labels changed, do not over-escalate.
- If formulas changed, do not under-validate.
- If the Kotlin target still lacks required metadata or integrations, state the limitation instead of over-claiming parity.
