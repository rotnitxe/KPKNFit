---
name: debug-audit
description: Audit KPKN changes for bugs and regressions
---

# Debug Audit

## Checklist

- Compare the diff with the approved plan and the KPKN map.
- Check nullability, lifecycle cancellation, coroutine dispatchers, Room migration safety, and state restoration.
- Check Android/iOS/backend parity for shared domain behavior.
- Check secrets, permissions, generated assets, and documentation freshness.
- Prefer a focused reproducer or test over speculative fixes.

## Report

Report findings by severity with exact file and symbol references. End with `APROBADO` only when the validation evidence supports it.
