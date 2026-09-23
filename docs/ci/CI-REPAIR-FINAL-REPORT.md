# ClosePaw CI Repair Final Report

## Starting Commit

`d7851fe` — CI run `35836040647`: unit-tests FAILED (2297 tests, 138 failed), debug-apk SUCCESS.

## Final Commit

`9505294` — CI run `35840213241`: unit-tests SUCCESS, debug-apk SUCCESS.

## GitHub Workflow

`CI` (`.github/workflows/ci.yml`), push-triggered on master. Jobs: `unit-tests`
(`./gradlew :app:testDebugUnitTest`, Temurin JDK 17) and `debug-apk`
(`./gradlew :app:assembleDebug` + `app-debug` artifact upload).

## Unit Test Status

PASS (latest run, commit `9505294`).

## APK Build Status

PASS (both runs; artifact `app-debug` uploaded).

## Other Required Jobs

None (repo has ci/pages/release workflows; only `CI` gates push health; pages/release not triggered here).

## Failures Found

138 failures in run `35836040647`, three groups:
1. 134× `NoClassDefFoundError`/`ClassNotFoundException` at `MockWebServer()` setup in 6 classes (ModelDiscovery, ApiKeyStepState, HttpLlmCredentialValidator, OnboardingViewModel, PermissionStepState, TermuxShellTool).
2. 3× `HmxDeviceStateProviderTest` comparison failures (stale re-read, external-event invalidate, no re-probe within window) — also failing at base `db9f1aa` (run `35210054961`: 2271 tests, 3 failed).
3. 1× `ToolRouterCapabilityTest.tool without requirements...` assertion failure (new test).

## Root Causes

1. **Dependency hybrid (my change, Phase B):** downgrading `mockwebserver 5.2.1→4.12.0` while Leap SDK's `ktor-client-okhttp` forces `okhttp 4.12.0→5.2.1` in every runtime produced a MockWebServer-4.x/okhttp-5.x hybrid; MockWebServer 4.x links okhttp 4.x internals absent from okhttp 5 → NCDFE at construction. The original "skew" was a false alarm: Gradle unifies okhttp to 5.2.1 everywhere, so 5.2.1 was already the consistent pair. Diagnosed via `:app:dependencies --configuration debugUnitTestRuntimeClasspath` (read-only).
2. **Eager probe (pre-existing Phase 4 bug):** `MutableStateFlow(initial ?: read())` probed at construction while `cached` stayed null, so the first `current()` probed again — extra foreground-package read broke the bounded-cache contract the tests pin.
3. **Test routing bug (mine):** `ToolName.from("plain_tool")` → `Unknown` → screen-changing → SMART/CAUTIOUS routes to approval, which never resolves in-test → timeout-Cancelled instead of Success.

## Fixes Applied

1. Reverted `mockwebserver` to `5.2.1` with an explanatory comment (never downgrade while okhttp resolves to 5.x).
2. `AndroidHmxDeviceStateProvider`: single construction read seeds both `cached` and the flow (`initialState`).
3. Capability test uses real `"scratchpad"` category for the requirement-free case, with a comment explaining Unknown-name routing.

## Tests Added/Modified

No new tests this round; 1 test corrected. Prior-round suites (BackupManager ×10, capability ×6, device ×4, memory ×3, perf ×2, onboarding +2) all pass — none appeared in either failure list.

## Local Validation

None executed (operator forbids local test/build runs). Diagnosis used a read-only Gradle dependency query only. Validation gate is CI itself.

## GitHub Actions Validation

Run `35840213241` on `9505294`: `unit-tests` completed/success, `debug-apk` completed/success. Verified via API (run + jobs endpoints).

## APK Artifact

`app-debug` uploaded by the `debug-apk` job (workflow-configured).

## Remaining Issues

None blocking. Advisory warnings in CI log (pre-existing): `packagingOptions` deprecation, EncryptedSharedPreferences/MasterKey deprecation (stable 1.1.0 deprecates the old API surface — migration follow-up candidate), assorted test-naming/opt-in warnings, `BackupManagerTest:93` unnecessary `!!` (cleanup candidate).

## Final Status

PASS
