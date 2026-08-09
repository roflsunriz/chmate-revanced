package io.github.chmate.revanced

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkBoundaryPatchTest {
    @Test
    fun `detects ChMate User-Agent factory literals`() {
        assertTrue(isChMateUserAgentLiteral("Monazilla/1.00 2chMate/0.8.10.241"))
        assertTrue(isChMateUserAgentLiteral("Monazilla/1.00"))
        assertFalse(isChMateUserAgentLiteral("Mozilla/5.0"))
        assertFalse(isChMateUserAgentLiteral("Monazilla/0.99"))
    }
}
