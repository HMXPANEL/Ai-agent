package ai.closepaw.agent

import ai.closepaw.model.PerceptionElement
import ai.closepaw.model.ScreenSnapshot

/**
 * HMX self-understanding layer (P14).
 *
 * A structured, read-only view of HMX's own state: identity (provider/model/
 * backend), current screen/task, capabilities, and recent errors. Built ONLY
 * from existing single sources of truth (settings snapshot, session config,
 * capability snapshot, diagnostics) — never a second truth, never OCR-only.
 * The agent answers "which model are you using?" from this, and verifies
 * self-management outcomes (e.g. "change the model") against persisted state.
 */
data class HmxSelfState(
    val provider: String?,
    val model: String?,
    val backend: String?,
    val otherConfigured: Boolean,
    val sessionId: String?,
    val taskSummary: String?,
    val screen: AppStateSnapshot?,
    /** Capability name → state name (UNKNOWN/absent = denied by default). */
    val capabilities: Map<String, String> = emptyMap(),
    val recentErrors: List<String> = emptyList(),
) {
    /** Compact one-liner appended to the agent system prompt. */
    fun promptLine(): String = buildString {
        append("[self: provider=${provider ?: "?"} model=${model ?: "?"}")
        append(" backend=${backend ?: "?"}")
        screen?.let { append(" app=${it.packageName ?: "?"} ui=${it.elementCount}") }
        if (capabilities.isNotEmpty()) {
            val avail = capabilities.count { it.value == "AVAILABLE" }
            append(" caps=$avail/${capabilities.size}")
        }
        taskSummary?.take(80)?.let { append(" task=$it") }
        append("]")
    }

    /** Multi-line self-report answering state questions. */
    fun describe(): String = buildString {
        appendLine("HMX self-state:")
        appendLine("- provider: ${provider ?: "<not selected>"}")
        appendLine("- model: ${model ?: "<not selected>"}")
        appendLine("- backend: ${backend ?: "<unknown>"}")
        appendLine("- other-configured: $otherConfigured")
        appendLine("- session: ${sessionId ?: "<none>"}")
        appendLine("- task: ${taskSummary ?: "<none>"}")
        appendLine("- screen: ${screen?.describe() ?: "<unknown>"}")
        if (capabilities.isEmpty()) appendLine("- capabilities: <unknown>")
        else capabilities.forEach { (k, v) -> appendLine("- cap $k: $v") }
        recentErrors.take(3).forEach { appendLine("- recent error: $it") }
    }
}

/** Compact semantic screen summary (P15 perception): counts + focus, not dumps. */
data class AppStateSnapshot(
    val packageName: String?,
    val elementCount: Int,
    val editableCount: Int,
    val clickableCount: Int,
    val focusedText: String?,
    val dialogVisible: Boolean,
) {
    fun describe(): String =
        "pkg=${packageName ?: "?"} elements=$elementCount editable=$editableCount " +
            "focused=${focusedText?.take(40) ?: "-"} dialog=$dialogVisible"
}

/** Build a compact snapshot from a fresh accessibility capture. */
fun ScreenSnapshot.summarize(packageName: String?): AppStateSnapshot {
    val focused = elements.firstOrNull { it.isFocused && it.isEditable }
        ?: elements.firstOrNull { it.isFocused }
    return AppStateSnapshot(
        packageName = packageName,
        elementCount = elements.size,
        editableCount = elements.count { it.isEditable },
        clickableCount = elements.count { it.isClickable },
        focusedText = focused?.bestText(),
        dialogVisible = elements.any { it.isDialogLike() },
    )
}

private fun PerceptionElement.bestText(): String? =
    text.takeIf { it.isNotEmpty() }
        ?: description.takeIf { it.isNotEmpty() }
        ?: hintText.takeIf { it.isNotEmpty() }

private fun PerceptionElement.isDialogLike(): Boolean {
    val c = className.lowercase()
    return ("dialog" in c || "alert" in c || "popup" in c || "modal" in c) && text.isNotEmpty()
}
