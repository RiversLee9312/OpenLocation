package dev.openlocation.runtime

import org.junit.Assert.*
import org.junit.Test

class TargetPolicyTest {
    @Test
    fun brokerOptInCannotOverrideAnUnselectedRecipient() {
        val selected = setOf(10001, 10002)
        assertTrue(TargetPolicy.allows(10001, emptyList(), selected))
        assertFalse(TargetPolicy.allows(10003, emptyList(), selected))
        assertTrue(TargetPolicy.allows(10003, listOf(10003, 10001, 10002), selected))
        assertFalse(TargetPolicy.allows(10001, listOf(10001, 10002, 10003), selected))
        assertFalse(TargetPolicy.allows(10001, listOf(10001, 10002), emptySet()))
    }
}
