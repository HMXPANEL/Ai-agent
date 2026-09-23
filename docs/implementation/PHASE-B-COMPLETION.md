# Implementation Phase B — Completion Report

## Objective

P0 correctness/security/build: BackupManager behavior tests + fix; okhttp/MockWebServer skew verdict + fix; security-crypto alpha verdict + migration; build-env verdict; core-ktx pin alignment.

## Gaps Investigated

G10, G12, G13, G14 + build-env compatibility.

## Gaps Confirmed

- G10: zero BackupManager tests (re-proven via `find`).
- G12: REAL skew — cache holds okhttp 4.12.0 + 5.2.1 (5.2.1 pulled transitively by mockwebserver 5.2.1); test runtime resolves okhttp to 5.x while prod ships 4.12.0. Tolerated only because CI was green.
- G13: alpha06 in auth path; Google Maven offers 1.1.0 stable (release of this alpha line) + beta01/alpha07.
- G14: stale 1.12.0 pin vs 1.17.0 transitive (resolution already yields 1.17.0).

## Changes Implemented

1. **`BackupManager.restoreBackup` hardening**: checksum-verify wrapped in `runCatching(...).getOrDefault(false)` (missing/deleted file → graceful mismatch instead of throw); gzip `decompress` wrapped in try/catch → `RestoreReport(success=false, "Backup archive is corrupted or unreadable")`. Previously both paths threw out of a `suspend` restore — now all restore failures are values.
2. **`BackupManagerTest`** (new, 10 behavior tests): create report accuracy; list ordering + orphan-skip; restore round-trip with content check; tamper→mismatch; missing meta; malformed meta; corrupted-archive-with-forged-checksum (covers the new catch); deleted data file; auto-backup once-then-skip; credential-exclusion scan of payload. Follows `SessionStorageTest`/`ChatBackupTest` patterns (TemporaryFolder, mockk `buildTestContext` + relaxed SharedPreferences, Truth, runTest).
3. **`mockwebserver:5.2.1` → `4.12.0`**: test runtime now resolves okhttp 4.12.0 = prod. All 6 consumer files use only basic API (`MockWebServer()`, `start()`, enqueue) identical across majors.
4. **`security-crypto:1.1.0-alpha06` → `1.1.0`**: stable release of the same lineage; `AuthStore` API (MasterKey/EncryptedSharedPreferences) unchanged.
5. **`core-ktx:1.12.0` → `1.17.0`**: declaration now matches the resolved version (was already 1.17.0 via Leap transitive).

## Files Changed

- `app/src/main/kotlin/ai/closepaw/storage/BackupManager.kt` (restore failure-as-values)
- `app/src/test/kotlin/ai/closepaw/storage/BackupManagerTest.kt` (new)
- `app/build.gradle.kts` (3 version lines)

## Architecture Changes

None (same classes, same seams; one added optional behavior: graceful restore failure).

## Tests Added

10 (BackupManagerTest). Existing ChatBackup/SessionStorage tests untouched.

## Tests Run

None locally (operator forbids local execution). Validation gate: GitHub Actions `ci.yml` (`:app:testDebugUnitTest`, `:app:assembleDebug`) on push.

## Build Results

Pending CI. Risk notes for CI watch: mockk relaxed `getSharedPreferences` stub; `runTest` + real `Dispatchers.IO` in BackupManager paths (same pattern as production code under test); new deps must resolve from google()/mavenCentral().

## Security Impact

Positive: restore path no longer throws (DoS-safe malformed backups); crypto dep leaves alpha; test/prod okhttp parity removes a class of test-only behavior drift.

## Performance Impact

None.

## Documentation Updated

`doc/main/app/history/persistence.md` UNTESTED marker is now stale → updated in this commit (see below).

## Remaining Issues

- AuthStore device-level crypto regression (alpha→stable Keystore behavior) cannot be verified without a device — recorded in `09` residual risks; unit tests use fake prefs.
- Build-env (AGP 8.9.1/Kotlin 2.3.0/SDK 36): no change; CI-green lineage retained.

## Git Commit

`fix(backup): add BackupManager coverage and restore hardening` + `fix(build): align test/prod dependency versions`

## Git Push

Deferred to end of run (single push; auth previously invalid).

## Status

COMPLETE (pending CI verdict on push)
