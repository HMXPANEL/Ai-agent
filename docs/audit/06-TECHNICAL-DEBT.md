# Technical Debt — 06

## Architecture debt

1. **Two gating mechanisms coexist** (`CapabilityManager` vs `DefaultBrowserScriptCapabilityGate`). Only 3 tools use the new system; browser/CDP uses the old gate. Unify declarations or document the split (P2).
2. **No execution-time capability re-check.** Approval path re-checks package (TOCTOU) but not capability. A capability lost mid-approval (Shizuku death, Termux kill) is not re-checked at execution. Add re-check in `ToolRouter.execute` post-approval (P1).
3. **Dead `OAuthCodexValidator`.** Defined, tested(?), never called; sign-in explicitly skips it. Either wire it or delete it (P2).
4. **`Turn.run()` non-stream role unclear.** Only `Compactor` uses it; loop uses streaming. Document or fold (P2).
5. **`LOCAL_LFM` factory throw by design.** Correct but surprising; document at factory + bootstrapper (P3).
6. **Storage decision drift.** Plan (MediaStore) vs shipped (app-external). Re-verify uninstall survival; update one doc as superseded (P1).

## Code debt

7. `MainActivity.kt:919` `runBlocking { authStore.get(...) }` on calling thread — inspect for main-thread block (P1).
8. `Thread.sleep` on binder/pairing threads (`ShizukuStatusAdapter.kt:30,38`; `ChromeDevtoolsUserService.kt:103-113`) instead of `delay` (P1, low risk).
9. Only 3 TODOs in main source (`AuthStore` NotImplementedError default refresher — by design; wireless retry counter; overlay-permission check). Codebase is clean of TODO sprawl. No `UnsupportedOperationException` in prod paths.
10. `core-ktx` stale pin 1.12.0 vs transitive 1.17.0 (P2). `okhttp` vs `mockwebserver` major skew (P0-verify).
11. Working-tree trash: 15 deleted launcher icons + 5 `.trashed-*` files (`git status`). Restore or remove (P3).

## Documentation debt

12. 6 `doc/main/*` contradictions (§C of `03`) — all small, all fixable by reading source (P1).
13. 4 `docs/audit_*.md` drift points — add BASELINE pointer headers or correct (P3).
14. `doc/main/infra/session.md` stale roles paragraph (P3).
15. Missing specs for capability system, DeviceState, HmxAgent/Planner, ClosePawStorage (see `05`) (P3).

## Test debt

16. `storage/BackupManager` zero direct tests (P0).
17. `protocol/`, `debug/` no test dirs (P2, low risk).
18. No perf benchmark (P4).
19. `MemoryStoreTest.kt` is binary-unreadable via text search (compiled assertion artifact?) — confirm it runs in CI (P2-verify).
