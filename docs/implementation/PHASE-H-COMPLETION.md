# Implementation Phase H — Completion Report

## Objective

Determine what can be validated on real hardware; run it if a device exists; otherwise mark BLOCKED with the exact matrix (never fake it).

## Gaps Investigated

G7 (device validation for Phases 1–4 + all integrations).

## Gaps Confirmed

**BLOCKED — no device in this environment.** Debian `platform-tools` ships without `adb`; no USB device, no emulator binary, no cloud device farm credential. Installing Google `platform-tools` would provide `adb` but nothing to connect it to, so no action taken.

## Changes Implemented

None (nothing to run against).

## Required device test matrix (for the first device session)

Preconditions: Android 12+ device, Shizuku (wireless-ADB paired), F-Droid Termux + `termux-api`, Chrome stable, ClosePaw debug APK from CI (`debug-apk` artifact).

| # | Area | Procedure | Pass criteria |
|---|---|---|---|
| H1 | Accessibility capture | Enable service; run demo task "Open Settings" | Pre-turn snapshots non-empty; tree → prompt JSON sane |
| H2 | Shizuku binder | Pair via wireless ADB; start VD session | No fallback-to-A11Y warning; display `closepaw_agent_display` created |
| H3 | VD lifecycle | Run task → end session → follow-up | Display created/operated/destroyed; no leaked displays (`dumpsys display`) |
| H4 | VD input | Tap/type/scroll in VD app | Actions land on VD display, not physical |
| H5 | Chrome CDP | `browser_script` list-tabs + screenshot | Tab targets enumerated; artifact path returned |
| H6 | Wireless self-pair | Fresh pair flow | Pairing completes without manual `adb` |
| H7 | Termux bridge | Setup → exec `echo` → timeout kill | Setup reaches Ready; 120 s kill verified with `sleep 130` |
| H8 | Foreground/background | Background app mid-task; overlay capsule | Capsule/Island/Glow states per `ui/capsule` matrix |
| H9 | Verification on device | Type-then-readback task | VERIFY lines appear; typed-but-unsent fails as designed |
| H10 | Takeover/resume | Takeover mid-turn → Resume | Pause at turn boundary; resume continues same task |
| H11 | Battery/network fields | Drain/ airplane toggles | DeviceState reflects values; UNKNOWN when unreadable |
| H12 | Backup/restore | Backup → reinstall → auto-restore | Sessions return; credentials absent from payload |
| H13 | Uninstall survival | Backup → uninstall → reinstall | App-external `ClosePaw/` root verdict (G5) |

Emulator/host validation stays separate: CI unit tests + `eval/` AndroidWorld runs are NOT device evidence.

## Files Changed

This report only.

## Tests Run

None (blocked). No validation faked.

## Git Commit

`docs(impl-phase-h): device validation blocked with matrix`

## Git Push

Deferred to end of run.

## Status

BLOCKED (environment) — matrix ready for first device session
