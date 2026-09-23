# Persistence Layer

> SessionHistoryManager, SessionRecordingService, and SessionStorage.
> -> See: [overview](overview.md) for architecture.

## SessionHistoryManager

> See: `history/SessionHistoryManager.kt`

High-level API coordinating between `SessionStorage` and `SessionRecordingService`.

Key operations: `listSessions()`, `loadSession(sessionId)`, `deleteSession(sessionId)`, `startNewSession()`, `resumeSession()`, `endSession()`.

- **Session info caching**: `ConcurrentHashMap<String, CachedSessionInfo>` with file modification time checks — re-read only when file updated. Protected by `Mutex`.
- **Session lookup**: `extractSessionIdFromFileName()` for exact matching (avoids substring collisions with UUID-based IDs).
- **Active session tracking**: Two `SessionRecordingService` instances exist — one in `SessionHistoryManager` (sidebar listing) and a per-session one in `SessionServices` (records events). `externalActiveSessionId` bridges this gap. Set by `MainActivity` at session lifecycle boundaries.

## SessionRecordingService

> See: `history/SessionRecordingService.kt`

Real-time bridge between `AgentEvent` stream and persisted `SessionRecord`.

Key operations: `initializeNewSession()`, `resumeSession()`, `recordUserMessage()`, `startAgentMessage()`, `appendTextDelta()`, `recordAction()`, `updateActionState()`, `recordScreenState()`, `completeAgentMessage()`, `completeSession()`.

Key behaviors:
- **Debounced saves**: 500ms delay (`SAVE_DEBOUNCE_MS`) to avoid excessive I/O
- **Agent message buffering**: `AgentMessageBuffer` accumulates streaming text + interleaved actions
- **Immediate save** on session completion
- **Screen state recording**: Normalizes paths, captures `traceRunId` for replay/debug artifact correlation

## AgentMessageBuffer

> See: `history/AgentMessageBuffer.kt`

Buffers a streaming agent message with interleaved text and action blocks. `appendText(delta)` accumulates in `StringBuilder`; `recordAction(action)` finalizes current text block and adds action block. `buildPartialSnapshot()` for incremental saves; `finalizeSnapshot()` for final output.

## SessionRecordMessageMerger

> See: `history/SessionRecordMessageMerger.kt`

`mergeAgentSnapshot()` updates or inserts an agent message snapshot into a `SessionRecord`, returning a new record with updated `lastUpdated`.

## SessionStorage

> See: `history/storage/SessionStorage.kt`

Low-level file I/O. Key operations: `writeSession()`, `readSession()`, `writeSnapshot()`, `readSnapshot()`, `listSessionFiles()`, `deleteSession()`, `deleteSessionPair()`.

- **Storage location**: `/data/data/{package}/files/sessions/`
- **Session files**: `session-{yyyy-MM-ddTHH-mm-ss}-{uuid}.json`
- **Context files**: `context-{yyyy-MM-ddTHH-mm-ss}-{uuid}.json` (checkpoint snapshots)
- **JSON config**: pretty print, ignore unknown keys, encode defaults
- All I/O on `Dispatchers.IO`

## ClosePawStorage + BackupManager (Phase 2)

→ See: `storage/ClosePawStorage.kt`, `storage/BackupManager.kt`

`ClosePawStorage` singleton roots user data at the app-external `ClosePaw/` directory
(`memory/`, `sessions/`, `backups/`, `settings/`, `skills/`, `diagnostics/`, `exports/`,
`metadata/`; schema v2, format `1.0.0`). Note: the original plan called for a MediaStore
user-visible directory, but `PHASE_2_STATUS.md` records it as unavailable via SDK, so the
app-external root shipped instead — **uninstall survival is weakened and must be
re-verified on device** (queued P1).

`BackupManager.createBackup()` exports session records + non-secret prefs as versioned,
checksummed, gzipped JSON (`closepaw_backup_<ts>.json.gz` + `.meta.json` with SHA-256);
`restoreBackup()` requires the sidecar, verifies checksum, adopts only new/strictly-newer
records via `ChatBackup.verify/reconcile`, stages temp + atomic rename with rollback on
failure; `restoreIfEmpty()` auto-restores after reinstall when the store is empty; daily
auto-backup is triggered best-effort on session end. Credentials are **deliberately
excluded** (`AuthStore` never enters a backup).

> **Status: COVERED (2026-09-24).** `app/src/test/.../storage/BackupManagerTest.kt` covers
> create/list/restore, checksum mismatch, missing/malformed metadata, corrupted archive,
> deleted data file, auto-backup cadence, and credential exclusion. Restore failures are
> values (`RestoreReport(success=false)`), never throws.
