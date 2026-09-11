package ai.closepaw.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** P5 cross-turn verification memory. */
class TaskVerificationTrackerTest {

    @Test
    fun `fresh tracker has nothing unresolved`() {
        assertThat(TaskVerificationTracker().hasUnresolvedActuation()).isFalse()
    }

    @Test
    fun `unverified actuation arms the block`() {
        val tracker = TaskVerificationTracker()
        tracker.noteTurn(TaskVerification.Assessment.REQUIRED_BUT_UNVERIFIED)

        assertThat(tracker.hasUnresolvedActuation()).isTrue()
    }

    @Test
    fun `fresh verified proof clears earlier doubt`() {
        val tracker = TaskVerificationTracker()
        tracker.noteTurn(TaskVerification.Assessment.REQUIRED_BUT_UNVERIFIED)
        tracker.noteTurn(TaskVerification.Assessment.REQUIRED_AND_VERIFIED)

        assertThat(tracker.hasUnresolvedActuation()).isFalse()
    }

    @Test
    fun `observation-only turn neither arms nor clears`() {
        val tracker = TaskVerificationTracker()
        tracker.noteTurn(TaskVerification.Assessment.NOT_REQUIRED)
        assertThat(tracker.hasUnresolvedActuation()).isFalse()

        tracker.noteTurn(TaskVerification.Assessment.REQUIRED_BUT_UNVERIFIED)
        tracker.noteTurn(TaskVerification.Assessment.NOT_REQUIRED)
        assertThat(tracker.hasUnresolvedActuation()).isTrue()
    }

    @Test
    fun `reset clears everything`() {
        val tracker = TaskVerificationTracker()
        tracker.noteTurn(TaskVerification.Assessment.REQUIRED_BUT_UNVERIFIED)
        tracker.reset()

        assertThat(tracker.hasUnresolvedActuation()).isFalse()
    }
}
