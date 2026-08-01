---
name: room-migrations
description: Safely change KPKN Room schema and migrations
---

# Room Migrations

## When To Use

Use before changing entities, DAOs, serialized payloads, indices, or database version.

## Procedure

1. Inspect `android-native/app/src/main/java/com/example/kpkn/data/db/KpknDatabase.kt` and the latest exported schema under `android-native/app/schemas/`.
2. Confirm the current version in code, not only in docs.
3. Add the migration in sequence, update entities/DAOs, and preserve old-user data.
4. Run Room/database tests and inspect the generated schema diff.

## Pitfalls

Do not delete or recreate user tables casually. Complex models are JSON payloads, so serialization changes require compatibility review.
