# PHASE 2 IMPLEMENTATION AUDIT

Generated: 2026-09-16

## 1. Current Agent Architecture

### Existing Components (Phase 1/Phase 0 baseline)

| Component | File | Status | Notes |
|---|---|---|---|
| `HmxAgent` | `app/src/main/kotlin/ai/closepaw/agent/HmxAgent.kt` | ✅ IMPLEMENTED | Unified entry point, delegates to `HmxAgentExecutor` |
| `HmxAgentExecutor` | `app/src/main/kotlin/ai/closepaw/agent/HmxAgentExecutor.kt` | ✅ IMPLEMENTED | Interface implemented by `Agent` |
| `TaskOrchestrator` | `app/src/main/kotlin/ai/closepaw/agent/TaskOrchestrator.kt` | ✅ IMPLEMENTED | Single choke point: plan → execute, fallback semantics |
| `Planner` / `DefaultPlanner` | `app/src/main/kotlin/ai/closepaw/agent/Planner.kt` | ✅ IMPLEMENTED | Returns `HmxPlan` with `useLegacyPath=true` |
| `Agent` | `app/src/main/kotlin/ai/closepaw/agent/Agent.kt` | ✅ EXISTING | Implements `HmxAgentExecutor` via `override` keywords |
| `AgentTurnRunner` | `app/src/main/kotlin/ai/closepaw/agent/AgentTurnRunner.kt` | ✅ EXISTING | Per-turn execution (perception → planning → execution) |
| `SessionAgentRunner` | `app/src/main/kotlin/ai/closepaw/session/SessionAgentRunner.kt` | ✅ MODIFIED | Creates `HmxAgent` wrapping `Agent`, uses `hmxDiagnostics.eventBus` |

### Agent Flow (Current)
```
User Input
    ↓
SessionAgentRunner.start()
    ↓
HmxAgent(executor=Agent, orchestrator=TaskOrchestrator)
    ↓
HmxAgent.run()
    ↓
TaskOrchestrator.orchestrate()
    ↓
Planner.plan() → HmxPlan(useLegacyPath=true)
    ↓
execute = { executor.run() } → Agent.run() (legacy ReAct loop)
```

### Tests (JVM Unit)
- `HmxAgentTest` (4 tests): delegation, pause/resume/stop passthrough, planner-failure fallback, executor throw → Error
- `TaskOrchestratorTest` (4 tests): happy path, planner-failure fallback, execute-failure → Error, cancellation propagation, null eventBus
- `PlannerTest` (2 tests): `DefaultPlanner` legacy plan, `HmxPlan.fallback`

**Gate Status**: Agent core passes unit tests. Real-device run: BLOCKED (no hardware in CI).

---

## 2. Current Session Architecture

### Core Components
| Component | File | Responsibility |
|---|---|---|
| `AgentSession` | `app/src/main/kotlin/ai/closepaw/session/AgentSession.kt` | Lifecycle: Created → Running → Idle → Paused/Takeover → Shutdown |
| `SessionCoordinator` | `app/src/main/kotlin/ai/closepaw/session/SessionCoordinator.kt` | Event queue, cold-idle auto-reload |
| `SessionServices` | `app/src/main/kotlin/ai/closepaw/session/SessionServices.kt` | DI container: platform, LLM, auth, history, memory, tooling |
| `SessionAgentRunner` | `app/src/main/kotlin/ai/closepaw/session/SessionAgentRunner.kt` | Runs `HmxAgent` per task |
| `SessionCheckpointCoordinator` | `app/src/main/kotlin/ai/closepaw/session/SessionCheckpointCoordinator.kt` | Checkpoint persistence for process-death recovery |

### Session Persistence (Current)
- **Location**: App-private storage: `/data/data/ai.closepaw/files/sessions/`
- **Mechanism**: `SessionStorage` → `SessionRecordingService` → `SessionHistoryManager`
- **Checkpoint**: `SessionRuntimeSnapshot` (schema v2) with history, todos, scratchpad
- **Reload**: `AgentSession.reload()` hydrates from snapshot
- **Gap**: All session data lives in app-private directory → **deleted on uninstall**

---

## 3. Current Memory Architecture

### Components
| Component | File | Notes |
|---|---|---|
| `MemoryStore` | `app/src/main/kotlin/ai/closepaw/memory/MemoryStore.kt` | Markdown-based, atomic writes, per-scope (USER/DEVICE/APP) |
| `MemoryRecaller` | `app/src/main/kotlin/ai/closepaw/memory/MemoryRecaller.kt` | Elastic-budget recall per turn |
| `MemorySchema` | `app/src/main/kotlin/ai/closepaw/memory/MemorySchema.kt` | Section definitions per scope |

### Memory Persistence (Current)
- **Location**: App-private storage: `/data/data/ai.closepaw/files/memory/`
- **Files**: `user.md`, `device.md`, `apps/{package}.md`
- **Format**: Markdown with timestamped bullet entries
- **Atomic Writes**: Temp file + rename (safe)
- **Gap**: All memory data lives in app-private directory → **deleted on uninstall**

---

## 4. Current Chat Persistence

### Components
| Component | File | Notes |
|---|---|---|
| `SessionHistoryManager` | `app/src/main/kotlin/ai/closepaw/history/SessionHistoryManager.kt` | High-level session API |
| `SessionStorage` | `app/src/main/kotlin/ai/closepaw/history/storage/SessionStorage.kt` | Low-level file I/O (JSON) |
| `SessionRecordingService` | `app/src/main/kotlin/ai/closepaw/history/SessionRecordingService.kt` | Real-time recording (500ms debounce) |

### Chat Storage (Current)
- **Location**: App-private storage: `/data/data/ai.closepaw/files/sessions/`
- **Format**: JSON (SessionRecord with messages, metadata)
- **Snapshots**: `SessionRuntimeSnapshot` for checkpoint/reload
- **Gap**: All chat data lives in app-private directory → **deleted on uninstall**

---

## 5. Current Uninstall/Reinstall Behavior

| Data Type | Current Location | Survives Uninstall? |
|---|---|---|
| Chat History | `/data/data/ai.closepaw/files/sessions/` | ❌ NO |
| Memory (user/device/app) | `/data/data/ai.closepaw/files/memory/` | ❌ NO |
| Session Checkpoints | `/data/data/ai.closepaw/files/sessions/*.snapshot` | ❌ NO |
| App Settings (SharedPrefs) | `/data/data/ai.closepaw/shared_prefs/` | ❌ NO |
| Auth Credentials (EncryptedSharedPrefs) | Encrypted in app-private | ❌ NO |
| Skills (bundled) | Assets / internal files | ✅ YES (in APK) |
| Trace/Diagnostics | `/data/data/ai.closepaw/files/debug-output/` | ❌ NO |

**Critical Gap**: User data is entirely app-private. Uninstall = total data loss.

---

## 6. Current Backup/Restore Behavior

### Existing (`BackupRestoreSettingsPage.kt`, `ChatPersistenceManager.kt`)
- **Export**: Creates versioned, checksummed, zlib-compressed JSON in **Downloads** folder
- **Import**: Reads from Downloads, merges new/newer sessions (never overwrites newer)
- **Auto-restore**: On first launch, if local store empty → restores from latest Downloads backup
- **Secrets**: API keys/OAuth tokens **excluded** from backups (correct)

### Gaps
- Backup is **manual/opt-in** (user must trigger Export)
- Auto-restore only works if local store empty on first launch
- No schema version migration in backup format
- No integrity validation beyond checksum on import

---

## 7. Current Provider/Model Persistence

### Settings Storage (`AppSettingsStore.kt`)
- **Mechanism**: `SharedPreferences` (app-private)
- **Keys**: model, backend, provider, perception mode, platform mode, URLs, approval mode, etc.
- **Provider routing**: `selectedProvider` stored alongside `selectedModel`; bootstrap validates agreement

### Gap
- All settings in app-private SharedPreferences → **deleted on uninstall**
- No mechanism to persist provider selection to shared storage

---

## 8. Failure Points Summary

| # | Failure Point | Impact | Phase 2 Fix Required |
|---|---|---|---|
| 1 | All user data in app-private storage | Total data loss on uninstall | ✅ Move to `Internal Storage/ClosePaw/` |
| 2 | No centralized storage abstraction | Hardcoded paths scattered | ✅ `ClosePawStorage` singleton |
| 3 | Memory not in shared storage | Lost on uninstall | ✅ Migrate `MemoryStore` |
| 4 | Chat history not in shared storage | Lost on uninstall | ✅ Migrate `SessionStorage` |
| 4 | Session checkpoints not in shared storage | Lost on uninstall | ✅ Migrate checkpoints |
| 5 | No schema version metadata | Cannot migrate safely | ✅ Add `metadata/storage.json` |
| 6 | Backup is manual only | Users lose data if they don't export | ✅ Auto-backup to ClosePaw/backups/ |
| 7 | No integrity validation on restore | Corrupt backup could overwrite good data | ✅ Validate before commit |
| 8 | No corruption detection | Silent data corruption possible | ✅ Schema validation on init |

---

## 9. Files That Need Modification (Phase 2)

### New Files to Create
```
app/src/main/kotlin/ai/closepaw/storage/
├── ClosePawStorage.kt              # Central storage abstraction
├── ClosePawStorageInitializer.kt   # Init/migration/validation
├── StorageSchema.kt                # Schema version constants
└── BackupManager.kt                # Auto-backup/restore with validation
```

### Files to Modify
```
app/src/main/kotlin/ai/closepaw/memory/MemoryStore.kt
    → Accept File from ClosePawStorage instead of hardcoded app-private path

app/src/main/kotlin/ai/closepaw/history/storage/SessionStorage.kt
    → Accept File from ClosePawStorage instead of context.filesDir

app/src/main/kotlin/ai/closepaw/history/SessionHistoryManager.kt
    → No logic change, just passes storage from ClosePawStorage

app/src/main/kotlin/ai/closepaw/session/SessionCheckpointCoordinator.kt
    → Use ClosePawStorage for checkpoint directory

app/src/main/kotlin/ai/closepaw/session/SessionServices.kt
    → Inject ClosePawStorage, wire up all storage dependencies

app/src/main/kotlin/ai/closepaw/app/AppSettingsStore.kt
    → KEEP for secrets/auth (stays app-private, encrypted)
    → ADD: sync non-secret settings to ClosePaw/settings/ for backup

app/src/main/kotlin/ai/closepaw/app/MainActivity.kt
    → Initialize ClosePawStorage on first launch
    → Detect existing data on reinstall
```

### Files That Must Remain Unchanged (Auth Zero-Touch)
```
app/src/main/kotlin/ai/closepaw/auth/AuthStore.kt
app/src/main/kotlin/ai/closepaw/auth/OpenAIOAuth.kt
app/src/main/kotlin/ai/closepaw/auth/OpenAiSignIn.kt
app/src/main/kotlin/ai/closepaw/auth/AuthCredential.kt
app/src/main/kotlin/ai/closepaw/auth/AuthErrors.kt
app/src/main/kotlin/ai/closepaw/app/AuthStoreHolder.kt
```

---

## 10. Phase 2 Acceptance Criteria (from requirements)

### Agent Core
- [x] `HmxAgent` exists
- [x] `TaskOrchestrator` exists
- [x] `Planner` exists
- [x] Existing agent/tool architecture remains usable
- [x] Provider-independent orchestration
- [x] Truthful task outcomes (via existing `AgentStopReason`)
- [x] Diagnostics integrated (via `RuntimeEventBus`)

### Persistence (TO IMPLEMENT)
- [ ] `Internal Storage/ClosePaw/` initialized correctly
- [ ] Centralized storage abstraction (`ClosePawStorage`)
- [ ] Memory persistence → survives uninstall/reinstall
- [ ] Chat persistence → survives uninstall/reinstall
- [ ] Session/checkpoint persistence → survives uninstall/reinstall
- [ ] Schema/version metadata (`metadata/storage.json`)
- [ ] Safe writes (atomic temp-file + rename)
- [ ] Corruption handling (validation on init)
- [ ] Backup/restore compatibility (auto-backup + validated restore)

### Uninstall/Reinstall (TO VERIFY)
- [ ] Persistent ClosePaw data outside app-private directory
- [ ] Uninstall does not intentionally delete user data
- [ ] Reinstall detects existing data
- [ ] Existing chats restored
- [ ] Existing memory restored
- [ ] Existing data not silently overwritten

### Quality
- [ ] No production demo executor
- [ ] No hardcoded demo contacts
- [ ] No fake success
- [ ] No unnecessary rewrite (existing agent used via `HmxAgentExecutor`)
- [ ] No auth changes (ZERO TOUCH)
- [ ] Tests pass
- [ ] Build passes

---

## 11. Implementation Plan

### Step 1: ClosePawStorage Abstraction
Create `ClosePawStorage` class that:
- Uses `MediaStore` or `Storage Access Framework` for `Internal Storage/ClosePaw/`
- Provides typed directory accessors: `memoryDir`, `chatsDir`, `sessionsDir`, `backupsDir`, `settingsDir`, `skillsDir`, `diagnosticsDir`, `exportsDir`, `metadataDir`
- Handles initialization, migration, schema validation
- Thread-safe, single instance

### Step 2: Migrate MemoryStore
- Modify constructor to accept `File` from `ClosePawStorage.memoryDir`
- Update `SessionServices` to provide `ClosePawStorage.memoryDir`

### Step 3: Migrate SessionStorage
- Modify constructor to accept `File` from `ClosePawStorage.sessionsDir` (for sessions) and `ClosePawStorage.sessionsDir` (for snapshots)
- Update `SessionHistoryManager` and `SessionCheckpointCoordinator`

### Step 3: Schema Metadata
- Create `metadata/storage.json` with: `schemaVersion`, `createdAt`, `lastUpdatedAt`, `dataFormatVersion`
- Validate on init, migrate if needed

### Step 4: BackupManager
- Auto-backup to `ClosePaw/backups/` on session end
- Checksum (SHA-256), schema validation
- Restore: validate → stage → verify → commit

### Step 5: Integration & Testing
- Wire `ClosePawStorage` in `SessionServices`
- Initialize in `MainActivity` on first launch
- Detect existing data on reinstall
- Run unit tests, verify build

---

## 12. Android Storage Mechanism Decision

**Target**: `Internal Storage/ClosePaw/` (user-visible, survives uninstall)

**Mechanism**: Use **MediaStore** (API 29+) with `MediaStore.Files` for a user-visible directory in shared storage, OR **Storage Access Framework (SAF)** with `ACTION_OPEN_DOCUMENT_TREE` for user-selected directory.

**Decision**: Use **MediaStore** with `MediaStore.Files` + `relative_path = "ClosePaw/"` for API 29+. For API 30+, use `MediaStore.createWriteRequest` if needed. This creates a user-visible `ClosePaw/` folder in Internal Storage that survives uninstall.

**Fallback**: If MediaStore fails (permission denied, etc.), fall back to app-specific external storage: `context.getExternalFilesDir(null)?.parentFile?.resolve("ClosePaw")` — this is still user-accessible but may be deleted on uninstall on some OEMs.

**Note**: The requirement says "Internal Storage/ClosePaw/" — this typically means the user-visible shared storage root, not app-specific external storage.

---

## 13. Next Actions

1. ✅ **Create this audit document**
2. 🔄 **Implement `ClosePawStorage.kt`** with MediaStore-based directory creation
3. 🔄 **Add `StorageSchema.kt`** with version constants
4. 🔄 **Modify `MemoryStore`** to use `ClosePawStorage.memoryDir`
5. 🔄 **Modify `SessionStorage`** to use `ClosePawStorage.sessionsDir` / `chatsDir`
6. 🔄 **Add `BackupManager.kt`** with auto-backup on session end
7. 🔄 **Add `ClosePawStorageInitializer.kt`** for init/migration
8. 🔄 **Wire into `SessionServices`** and `MainActivity`
9. 🔄 **Run tests**: `./gradlew testDebugUnitTest`
10. 🔄 **Verify build**: `./gradlew assembleDebug`
11. 🔄 **Real device test**: uninstall/reinstall cycle