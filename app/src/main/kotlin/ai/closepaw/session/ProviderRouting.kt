package ai.closepaw.session

import ai.closepaw.llm.ApiType
import ai.closepaw.llm.LLMClient
import ai.closepaw.llm.LLMProvider
import ai.closepaw.llm.ModelEntry

/**
 * Provider-routing invariants.
 *
 * Routing is model-led: `SessionConfig.mainModel` resolves to a [ModelEntry]
 * whose provider selects the client. `SessionConfig.provider` carries the
 * UI-selected provider so the bootstrap can FAIL FAST instead of silently
 * misrouting when the two disagree (e.g. OTHER tab shown while a Codex model
 * key is still selected).
 */

/**
 * Fail fast when the UI-selected provider disagrees with the model's catalog
 * provider. Nulls skip the check (unknown model / legacy config without an
 * explicit provider) — those paths already fail loudly elsewhere
 * (MissingCredential / factory "unknown model").
 *
 * @throws IllegalStateException on mismatch. Never logs secrets — names only.
 */
internal fun checkProviderRouting(selectedProvider: LLMProvider?, entry: ModelEntry?) {
    if (selectedProvider == null || entry == null) return
    if (entry.provider != selectedProvider) {
        throw IllegalStateException(
            "Provider mismatch: UI selected $selectedProvider but model " +
                "'${entry.name}' resolves to ${entry.provider}. Re-select the " +
                "provider/model in LLM settings; refusing to silently misroute."
        )
    }
}

/** Strip userinfo, query, and fragment so logged URLs can never leak secrets. */
internal fun sanitizeBaseUrlForLog(baseUrl: String?): String {
    if (baseUrl.isNullOrBlank()) return "-"
    return try {
        val uri = java.net.URI(baseUrl)
        val host = uri.host ?: return "<unparseable>"
        val port = if (uri.port != -1) ":${uri.port}" else ""
        val path = uri.path?.trimEnd('/')?.takeIf { it.isNotEmpty() && it != "/" } ?: ""
        "$host$port$path"
    } catch (_: Exception) {
        "<unparseable>"
    }
}

/**
 * Canonical one-line routing proof. Emitted once per session bootstrap so the
 * runtime log makes it impossible to confuse which provider was selected.
 * Contains no keys, tokens, or headers — model id and host only.
 */
internal fun formatRoutingLine(
    provider: LLMProvider,
    modelId: String,
    client: LLMClient,
    api: ApiType?,
    baseUrl: String?,
): String =
    "Routing: provider=$provider model=$modelId " +
        "client=${client.javaClass.simpleName} api=${api?.name ?: "-"} " +
        "baseUrl=${sanitizeBaseUrlForLog(baseUrl)}"
