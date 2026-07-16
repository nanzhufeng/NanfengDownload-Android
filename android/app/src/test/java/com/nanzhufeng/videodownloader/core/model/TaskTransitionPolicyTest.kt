package com.nanzhufeng.videodownloader.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTransitionPolicyTest {
    @Test
    fun waitingCanStartParsingOrSkip() {
        assertTrue(
            TaskTransitionPolicy.canTransition(
                DownloadTaskStatus.WAITING,
                DownloadTaskStatus.PARSING,
            ),
        )
        assertTrue(
            TaskTransitionPolicy.canTransition(
                DownloadTaskStatus.WAITING,
                DownloadTaskStatus.SKIPPED,
            ),
        )
    }

    @Test
    fun downloadingCanWaitForNetworkOrCancel() {
        assertTrue(
            TaskTransitionPolicy.canTransition(
                DownloadTaskStatus.DOWNLOADING,
                DownloadTaskStatus.WAITING_NETWORK,
            ),
        )
        assertTrue(
            TaskTransitionPolicy.canTransition(
                DownloadTaskStatus.DOWNLOADING,
                DownloadTaskStatus.CANCELLED,
            ),
        )
    }

    @Test
    fun terminalStateCannotRestartDirectly() {
        assertFalse(
            TaskTransitionPolicy.canTransition(
                DownloadTaskStatus.COMPLETED,
                DownloadTaskStatus.DOWNLOADING,
            ),
        )
        assertFalse(
            TaskTransitionPolicy.canTransition(
                DownloadTaskStatus.FAILED,
                DownloadTaskStatus.DOWNLOADING,
            ),
        )
    }
}
