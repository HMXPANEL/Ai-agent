package ai.closepaw.memory

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Agent-written memory entries must not persist credential-shaped content. */
class MemoryRedactionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun store(): MemoryStore = MemoryStore(tempFolder.newFolder("memory"))

    @Test
    fun `append redacts api key shape`() {
        val store = store()

        assertThat(
            store.append(
                MemoryScope.USER,
                MemorySection.PREFERENCES,
                "login uses api_key=sk-live-abcdefghijklmnopqrstuvwxyz1234 for sync"
            )
        ).isTrue()

        val raw = File(tempFolder.root, "memory/user.md").readText()
        assertThat(raw).doesNotContain("sk-live-abcdefghijklmnopqrstuvwxyz1234")
        assertThat(raw).contains("[REDACTED]")
    }

    @Test
    fun `append redacts bearer token and password assignment`() {
        val store = store()

        store.append(
            MemoryScope.DEVICE,
            MemorySection.FACTS,
            "header Authorization: Bearer abcdefghij1234567890 and password=hunter2!"
        )

        val raw = File(tempFolder.root, "memory/device.md").readText()
        assertThat(raw).doesNotContain("abcdefghij1234567890")
        assertThat(raw).doesNotContain("hunter2!")
        assertThat(raw).contains("[REDACTED]")
    }

    @Test
    fun `append leaves benign content untouched`() {
        val store = store()
        val note = "Prefers English; BACK may dismiss keyboard first"

        assertThat(store.append(MemoryScope.USER, MemorySection.PREFERENCES, note)).isTrue()

        val raw = File(tempFolder.root, "memory/user.md").readText()
        assertThat(raw).contains(note)
        assertThat(raw).doesNotContain("[REDACTED]")
    }
}
