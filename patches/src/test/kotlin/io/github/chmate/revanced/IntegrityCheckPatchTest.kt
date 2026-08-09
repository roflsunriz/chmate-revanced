package io.github.chmate.revanced

import com.android.tools.smali.dexlib2.Opcode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IntegrityCheckPatchTest {
    private val firstRead = IntegrityArrayRead(10, 12, resultRegister = 2, sourceRegister = 3)
    private val secondRead = IntegrityArrayRead(14, 16, resultRegister = 5, sourceRegister = 3)

    @Test
    fun `detects integrity comparison with an interleaved array index constant`() {
        assertTrue(matchesIntegrityComparison(firstRead, secondRead, 5, 2, listOf(Opcode.CONST_4)))
    }

    @Test
    fun `does not classify reads from unrelated result arrays`() {
        assertFalse(
            matchesIntegrityComparison(
                firstRead,
                secondRead.copy(sourceRegister = 9),
                5,
                2,
                listOf(Opcode.CONST_4),
            ),
        )
    }

    @Test
    fun `does not classify comparisons with unrelated intervening work`() {
        assertFalse(matchesIntegrityComparison(firstRead, secondRead, 5, 2, listOf(Opcode.INVOKE_STATIC)))
    }

    @Test
    fun `detects the integrity division immediately before requesting a window feature`() {
        assertTrue(
            matchesWindowFeatureTrap(
                Opcode.DIV_INT_2ADDR,
                "Landroid/app/Activity;->requestWindowFeature(I)Z",
            ),
        )
    }

    @Test
    fun `does not classify ordinary divisions or unrelated calls as the window feature trap`() {
        assertFalse(
            matchesWindowFeatureTrap(
                Opcode.DIV_INT,
                "Landroid/app/Activity;->requestWindowFeature(I)Z",
            ),
        )
        assertFalse(
            matchesWindowFeatureTrap(
                Opcode.DIV_INT_2ADDR,
                "Ljava/lang/Math;->abs(I)I",
            ),
        )
    }
}
