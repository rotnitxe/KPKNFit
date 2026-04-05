# Nutrition AI Translation Rules

## Preserve vs Adapt

### Preserve exactly when possible

- parser output contract
- deterministic fallback availability
- Spanish/Chilean food parsing intent
- food scaling and macro math
- explicit analysis workflow
- review-aware output semantics
- save behavior into `NutritionLog`

### Adapt for Android

- drawer and review layout
- per-item editing affordances
- loading and status feedback
- lifecycle and runtime warmup handling
- manual review UI density

### Defer unless explicitly requested

- full PWA telemetry parity
- resolution-memory systems
- broad food-search pipeline rewrites
- large nutrition-screen redesigns unrelated to the logging/parsing task

## Food Logger Guidance

- Keep the logger fast to type into.
- Run analysis when the user asks for it, not continuously.
- Make the status of each item obvious: ready, estimated, unresolved, review-required.
- Prefer compact native editing controls over replicating web drawer choreography.

## Parser Guidance

- The deterministic parser is the safety net. Protect it.
- Treat local AI as a higher-context estimator that can fail, time out, or be unavailable.
- Preserve or improve merge behavior; do not let AI output erase a food that deterministic parsing found unless you are sure it is covered.
- Keep composite dishes and protected phrases safe.

## Local Runtime Guidance

- Verify the real asset path before changing anything related to model loading.
- Prefer surfacing status and fallback honestly over hiding runtime uncertainty.
- Keep inference bounded by timeouts and thread-safe access.
- Avoid hidden state that makes parser behavior impossible to reason about.

## Persistence Guidance

- Resolve parsed items into `LoggedFood` before saving.
- If the user edits grams, portion, or macros, make sure the saved log reflects those edits rather than stale derived state.
- Treat repository writes as part of feature correctness, not as plumbing you can ignore.

## Mixed/Imperfect Repo Heuristics

- If docs or scripts disagree with `android-native`, trust the code you are editing and note the mismatch.
- If Android is intentionally simpler than the PWA, preserve that simplicity unless the task requires more parity.
- If a small bugfix does not require telemetry or food-memory systems, do not drag them in.
