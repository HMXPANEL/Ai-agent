package ai.closepaw.agent

/**
 * Structured task understanding (assist layer, not a replacement for LLM reasoning).
 *
 * Extracted once per user request and carried alongside the free-form goal. It assists
 * planning, target resolution, and — critically — verification, by stating the expected
 * observable outcome up front. The LLM still reasons freely; this struct only records
 * what "done" looks like so the execution layer can check it.
 */
data class StructuredTask(
    val intent: TaskIntent,
    /** Human app label as written by the user (resolved against installed apps at execution). */
    val application: String?,
    /** Resolved Android package, when the app label matched an installed app. */
    val packageName: String?,
    /** Key entities: recipients, contacts, places, filenames… */
    val entities: List<String> = emptyList(),
    /** The payload content (message text, search query, …), when present. */
    val content: String? = null,
    /** Constraints mentioned in the request (e.g. "without calling"). */
    val constraints: List<String> = emptyList(),
    /** What must be observably true for the task to count as done. */
    val expectedOutcome: String,
    /** Machine-checkable verification criteria. */
    val verification: TaskVerificationCriteria,
    /** Raw request, preserved verbatim. */
    val rawRequest: String,
) {
    /** True for HMX self-management requests ("change the model", "open your settings"). */
    val isSelfRequest: Boolean get() = intent == TaskIntent.HMX_SELF
}

/** Task classes the agent can plan around. Generic — never app-specific. */
enum class TaskIntent {
    SEND_MESSAGE,
    CALL,
    OPEN_APP,
    SEARCH,
    COPY,
    FILL_FORM,
    SEND_EMAIL,
    NAVIGATE,
    READ,
    SHARE,
    HMX_SELF,
    SYSTEM_OPERATION,
    UNKNOWN,
}

/** What the execution layer must observe before reporting success. */
data class TaskVerificationCriteria(
    /** Expected foreground package after the task (null = no package requirement). */
    val expectedPackage: String? = null,
    /** Expected exact field value for type/fill tasks (null = no text requirement). */
    val expectedFieldText: String? = null,
    /** Expected outgoing message text for send tasks (null = none). */
    val expectedSentText: String? = null,
    /** Recipient/context that must still match at verification time. */
    val expectedRecipient: String? = null,
)

/**
 * Lightweight deterministic parser: verb-first intent + `on|in|via <App>` application +
 * quoted/capitalized entity spans. Never hard-codes app-specific parsing; the app label is
 * resolved against [knownApps] (label → package) supplied by the platform layer.
 */
object StructuredTaskParser {

    private val APP_PREPOSITIONS = listOf(" on ", " in ", " via ", " using ", " with ")

    fun parse(rawRequest: String, knownApps: Map<String, String> = emptyMap()): StructuredTask {
        val text = rawRequest.trim()
        val lower = text.lowercase()
        // Application first: intent detection may consult the app label
        // generically (e.g. any *mail* app implies email) without hard-coding apps.
        val (application, packageName, remainder) = detectApplication(text, knownApps)
        val intent = detectIntent(lower, application)
        val content = extractContent(text, intent)
        val entities = extractEntities(remainder.ifEmpty { text }, application)
        val expectedRecipient = entities.firstOrNull()
        return StructuredTask(
            intent = intent,
            application = application,
            packageName = packageName,
            entities = entities,
            content = content,
            expectedOutcome = describeOutcome(intent, application, expectedRecipient, content),
            verification = TaskVerificationCriteria(
                expectedPackage = packageName,
                expectedFieldText = if (intent == TaskIntent.FILL_FORM) content else content,
                expectedSentText = if (intent == TaskIntent.SEND_MESSAGE || intent == TaskIntent.SEND_EMAIL) {
                    content
                } else {
                    null
                },
                expectedRecipient = expectedRecipient,
            ),
            rawRequest = rawRequest,
        )
    }

    private fun detectIntent(lower: String, application: String?): TaskIntent {
        val first = lower.split(Regex("\\s+")).firstOrNull() ?: return TaskIntent.UNKNOWN
        // Generic mail-app rule: any application whose label contains "mail"
        // (Gmail, Email, FairEmail…) implies email — no fixed app list.
        val mailApp = application?.contains("mail", ignoreCase = true) == true
        return when {
            lower.contains("your settings") || lower.contains("yourself") ||
                lower.contains("which model") || lower.contains("what model") ||
                lower.contains("change the model") || lower.contains("switch provider") ||
                lower.contains("my previous chats") || lower.contains("my chats") ||
                lower.contains("last task fail") || lower.contains("diagnostics") -> TaskIntent.HMX_SELF
            first in setOf("send") &&
                ("email" in lower || "mail to" in lower || mailApp) -> TaskIntent.SEND_EMAIL
            // Generic messaging verbs only — never app names (no per-app core branches).
            first in setOf("send", "text", "message", "dm") -> TaskIntent.SEND_MESSAGE
            first in setOf("call", "phone", "dial", "ring") -> TaskIntent.CALL
            first in setOf("open", "launch", "start", "show") -> TaskIntent.OPEN_APP
            first in setOf("search", "google", "find", "look") -> TaskIntent.SEARCH
            first in setOf("copy") -> TaskIntent.COPY
            first in setOf("fill", "enter", "type") -> TaskIntent.FILL_FORM
            first in setOf("navigate", "directions", "route") -> TaskIntent.NAVIGATE
            first in setOf("read", "summarize", "summarise", "check") -> TaskIntent.READ
            first in setOf("share", "forward") -> TaskIntent.SHARE
            else -> TaskIntent.UNKNOWN
        }
    }

    private fun detectApplication(
        text: String,
        knownApps: Map<String, String>
    ): Triple<String?, String?, String> {
        val lower = text.lowercase()
        // Longest app-label match wins ("chrome beta" beats "chrome").
        val hit = knownApps.keys
            .sortedByDescending { it.length }
            .firstOrNull { label ->
                val l = label.lowercase()
                APP_PREPOSITIONS.any { lower.contains("$it$l") } || lower.endsWith(" $l") ||
                    lower.contains("open $l") || lower.contains("$l app")
            }
        if (hit == null) return Triple(null, null, text)
        // Strip the app mention so entity extraction doesn't pick it up.
        var remainder = text
        for (prep in APP_PREPOSITIONS) {
            val idx = remainder.lowercase().lastIndexOf("$prep${hit.lowercase()}")
            if (idx >= 0) {
                remainder = (remainder.substring(0, idx) + " " +
                    remainder.substring(idx + prep.length + hit.length)).trim()
                break
            }
        }
        return Triple(hit, knownApps[hit], remainder)
    }

    private fun extractContent(text: String, intent: TaskIntent): String? {
        if (intent != TaskIntent.SEND_MESSAGE && intent != TaskIntent.SEND_EMAIL &&
            intent != TaskIntent.FILL_FORM && intent != TaskIntent.SEARCH
        ) {
            return null
        }
        // Prefer an explicit quoted payload: send "hi" to X / send 'hi' …
        val quoted = Regex("""[""]([^""]+)[""]""").find(text)?.groupValues?.getOrNull(1)
        if (quoted != null) return quoted
        // Else: token after send/text/message/call verb, stopping at "to" + recipient.
        val m = Regex(
            """(?i)^(?:send|text|message|call|search|type|fill|enter)\s+(.+?)(?:\s+to\s+.+)?$"""
        ).find(text.trim())?.groupValues?.getOrNull(1)?.trim()
        return m?.takeIf { it.isNotEmpty() }
    }

    private fun extractEntities(remainder: String, application: String?): List<String> {
        val entities = mutableListOf<String>()
        // "to <Name …>" recipient.
        Regex("""(?i)\bto\s+([A-Z][\w.']*(?:\s+[A-Z][\w.']*)*)""").find(remainder)?.let {
            entities += it.groupValues[1].trim()
        }
        if (entities.isEmpty()) {
            // Fallback: capitalized spans that are not the app label or sentence start.
            Regex("""\b([A-Z][a-z]+(?:\s+[A-Z][a-z]+)*)\b""").findAll(remainder)
                .map { it.groupValues[1] }
                .filter { it != application && it.length > 1 }
                .take(2)
                .toCollection(entities)
        }
        return entities.distinct()
    }

    private fun describeOutcome(
        intent: TaskIntent,
        application: String?,
        recipient: String?,
        content: String?
    ): String = when (intent) {
        TaskIntent.SEND_MESSAGE -> "Message '${content ?: "?"}' sent to ${recipient ?: "recipient"}" +
            (application?.let { " on $it" } ?: "")
        TaskIntent.SEND_EMAIL -> "Email sent to ${recipient ?: "recipient"}"
        TaskIntent.CALL -> "Call placed to ${recipient ?: "contact"}"
        TaskIntent.OPEN_APP -> "${application ?: "App"} in foreground"
        TaskIntent.HMX_SELF -> "HMX self-state changed/answered"
        else -> "Requested outcome observably true"
    }
}
