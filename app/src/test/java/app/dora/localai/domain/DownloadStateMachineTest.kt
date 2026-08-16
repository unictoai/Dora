package app.dora.localai.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStateMachineTest {
    @Test
    fun acceptsHappyPath() {
        assertTrue(DownloadStateMachine.canTransition(DownloadState.QUEUED, DownloadState.STARTING))
        assertTrue(DownloadStateMachine.canTransition(DownloadState.STARTING, DownloadState.DOWNLOADING))
        assertTrue(DownloadStateMachine.canTransition(DownloadState.DOWNLOADING, DownloadState.VERIFYING))
        assertTrue(DownloadStateMachine.canTransition(DownloadState.VERIFYING, DownloadState.VALIDATING))
        assertTrue(DownloadStateMachine.canTransition(DownloadState.VALIDATING, DownloadState.INSTALLING))
        assertTrue(DownloadStateMachine.canTransition(DownloadState.INSTALLING, DownloadState.COMPLETED))
    }

    @Test
    fun acceptsPauseResumeAndRetryPaths() {
        assertTrue(DownloadStateMachine.canTransition(DownloadState.DOWNLOADING, DownloadState.PAUSED))
        assertTrue(DownloadStateMachine.canTransition(DownloadState.PAUSED, DownloadState.DOWNLOADING))
        assertTrue(DownloadStateMachine.canTransition(DownloadState.FAILED, DownloadState.RETRYING))
        assertTrue(DownloadStateMachine.canTransition(DownloadState.RETRYING, DownloadState.STARTING))
    }

    @Test
    fun rejectsReadyShortcutAndTerminalTransitions() {
        assertFalse(DownloadStateMachine.canTransition(DownloadState.DOWNLOADING, DownloadState.COMPLETED))
        assertFalse(DownloadStateMachine.canTransition(DownloadState.VERIFYING, DownloadState.COMPLETED))
        assertFalse(DownloadStateMachine.canTransition(DownloadState.COMPLETED, DownloadState.DOWNLOADING))
    }
}
