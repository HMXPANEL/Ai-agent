FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/auth/AuthStore.kt
PACKAGE: ai.closepaw.auth

MAIN CLASSES:
- AuthStore - Unified credential store for all cloud LLM providers

CONSTRUCTION PARAMETERS:
- context: Context - application context
- refresher: suspend (refreshToken: String) -> AuthCredential.OAuth - token refresh function
- nowMs: () -> Long = System.currentTimeMillis - timestamp supplier
- prefsProvider: (Context) -> SharedPreferences = defaultEncryptedPrefs - preferences provider

COMPANION OBJECT:
- TAG = "AuthStore"
- PREFS_NAME = "auth_store"
- REFRESH_BUFFER_MS = 5 * 60 * 1000L (5 min buffer)
- defaultEncryptedPrefs(context) - creates EncryptedSharedPreferences with MasterKey

DATA CLASSES:
- StoredCredential (internal, serialized to/from JSON)
- AuthCredential -.ApiKey or .OAuth
- AuthCredential.OAuth has: accessToken, refreshToken, expiresAt, email, idToken

KEY INTERNAL TYPES:
- StoredCredential.type: "api_key" or "oauth"
- StoredCredential.toDomain() - converts to AuthCredential
- AuthCredential.toStored() - converts to StoredCredential

CORE METHODS:
- get(provider: LLMProvider): AuthCredential? - read from preferences
- set(provider, cred: AuthCredential) - write + bump generation
- clear(provider) - clear credentials + bump generation
- has(provider): Boolean - check if credential exists
- generation(provider): Long - generation counter for invalidation
- requireApiKey(provider): String - get API key or throw MissingCredential
- codexHeaders(provider: LLMProvider): CodexHeaders - get auth headers (with auto-refresh)
- has(provider): Boolean - credential existence check

AUTO-REFRESH LOGIC (codexHeaders):
- Fast path: if token fresh (not within REFRESH_BUFFER_MS), return cached headers
- Lock step: refreshMutex ensures serialized refresh under concurrent access
- Refresh cycle: read → check expiry → call refresher network → if generation unchanged, write new credentials
- Concurrent safety: if generation changed during refresh, yield to caller's write

CREDENTIAL PARSING:
- read(key): reads JSON string from prefs, decodes using kotlinx.serialization Json
- write(key, cred): encodes cred to StoredCredential JSON, writes to prefs
- Handles null cred → remove key, null cred → just write

CREDENTIAL TYPES:
- AuthCredential.ApiKey(key) - simple API key string
- AuthCredential.OAuth(accessToken, refreshToken, expiresAt, email, idToken) - full OAuth

ERROR CONDITIONS:
- MissingCredential - provider has no stored credential
- WrongCredentialType - wrong type requested (e.g. asking for ApiKey when OAuth stored)
- OAuthRefreshFailed - refresher network call failed

PERSISTENCE:
- EncryptedSharedPreferences with MasterKey (AES256_GCM)
- Key hierarchy: PREFS_NAME ("auth_store") → per-provider keys → StoredCredential JSON
- MasterKey from MasterKey.Builder with AES256_GCM scheme
- Values encrypted with AES256_GCM SIV for keys, AES256_GCM for values
- On prefsProvider failure → exception bubbles up (no silent fallback)

NETWORK:
- Indirect: refresh network calls via injected refresher function
- No direct network operations in AuthStore itself

ANDROID API:
- EncryptedSharedPreferences, MasterKey, SharedPreferences
- Context for accessing files/Datastore

ERROR HANDLING:
- prefsProvider failure → exception bubbles (no silent fallback)
- JSON decode failure → Log warning, return null
- Credential type mismatches → typed errors (MissingCredential, WrongCredentialType)
- Refresh failures → OAuthRefreshFailed wrapped in exception

SECURITY:
- Uses Android Keystore via MasterKey for encryption key protection
- EncryptedSharedPreferences with AES256_GCM SIV + AES256_GCM
- **IMPORTANT**: If Keystore is broken → exception bubbles, no memory-only fallback
- Generation counter prevents stale reads during concurrent refresh
- Refresh mutex serializes concurrent token refreshes
- **RISK**: Credential content (access tokens, refresh tokens) stored encrypted but
  the encryption protects at rest only; the tokens are decrypted in memory when used
- parseChatgptAccountId extracts account ID from JWT without signature verification
- over-reliance on TLS for token transit (JWT came over TLS)

CRITICAL SECURITY CONCERN:
- Tokens stored encrypted at rest but decrypted in memory for use
- No explicit OTP/password redaction
- parseEmailFromJwt extracts email from access token JWT (no signature verification)
- parseChatgptAccountId extracts chatgpt_account_id from idToken JWT (no verification)
- **RISK**: If device is compromised, stored tokens accessible in memory
- **RISK**: JWT parsing without signature verification could accept forged tokens if
  attacker can influence token generation (but tokens come over TLS so this is mitigated)

TEST COVERAGE:
- AuthStoreTest.kt
- FakeSharedPreferences.kt (test mock)
- OpenAIOAuthTest.kt (OAuth flow tests)
- LlmAuthApiKeyPersistTest.kt (api key persist tests)
- LlmAuthOtherAutoFlipTest.kt (other backend tests)
- LlmAuthOtherAutoFlipTest.kt

STATUS: CONFIRMED IMPLEMENTED
- Full-featured credential store with encrypted persistence
- OAuth auto-refresh with concurrent safety
- Generation-based invalidation
- Multiple provider support (OpenAI API, Codex OAuth, OpenRouter, Other, Local LFM)
- **HIGH RISK**: No memory protection for decrypted tokens; Keystore failure crashes
- JWT parsing without signature verification (acceptable if tokens over TLS)
- Credential type enforcement (ApiKey vs OAuth)
- Comprehensive test coverage across all provider types