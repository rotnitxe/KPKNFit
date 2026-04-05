# Parity and Cutover

Use this reference when deciding how much parity is required and what evidence is enough to call a migration slice done.

## Parity levels

### Exact parity

Use for:

- AUGE calculations
- volume/recovery/readiness math
- nutrition parsing and macro logic
- schedule generation
- core persistence semantics

Evidence:

- representative input/output comparison
- tests or deterministic spot checks

### Equivalent parity

Use for:

- screen flows with the same task outcome
- editor behavior where interaction changed but results match
- dashboards with reordered layout but preserved insight quality

Evidence:

- task completion parity
- manual walkthrough
- targeted UI/state validation

### Adapted parity

Use for:

- desktop-first layouts redesigned for mobile
- drawers/panels converted into sheets/screens
- interaction patterns changed to fit Android

Evidence:

- rationale for the redesign
- preserved feature intent
- clear Android usability improvement

## KPKN migration priorities

Prefer stronger parity for:

- training/program/session logic
- AUGE and recovery engines
- nutrition and AI parsing logic
- onboarding data capture
- notification and sync semantics

Allow more adaptation for:

- dashboard composition
- visual grouping and information density
- edit flow structure
- advanced controls that were desktop-first in the PWA

## Validation ladder

1. Read source behavior closely.
2. Compile the target slice.
3. Run targeted tests when logic changed.
4. Compare representative scenarios with the PWA when parity matters.
5. Do a manual native UX pass for translated screens.
6. Widen validation only if the slice touches shared logic, persistence, navigation, or platform integration.

## Red flags before cutover

- Kotlin code still contains web mental-model artifacts.
- The feature compiles but feels awkward on a phone.
- Multiple behaviors were changed without being documented as adaptation.
- The migration touched core math but there is no parity evidence.
- UI hides or drops a high-value product capability from the PWA without an explicit decision.

## Good cutover summary

A good migration summary should say:

- source files reviewed
- Android landing zones used
- parity level for the slice
- what was intentionally adapted
- what validation ran
- what remains deferred
