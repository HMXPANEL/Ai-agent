package ai.closepaw.llm

/**
 * Correlates streamed tool-call fragments into complete calls (pure, JVM-testable).
 *
 * Two correlation modes:
 * - **Indexed** (OpenAI et al.): fragments carry `tool_calls[].index`; builders keyed by it,
 *   with id/name backfill when those arrive in later deltas. Behavior identical to the
 *   original inline implementation.
 * - **Unindexed** (some OpenAI-compatible providers, e.g. Gemini, omit `index`): fragments
 *   are correlated by occurrence order — the n-th index-less delta of a chunk continues the
 *   n-th index-less call. This keeps parallel index-less calls distinct instead of merging
 *   them into a single `0L` bucket.
 */
class ToolCallDeltaAccumulator {

    private data class Builder(
        var id: String,
        var name: String,
        val args: StringBuilder = StringBuilder(),
    )

    private val indexed = LinkedHashMap<Long, Builder>()
    private val unindexed = mutableListOf<Builder>()

    /**
     * Feed one delta fragment.
     *
     * @param index stream index, or null when the provider omitted it.
     * @param id call id when present in this fragment.
     * @param name function name when present in this fragment.
     * @param argsFragment arguments JSON fragment when present.
     * @param chunkOrdinal position of this delta among the index-less deltas of its chunk
     *   (0 for the common single-delta case); ignored when [index] != null.
     */
    fun onDelta(
        index: Long?,
        id: String?,
        name: String?,
        argsFragment: String?,
        chunkOrdinal: Int = 0,
    ) {
        val builder = if (index != null) {
            indexed.getOrPut(index) {
                Builder(id = id ?: "call_$index", name = name ?: "")
            }
        } else {
            while (unindexed.size <= chunkOrdinal) {
                unindexed += Builder(id = "call_u${unindexed.size}", name = "")
            }
            unindexed[chunkOrdinal]
        }
        // Backfill id/name when they arrive in later fragments.
        if (id != null && (builder.id.startsWith("call_"))) builder.id = id
        if (!name.isNullOrEmpty() && builder.name.isEmpty()) builder.name = name
        if (argsFragment != null) builder.args.append(argsFragment)
    }

    /** Drain all builders (indexed first, then unindexed) into complete calls and reset. */
    fun drain(): List<LLMToolCall> {
        val out = ArrayList<LLMToolCall>(indexed.size + unindexed.size)
        for ((_, b) in indexed) out += LLMToolCall(b.id, b.name, b.args.toString())
        for (b in unindexed) out += LLMToolCall(b.id, b.name, b.args.toString())
        clear()
        return out
    }

    fun clear() {
        indexed.clear()
        unindexed.clear()
    }

    fun isEmpty(): Boolean = indexed.isEmpty() && unindexed.isEmpty()
}
