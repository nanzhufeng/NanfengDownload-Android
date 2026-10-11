package com.nanzhufeng.videodownloader.domain.download

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferConcurrencyBudgetTest {
    @Test fun singleConnectionAudioLeavesTheRestOfTheBudgetForVideo() {
        val budget = TransferConcurrencyBudget(8)
        budget.enter("first").use {
            assertEquals(listOf(6, 1), budget.connectionsForStreams("first", listOf(6, 1)))
            budget.enter("second").use {
                assertEquals(listOf(3, 1), budget.connectionsForStreams("first", listOf(6, 1)))
                assertEquals(listOf(2, 2), budget.connectionsForStreams("second", listOf(4, 4)))
            }
        }
    }
    @Test
    fun dividesTheGlobalBudgetAcrossActiveTasksAndStreams() {
        val budget = TransferConcurrencyBudget(totalConnections = 8)
        val first = budget.enter("first")
        try {
            assertEquals(4, budget.connectionsPerStream("first", activeStreamCount = 2, requestedConnections = 6))

            val second = budget.enter("second")
            try {
                assertEquals(2, budget.connectionsPerStream("first", activeStreamCount = 2, requestedConnections = 6))
                assertEquals(2, budget.connectionsPerStream("second", activeStreamCount = 2, requestedConnections = 4))
            } finally {
                second.close()
            }

            assertEquals(4, budget.connectionsPerStream("first", activeStreamCount = 2, requestedConnections = 6))
        } finally {
            first.close()
        }
    }
}
