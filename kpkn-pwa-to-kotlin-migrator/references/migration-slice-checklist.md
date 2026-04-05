# Migration Slice Checklist

Use this reference to plan one migration slice at a time.

## 1. Inventory the source

- Entry component or route
- Supporting services
- stores/hooks/contexts used
- data files or static catalogs
- worker/background behavior
- edge cases, hidden defaults, and variant states

## 2. Define the slice type

- Logic-first
- Workflow-first
- UI-translation
- Integration-first

If a slice spans more than one type, break it into phases rather than doing everything at once.

## 3. Decide what to preserve

- exact formulas and thresholds
- validation logic
- critical user outcomes
- important copy and terminology
- persisted data meaning

## 4. Decide what to adapt

- layout and hierarchy
- navigation structure
- affordances and gestures
- sheets/dialogs/secondary flows
- dense editing surfaces

## 5. Choose landing zones in `android-native`

- `domain/` for business logic and calculations
- `data/repository/` for orchestration, persistence, and IO
- `screens/` for feature entry points and screen-specific components
- `ui/` for shared primitives
- `navigation/` for flow wiring

## 6. Define parity level

- `exact`: outputs and behavior should match the PWA closely
- `equivalent`: same user outcome, native interaction allowed
- `adapted`: intentional native redesign with preserved intent
- `deferred`: not ported in this slice; document explicitly

## 7. Implement in this order when logic exists

1. models
2. domain logic
3. repository/state wiring
4. screen/viewmodel
5. UI polish
6. tests and manual comparison

## 8. Validate the slice

- compile target Android path
- run relevant unit tests
- compare representative inputs/outputs where parity matters
- do a manual UX pass for the migrated screen or flow

## 9. Record intentional deviations

For every meaningful divergence, capture:

- what changed
- why the Android version is better
- whether parity remains exact/equivalent/adapted

## 10. Sign off only when

- the slice behaves correctly
- the Android result feels native
- no web-specific scaffolding leaked into Kotlin/Compose
- validation has real signal, not just a successful compile
