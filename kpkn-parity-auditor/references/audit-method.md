# KPKN Parity Audit Method

Use this method to keep parity audits disciplined and comparable across slices.

## Status taxonomy

- `parity`
  - Kotlin covers the audited behavior or user job with no meaningful gap found.
- `partial`
  - Kotlin covers part of the surface, but important states, tasks, or edge cases remain missing.
- `native-adapter`
  - Kotlin uses a platform-specific approach, but preserves the intended outcome.
- `missing`
  - No meaningful Kotlin equivalent exists.
- `behavior-drift`
  - Kotlin has the surface, but behavior or outputs differ in ways that matter.
- `dead-source`
  - PWA source still exists but appears orphaned or no longer product-relevant.
- `obsolete-doc-claim`
  - Repo docs or matrices claim parity that the current code does not support.
- `unknown`
  - The evidence is not yet enough to classify honestly.

Prefer `partial`, `behavior-drift`, or `unknown` over optimistic labeling.

## Audit dimensions

For each slice, compare only the dimensions that actually matter:

- Entry surface
  - route, screen, entry component, public function, repository API
- Task coverage
  - can the user or caller complete the intended job
- Models and defaults
  - fields, enum values, nullability, default thresholds, fallback values
- Derived behavior
  - calculations, filtering, mapping, queueing, draft state, state transitions
- Persistence and recovery
  - save path, resume path, restart behavior, stale draft handling
- Error and edge states
  - loading, empty, unresolved, offline, retry, validation errors
- Integrations
  - AI, permissions, notifications, native bridges, background work
- UX translation
  - the mobile flow may differ, but must still preserve task completion and clarity

## Audit sequence

1. Define the slice.
   - Keep it narrow enough to finish honestly.
2. Find the PWA oracle.
   - Trace from the entry point down to the real logic and persistence.
3. Find the Kotlin landing zone.
   - Trace from screen or route to viewmodel, repository, model, and domain logic.
4. Compare behavior, not just structure.
5. Classify with the status taxonomy.
6. Attach risk.
7. Recommend the smallest next step.

## Risk levels

- `critical`
  - readiness, recovery, fatigue, nutrition persistence, workout save/completion, irreversible data loss
- `high`
  - workflow completion gaps, navigation dead ends, parser result drift, broken drafts
- `medium`
  - important UI states missing, performance-sensitive fallback, degraded but usable flow
- `low`
  - visual tightening, secondary affordances, non-blocking detail gaps

Risk is about product impact, not code size.

## What counts as acceptable native adaptation

Good native adaptation:

- replaces a web drawer with a bottom sheet or full screen, while preserving the task
- replaces hover-only affordances with visible controls
- splits one large desktop screen into clearer mobile steps
- uses Room/DataStore instead of browser persistence, while preserving state meaning

Not acceptable as a native adaptation:

- dropping important editing controls
- removing recovery or parser states because the Android flow is simpler
- hiding a missing route behind a similarly named screen
- omitting save/resume behavior that users relied on

## Report shape

For serious audits, report in this order:

1. Slice and target
2. Status
3. Evidence
4. Key gaps or drifts
5. Intentional native adaptations
6. Risk
7. Recommended next step

## Questions to answer before closing the audit

- What exact PWA behavior did I compare?
- What exact Kotlin surface did I inspect?
- Did I confirm task completion, not just file existence?
- Did I verify defaults, fallbacks, and edge states?
- Am I relying on a stale document instead of live code?
- If I labeled something as native-adapter, can I explain the preserved user outcome?

## When not to refactor during an audit

- when the task is to document current parity only
- when the gap is not yet fully understood
- when a report is more valuable than a speculative fix
- when multiple Kotlin surfaces need classification before implementation

Audit first. Refactor later, unless the user asked for code changes now.
