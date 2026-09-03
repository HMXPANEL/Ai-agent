FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/termux/TermuxBridgeManager.kt
PACKAGE: ai.closepaw.termux

MAIN CLASSES:
- TermuxBridgeManager - Manages Termux bridge connection and capabilities

INTERNAL CLASS:
- TermuxBridgeManagerSessionBridge - implements TermuxSessionBridge interface

DATA FIELDS (from status and dependencies):
- healthCheck(): TermuxBridgeStatus
- ensureReadyForSession(timeoutMs: Long): TermuxBridgeStatus
- snapshot(enabled: Boolean): TermuxCapabilitySnapshot

RELEVANT FILES (from earlier exploration):
- TermuxBridgeStatus.kt - bridge status
- TermuxBridgeDependencies.kt - dependency info
- TermuxRunCommandAdapter.kt - command execution adapter
- inspection_tool/replay_v2 - trace replay

RESPONSIBILITY:
- Manages Termux bridge daemon connection
- Health check and readiness probing
- Capability snapshot for available Termux features
- Ensures bridge is ready before session operations
- Handles Termux unavailable/uninitialized state

STATE OWNERSHIP:
- Bridge connection state (available/unavailable)
- Capability snapshot (what Termux features are available)

DEPENDENCIES:
- AppSettingsStore (for termuxShellEnabled preference)
- Session config for perception
- Bridge daemon on localhost:18422

THREAD/COROUTINE:
- healthCheck(): suspend fun
- ensureReadyForSession(): suspend fun with timeout
- snapshot(): suspend fun
- All on coroutine scope context

INPUTS:
- enabled: Boolean - whether Termux shell is enabled in settings

OUTPUTS:
- TermuxBridgeStatus - availability status
- TermuxCapabilitySnapshot - available capabilities

PERSISTENCE:
- None - bridge state is runtime

NETWORK:
- HTTP to localhost:18422 (bridge daemon)
- Bridge commands and responses

ANDROID API:
- None directly; manages bridge process

ERROR HANDLING:
- Health check failure → TermuxBridgeStatus unavailable
- ensureReadyForSession timeout → reports unavailable
- snapshot when unavailable → TermuxCapabilitySnapshot.Unavailable

CRITICAL FLOW:
1. SessionServices.create() → captureTermuxSnapshot()
2. → bridge.ensureReadyForSession(timeoutMs) with 2s timeout
3. → bridge.snapshot(termuxShellEnabled)
4. → TermuxCapabilitySnapshot (Available or Unavailable)
5. → TermuxShellTool sees snapshot and either exposes or hides the tool

STATUS: CONFIRMED IMPLEMENTED
- Termux bridge management and health probing
- Capability snapshot for feature gating
- 2-second timeout for health check
- Graceful degradation when Termux unavailable
- Integrated into SessionServices creation flow