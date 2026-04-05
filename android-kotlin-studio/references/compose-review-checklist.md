# Compose Review Checklist

Use this reference when editing or reviewing Compose code, especially in mixed Compose + Views repositories.

## State ownership

- Confirm the state owner is clear: screen-level `ViewModel`, presenter, controller, or local remembered state.
- Keep transient UI-only state local unless it must survive process death, navigation, or cross-screen coordination.
- Avoid pushing screen-specific rendering concerns into repositories or domain models.
- Prefer explicit state models over scattered booleans when a screen has loading, content, and error phases.

## Effects and lifecycle

- Keep `LaunchedEffect` keys specific to the side effect trigger.
- Use `rememberUpdatedState` when an effect needs the latest callback or lambda without restarting unnecessarily.
- Use `DisposableEffect` for subscriptions, listeners, or observers with cleanup.
- Prefer lifecycle-aware collection when the repository already uses it.
- Avoid launching long-running work directly from leaf composables.

## Parameters and structure

- Pass only the state and callbacks a composable needs.
- Avoid giant parameter lists by using small state holder models only when they match existing repository style.
- Keep composables focused; split by responsibility rather than by arbitrary line counts.
- Separate stateful and stateless composables when it clarifies ownership or improves testability.

## Recomposition and performance

- Check for expensive work inside composition and move it out when practical.
- Use stable keys in lazy containers when item identity matters.
- Avoid recreating large objects, formatters, or mappers on every recomposition without reason.
- Do not introduce memoization everywhere by habit; use it only where it solves a measured or obvious issue.

## Events and navigation

- Keep navigation decisions near the screen boundary unless the repository has an established deeper pattern.
- Model one-shot events explicitly when the repository already uses event wrappers, channels, or effect streams.
- Avoid firing analytics, repository calls, or navigation from deep leaf composables unless that is the local pattern.

## Previews and IDE ergonomics

- Keep preview sample data local and cheap.
- Avoid preview setups that require real repositories, DI graphs, or network calls.
- Reuse theme wrappers used elsewhere in the repository so previews reflect real styling.
- If the repository values previews, update or add them for meaningful UI changes.

## Mixed Compose + Views notes

- Keep interop localized with `ComposeView`, `AndroidView`, or bridge containers rather than spreading it across the feature.
- Respect the existing navigation and lifecycle owner boundaries when embedding Compose inside Fragments or Views.
- Avoid rewriting an XML screen into Compose just because a subview changed.
