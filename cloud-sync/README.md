# KPKN account and sync service

This directory contains an executable Firebase service. Its default project is the local-only `demo-kpkn-stability`. No production deployment is part of the local test command.

The callable API uses verified Firebase Auth identity. Production callables require App Check. Firestore rejects client writes: personal mutations are accepted through server transactions with revisions, operation receipts and a changes cursor. Incompatible edits are stored as conflicts without replacing the confirmed entity. Guest import verifies the number and hashes of the incorporated records. An active workout has a device lease.

`deleteAccount` requires recent authentication and explicit confirmation. It marks the account as deleting before revoking sessions or removing files. Interrupted deletion remains blocked and can resume through the scheduled retry. It stops subscription renewals through the Play API. Exported user-controlled copies are outside the app's remote deletion.

Play validation reads the Developer API, checks the obfuscated KPKN account binding, persists a receipt and acknowledges confirmed purchases. RTDN re-reads current Play state instead of trusting the event's order. Per-token refresh leases prevent an older validation response from restoring a revoked state. Media in Storage is private; new uploads require a confirmed, unexpired Pro entitlement. Existing files remain readable and deletable after Pro expires while the account is active.

## Local verification

Install the pinned dependencies with `npm ci` here and in `functions/`. Functions use Node 22; `@firebase/rules-unit-testing` 6.0.0 requires Node 24.12 or later for the integration test client. Start Firebase CLI with Node 22 to preserve the Functions runtime and invoke the test client with Node 24 explicitly. Java 21 or later is required. Set `FUNCTIONS_DISCOVERY_TIMEOUT=60` when cold dependency loading needs longer on this shared Windows machine. Run `npm test` here. The configured loopback ports are 19099 (Auth), 18080 (Firestore), 19199 (Storage) and 15001 (Functions).

Twenty-one integration scenarios passed against these real local emulators on Node 22.23.3. They cover durable receipts and conflicts, ownership rules, verified guest incorporation, persistent active-session ownership with explicit cancellation, immutable private data blobs larger than Firestore's limit, media ownership and expiry, billing receipts, interrupted deletion, callable identity validation, late uploads after deletion, transactional retirement of obsolete operations and authenticated byte-range downloads. Ten pure/fixture cases also passed. The billing scenarios use an injected Play API fixture; no purchase, charge, live RTDN delivery or live acknowledgement has been performed. Evidence lives under `../artifacts/stability-launch/`.

## Deployment inputs and remaining integration

Android now has actual Auth/provider-linking APIs, a fixed-profile Room graph, a transactional outbox, HTTP callable transport bound to a captured identity, data-blob transport, native Billing, server-confirmed entitlement caching and account/conflict screens. Full JVM round24 passed 6,811 tests with zero failures and two real voice replay omissions; it precedes the new erasure journal and resumable native downloads, whose validation is still in progress. Round25 passed 106 directed tests and debug assembly. Full account flows, media transfer and two-device native verification remain open. The Swift envelope and common fixtures are implemented as a separate package; it has not been compiled on this Windows host. The current local integration suite does not include the Pub/Sub emulator or a live Play Console account.

The owner supplied Firebase test project `kpkn-47b03`. A read-only apps query could not authenticate because Firebase CLI has no signed-in session. Configure public application identifiers, provider settings, App Check and least-privilege Play API access before live tests. The live project is never selected by default.

No deployment, paid service activation or Play upload is authorized by the emulator setup. Prepare the concrete configuration and cost controls for owner review before those actions. Monthly/yearly prices remain owner inputs; sales cannot activate until the real Play product, offers and prices are approved.

A fresh local verification on 10 October 2026 passed 22 emulator integration scenarios and 12 pure/fixture cases with Functions/CLI Node 22.23.3 and rules client Node 24.19.0. The prior failed helper attempts are preserved under `../artifacts/stability-verification/`; production Firebase, Play purchases, Pub/Sub delivery and App Check remain unverified. The executable local QA entry point is `../scripts/stability/local_cloud_qa.py`.
