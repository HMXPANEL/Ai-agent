# Build and Resource Audit — 08

## Build system

- AGP 8.9.1 · Kotlin 2.3.0 · Gradle wrapper 8.11.1 · Java 17 (`source/targetCompatibility`, `jvmTarget JVM_17`).
- `compileSdk 36`, `targetSdk 36`, `minSdk 31`; namespace/applicationId `ai.closepaw`; version 0.1.0 (code 1).
- Compose BOM 2024.12.01 (ui, graphics, tooling-preview, foundation, material3, icons-extended, activity-compose 1.9.3, lucide-cmp 2.2.1).
- Key deps: `openai-java:4.14.0`, `okhttp:4.12.0`, `leap-sdk:0.9.2`, `kotlinx-serialization-json:1.6.3`, `snakeyaml:2.2`, `shizuku api+provider:13.1.5`, `hiddenapibypass:6.1`, `bcpkix/bcprov:1.84`, `eddsa:0.3.0`, `conscrypt-android:2.5.2`, `security-crypto:1.1.0-alpha06`, `coroutines-android:1.7.3`.
- Tests: junit 4.13.2, coroutines-test, mockk 1.13.9 (+android), truth 1.4.2, json 20240303, mockwebserver 5.2.1; compose test; runner AndroidJUnitRunner; `animationsDisabled`, `returnDefaultValues`, `includeAndroidResources`; unit-test heap 2g.
- Release: R8 minify + shrink resources, `proguard-android-optimize.txt` + `proguard-rules.pro` (serialization, Shizuku/AIDL, OpenAI/Jackson, Leap+native, HiddenApiBypass, SnakeYAML, Compose, coroutines, manifest entry points; line tables kept). Signing env-driven (`KEYSTORE_*`); release unsigned locally without env; debug uses debug keystore.
- `preBuild` copies `tools/termux-bridge/closepaw_bridge.py` → `res/raw`. License plugin JSON-only → assets; config-cache-incompatible tasks isolated. `packagingOptions` excludes META-INF noise + BC Java-25 classes (ASM major-69 crash). `networkSecurityConfig`: cleartext only 127.0.0.1, base deny. `allowBackup=true` + `backup_rules.xml` (includes sessions + agent_prefs; excludes auth_store, skills, diagnostics, DBs, cache).
- Gradle flags: `-Xmx4096m` (R8 OOMs at 2g), useAndroidX, nonTransitiveRClass, `suppressUnsupportedCompileSdk=35`, caching+parallel+configuration-cache.

## Flags / conflicts

1. **P0:** `okhttp:4.12.0` vs `mockwebserver:5.2.1` major skew — verify linkage in chat/codex client tests.
2. **P0:** `security-crypto:1.1.0-alpha06` alpha in auth path — track stable.
3. **P1:** `core-ktx` direct 1.12.0 vs Leap-transitive 1.17.0 — stale pin; align or drop version.
4. **P1:** `compileSdk/targetSdk 36` + `suppressUnsupportedCompileSdk=35` — requires current build-tools + API-36 emulator images for eval.
5. **By design:** `minSdk 31` excludes Android <12 (comment notes cloud-only flavor if sub-12 needed). Shizuku/VD/Leap assume modern APIs — consistent.

## Resource / performance

| Op | Cost | Evidence |
|---|---|---|
| Per-turn screenshot (scale+JPEG+disk+Base64) | **Critical** | `VirtualDisplayScreenshotProcessor.toScreenImage:28-34`, `Models.kt:74` Base64, quality 70 default |
| Full a11y-tree snapshot + `toPromptJson` every turn | **Critical** | `TurnObservation.kt:13,44`, `Perceptor.kt:71-95,203-236` |
| LLM context growth + compaction round-trips | **High** | `Compactor.kt:75-108,141-180`, `Turn.kt:107-166` retry loop |
| PixelCopy→ImageReader fallback surfaces in VD | **High** | `VirtualDisplayCaptureCoordinator.kt:4-29`, permanent demote policy |
| Coroutine fan-out (308 hits; 2 outliers) | **Medium** | `MainActivity.kt:919` runBlocking; `Thread.sleep` binder/pairing threads |
| Checkpoint/snapshot JSON + backup GZIP | **Medium** | `SessionStorage.kt:90,203`, `BackupManager.compress:150` (frequency-gated) |
| JPEG capacity alloc (512KB clamp) | **Medium** | `BitmapUtils.kt:44-48` |
| Reactive flows (replay-0/buffer-1) | **Low** | `AgentService.kt:51-69` |
| Bounded retries/backoff | **Low** | Agent ×1, compaction ×3, LLM ×5 no-retry-after-partial |

No profiler benchmark exists (P4). Build requirements: JDK 17, current AGP toolchain, ~4GB Gradle heap, network for Maven Central/Google; device requirements: Android 12+, Accessibility or Shizuku, F-Droid Termux for shell tools, Chrome + Shizuku for browser CDP.
