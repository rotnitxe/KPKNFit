# Program management baseline

Baseline captured on 2026-07-26 before the overlay and execution-scope
migrations.

## Persistence

- Room database: `KpknDatabase`, schema version 20.
- Historical migrations: explicit chain from 1 through 20.
- Exported schema: `android-native/app/schemas/com.example.kpkn.data.db.KpknDatabase/20.json`.
- Production uses the explicit migration chain. Destructive migration fallback
  is not enabled.
- Program payloads are stored in `ProgramEntity.data` as Kotlin Serialization
  JSON with `ignoreUnknownKeys = true` and `encodeDefaults = true`.
- `Program` does not yet have its own serialized schema-version field. Domain
  compatibility is currently handled by nullable/defaulted fields plus
  `ProgramMigrationEngine`.

## Baseline gates

- `:app:compileBaseDebugKotlin` and `:app:compileHealthDebugKotlin` pass with
  `--rerun-tasks`.
- Full Base unit suite passes: 614 tests after the hierarchy tests were added.
- The former nine failures (eight nutrition and one session-editor catalog)
  were caused by repository scopes writing through Room connections closed by
  another Robolectric class. Test repositories now own isolated in-memory
  databases, cancel and join their scopes, and close those databases in
  teardown.
- The critical pair passed ten consecutive executions.

## Delivery hygiene

The current worktree already contained a broad, unfinished program-management
change before this baseline. Central new sources and tests are intentionally
kept as part of the deliverable; unrelated root-level emulator screenshots and
UI XML dumps are not part of program management and must not be staged with the
release.

## Remaining migration work

- Add explicit execution scopes and calendar-overlay entities.
- Add versioned, idempotent migrations and fixtures from relevant historical
  Room/Program JSON versions.
- Quarantine undecodable Program JSON instead of retrying it on every launch.
- Remove legacy ambiguity between `startDay`, `weekStartDay`, and the future
  `splitStartDay`.
