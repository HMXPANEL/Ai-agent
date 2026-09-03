FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/auth/OpenAIOAuth.kt
PACKAGE: ai.closepaw.auth

MAIN CLASSES:
- OAuthConfig - OAuth constants (clientId, authorize URL, token URL, redirect URI, scope)
- OAuthCallbackServer - local HTTP server for OAuth callback
- OAuthTokens - access token, refresh token, expiresAt, email, idToken
- OAuthTokenExchange - token exchange, refresh, DNS retry logic
- OAuthCodexValidator - token validation against ChatGPT backend

COMPANION OBJECT / CONSTANTS:
- CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
- AUTHORIZE_URL = "https://auth.openai.com/oauth/authorize"
- TOKEN_URL = "https://auth.openai.com/oauth/token"
- REDIRECT_URI = "http://localhost:1455/auth/callback"
- SCOPE = "openid profile email offline_access api.connectors.read api.connectors.invoke"
- CALLBACK_PORT = 1455

PKCE:
- generatePkce() - generates verifier+challenge (SHA-256)
- generateOAuthState() - random hex state for CSRF protection
- buildAuthorizeUrl(challenge, state) - builds full authorization URL

OAuthCallbackServer:
- start() - binds ServerSocket on port 1455, 2 min timeout
- waitForCallback() - accepts connection, parses code/state, sends HTML response
- stop() - closes server socket
- successHtml()/errorHtml() - response HTML with closepaw://oauth-complete redirect

OAuthTokenExchange:
- exchange(code, verifier) - main flow: token exchange via id_token → api key
- exchangeForApiKey(idToken) - exchanges id_token for openai-api-key at TOKEN_URL
- refresh(refreshToken) - refreshes access token
- postTokenRequest(body) - POST to TOKEN_URL with DNS retry (UnknownHostException retry)
- toFormBody() - converts Pair<String,String> to application/x-www-form-urlencoded

OAuthCodexValidator:
- extractAccountId(accessToken) - extracts chatgpt_account_id from JWT
- validate(accessToken) - validates token against https://chatgpt.com/backend-api/codex/responses
  - POST with model "gpt-5.4", stream true, store false
  - headers: Authorization Bearer, chatgpt-account-id, OpenAI-Beta, originator, chatgpt-account-id
  - SSE stream on 200-299 → token valid
  - 401/403 → token rejected
  - else → connection error

PRIVATE HELPERS:
- parseEmailFromJwt(jwt) - extracts email from JWT payload
- base64UrlEncode/Decode - custom Base64 URL encoding
- withDnsRetry(block) - retries on UnknownHostException (2 retries with exponential backoff)
- List<Pair<String,String>>.toFormBody() - form encoding

SIDE EFFECTS:
- Starts local HTTP server on port 1455
- Launches browser to authorization URL
- Reads HTTP callback from browser
- Makes POST requests to OpenAI token endpoint
- Refreshes access tokens
- Validates tokens against ChatGPT backend
- Emits log messages for all operations

PERSISTENCE:
- None - OAuth state is transient (code verifier in memory, tokens stored by AuthStore)

NETWORK:
- All OAuth network: TOKEN_URL (https://auth.openai.com/oauth/token)
- Authorization: AUTHORIZE_URL (https://auth.openai.com/oauth/authorize)
- Token validation: CODEX_URL (https://chatgpt.com/backend-api/codex/responses)
- DNS retry for transient network failures

ANDROID API:
- HttpURLConnection for all HTTP requests
- ServerSocket for callback server
- Intent for browser launch
- Toast/Settings for permission prompts

ERROR HANDLING:
- OAuth flow errors: cancelled, denied, state mismatch, missing code, timeout
- Token exchange errors: HTTP codes, error responses
- Refresh errors: same pattern
- Validation errors: 401/403 = rejected, other = connection error
- DNS retry: 2 retries with backoff, then give up
- All errors returned as Result.Error or thrown exceptions

SECURITY:
- PKCE generation with SHA-256 (industry standard)
- State CSRF protection via random 16-byte hex state
- Redirect URI registered with OpenAI
- **CRITICAL**: Local HTTP server on port 1455 - vulnerable to localhost attacks
  if other apps can bind to same port or intercept callbacks
- Code verifier/challenge must remain secret until exchange
- **RISK**: Callback URL (closepaw://oauth-complete) could be intercepted
- **RISK**: OAuth code could be intercepted if redirect URI not properly registered
- **RISK**: Local server socket could be hijacked by another app with same port
- **RISK**: Token exchange sends client_id + code + code_verifier + redirect_uri
- **RISK**: JWT parsed without signature verification (parseEmailFromJwt, extractAccountId)
- **RISK**: auth code flow over localhost could be intercepted by malicious app
- **RISK**: openpaw:// URI scheme could be handled by malicious app

CRITICAL FLOW:
1. generatePkce() → verifier + challenge
2. buildAuthorizeUrl() → authorization URL with code_challenge, code_challenge_method=S256, state
3. User browses to URL, logs into ChatGPT/OpenAI
4. Redirect to REDIRECT_URI (http://localhost:1455/auth/callback) with code + state
5. OAuthCallbackServer.waitForCallback() receives request
6. Validates state matches expected → prevents CSRF
7. Exchanges code + verifier + client_id + redirect_uri at TOKEN_URL
8. Receives id_token + accessToken + refreshToken + email + expiresIn
9. exchangeForApiKey(idToken) → openai-api-key (same as Codex CLI)
10. Tokens stored via AuthStore.set(LLMProvider.OPENAI_CODEX, cred)
11. codexHeaders() auto-refreshes within REFRESH_BUFFER_MS

TEST COVERAGE:
- OpenAIOAuthTest.kt - OAuth flow instrumented test
- AuthStoreTest.kt - AuthStore integration with OAuth
- LlmAuthOtherAutoFlipTest.kt - other backend auth tests
- LlmAuthApiKeyPersistTest.kt - API key persist tests

STATUS: CONFIRMED IMPLEMENTED
- Complete OpenAI Codex OAuth 2.0 flow with PKCE
- Local HTTP callback server (port 1455)
- Token exchange (id_token → api key)
- Token refresh logic
- Code validation against ChatGPT backend
- **HIGH RISK**: Local HTTP server security (port 1455 accessibility)
- **HIGH RISK**: JWT parsing without signature verification
- **MEDIUM RISK**: Localhost callback interception risk
- **MEDIUM RISK**: DNS retry only handles UnknownHostException, not all network failures
- **LOW RISK**: State CSRF protection via random state
- Well-implemented for the use case, but with noted security considerations