FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/memory/MemoryStore.kt
PACKAGE: ai.closepaw.memory

MAIN CLASSES:
- MemoryStore - Persistent markdown-based memory store

DATA CLASSES:
- SaveResult - Success, TooLarge, IoError, InvalidScope
- DocumentSpec - internal: scope, file, title, intro, sections
- MemorySection - headings (FACTS, PREFERENCES, APP_SKILL_OVERRIDES, OPERATIONAL_NOTES)
- MemoryScope - USER, DEVICE, APP
- StoredCredential (internal to AuthStore, referenced here)

COMPANION OBJECT CONSTANTS:
- TAG = "MemoryStore"
- DEFAULT_MAX_CONTENT_LENGTH = 2000
- DEFAULT_MAX_FILE_BYTES = 8192
- APPS_DIR = "apps"
- USER_FILE = "user.md"
- DEVICE_FILE = "device.md"
- SAFE_PACKAGE_PATTERN = Regex("^[a-zA-Z0-9_.]+$")
- TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

PRIVATE METHODS (key functionality):
- read(scope, packageName) - side-effect-free raw file read
- write(scope, packageName, content) - atomic temp-file replace
- delete(scope, packageName) - file deletion
- listAppPackages() - list .md files in apps/ directory
- append(scope, section, content, packageName) - schema-aware insertion
- appendUserFact/Preference/DeviceFact/Verification/SkillOverride/Pref/OperationalNote
- validatePackageName() - package name validation
- buildSpec(scope, packageName) - builds DocumentSpec for scope
- sanitizeContent() - folds whitespace, strips control chars, collapses whitespace, truncates
- formatEntry() - formats with timestamp prefix
- insertEntry() - 5-rule insertion under headings (Rules 4-6)
- buildSkeleton() - creates new file with title, intro, sections
- atomicWrite() - temp file write then rename
- listAppPackages() - lists installed app memory files

SCHEMA-AWARE APPEND (5 rules in insertEntry()):
- Rule 1: heading missing → append at EOF
- Rule 2: single heading → insert before next "## " line or EOF
- Rule 3: duplicate heading (>1) → warning, insert under last occurrence
- Rule 4: skip back over blank lines before heading
- Rule 5: file doesn't exist → build skeleton with entry

SERIALIZATION:
- toJsonObject() - deep copy as JSONObject
- toPromptContext() - builds prompt-visible section with budget tracking (TOTAL_BUDGET=3000, DISPLAY_TRUNCATE_LENGTH=600)
- serializeValue() - strings quoted, others toString()
- formatEntry() - "- [timestamp] content"

MemoryScope handling in buildSpec():
- USER → user.md file with "# User Memory" title
- DEVICE → device.md file with "# Device Memory" title
- APP → "$safeName.md" in apps/ directory with app-specific title

RECALL (MemoryRecaller):
- Recalls USER + DEVICE + APP package memory blocks
- Filters by maxFileBytes cap
- Prepends "## Recalled Memory" section

SIDE EFFECTS:
- File I/O (read/write/delete)
- Atomic file replace (write)
- Emits Log warnings for errors/invalid operations
- onMutation callback invocation

PERSISTENCE:
- Internal Files directory (context.filesDir)/memory/
- Files: user.md, device.md, apps/*.md (per-app memory)
- Markdown format with timestamped entries
- 8KB max file size, 2000 char content limit per entry

NETWORK:
- None - local file-based storage

ANDROID API:
- File I/O operations
- Android context for filesDir acquisition

ERROR HANDLING:
- IOException on read/write → Log warning, return null/failure
- Invalid scope → SaveResult.InvalidScope
- File too large → SaveResult.TooLarge
- Empty content after sanitization → rejected
- Package name validation → reject unsafe names

SECURITY:
- Package name validation via SAFE_PACKAGE_PATTERN (alphanumeric + dots)
- No sensitive data sanitization beyond control char stripping
- Files stored in app's internal storage (not encrypted by default)
- No explicit sensitive data handling (passwords, tokens could end up here)

CRITICAL SECURITY CONCERN:
- MemoryStore can accidentally store passwords, tokens, OTP, private messages, financial information, authentication information
- No redaction or filtering of sensitive content
- Appended entries format as "- [timestamp] content" with no redaction
- User has full control over what is stored via scratchpad/action outputs

TEST COVERAGE:
- MemoryStoreTest.kt
- MemoryRecallerTest.kt
- ModelsTest.kt (indirectly)

STATUS: CONFIRMED IMPLEMENTED
- Full markdown-based persistent memory system
- Schema-aware append with 5 insertion rules
- Scope-based separation (USER, DEVICE, per-app)
- 8KB file size limit, 2000 char content limit per entry
- Atomic writes prevent corruption
- HIGH RISK: Can store sensitive data (passwords, tokens) with no protection
- No encryption, no redaction, no access controls beyond package-scoped files
- Files in internal storage, readable by app if user roots device