# PHASE 2 — Unified Agent Core + Persistent Device Data: Status

- Objective: repair Phase 2 until CI green (persistent device data + unified agent core).
- Implementation: `ClosePawStorage` singleton (class + companion) rooted at the
  app-external `ClosePaw/` dir (`memory/ sessions/ backups/ settings/ skills/
  diagnostics/ exports/ metadata/`); `BackupManager` (versioned, checksummed,
  gzipped backups, auto-backup on session end); migrated `MemoryStore`,
  `SessionStorage`, `SessionHistoryManager`, `SessionServices`,
  `AgentSession`, `SessionCoordinator`; `hasLegacyUsageEvidence` restored on
  the Phase 2 storage path; main-source compile repairs (imports, Composable
  hoisting, capability wiring call sites); test call-site migration.
- Files changed: storage/ (2 new), session/, app/, history/, llm/ catalog seed,
  onboarding CODEX baseline restore, settings UI.
- Tests: full `:app:testDebugUnitTest` suite green (2256 tests).
- Build result: `:app:assembleDebug` green.
- GitHub Actions result: GREEN (unit-tests + debug-apk).
- Device validation: REQUIRED (uninstall/reinstall survival, real permission
  states) — not validated in CI.
- Known limitations: storage root is app-external (deleted with app data on
  uninstall if the user clears it); MediaStore shared-dir approach unavailable
  via SDK; `packagingOptions` deprecation warning (non-blocking).
- Commits: d224fd6, 5b38e96, e05cafc, b8eed85, 7e07f09, c84daf6, 0be9ba7,
  43232dd, dd9f0b5, 3beb6eb, 35b3483.
- Next phase: Phase 3 (accepted — CI green).
