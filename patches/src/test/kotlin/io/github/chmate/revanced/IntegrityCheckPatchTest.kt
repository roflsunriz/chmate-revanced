package io.github.chmate.revanced

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IntegrityCheckPatchTest {
    private val firstRead = IntegrityArrayRead(10, 12, resultRegister = 2, sourceRegister = 3)
    private val secondRead = IntegrityArrayRead(14, 16, resultRegister = 5, sourceRegister = 3)

    @Test
    fun `follows same class lifecycle dispatchers and terminates recursive calls`() {
        val owner = "Lsample/ImageActivity;"
        fun call(type: String, name: String) = ImmutableInstruction35c(
            Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0,
            ImmutableMethodReference(type, name, emptyList<String>(), "V"),
        )
        fun method(name: String, instructions: List<ImmutableInstruction>) = ImmutableMethod(
            owner, name, emptyList(), "V", 0, emptySet(), emptySet(),
            ImmutableMethodImplementation(1, instructions, emptyList(), emptyList()),
        )
        val entry = method("onResume", listOf(call(owner, "dispatch"), ImmutableInstruction10x(Opcode.RETURN_VOID)))
        val helper = method("dispatch", listOf(call(owner, "dispatch"), ImmutableInstruction11x(Opcode.THROW, 0)))
        val collected = lifecycleInstructions(entry, listOf(entry, helper))
        assertTrue(hasLifecycleTrapOpcode(collected.map { it.opcode }))
        assertEquals(4, collected.size)
        val foreignEntry = method("onResume", listOf(call("Lforeign/Activity;", "dispatch")))
        assertFalse(hasLifecycleTrapOpcode(lifecycleInstructions(foreignEntry, listOf(helper)).map { it.opcode }))
    }

    @Test
    fun `detects integrity comparison with an interleaved array index constant`() {
        assertTrue(matchesIntegrityComparison(firstRead, secondRead, 5, 2, listOf(Opcode.CONST_4)))
    }

    @Test
    fun `patches board and response list lifecycle integrity classes`() {
        assertTrue("Ljp/syoboi/a2chMate/activity/BoardList2Activity;" in lifecycleIntegrityClassTypes)
        assertTrue("Ljp/syoboi/a2chMate/activity/HomeActivity;" in lifecycleIntegrityClassTypes)
        assertTrue("Ljp/syoboi/a2chMate/activity/ImagesViewer2Activity;" in lifecycleIntegrityClassTypes)
        assertTrue("Ljp/syoboi/a2chMate/activity/ImageListActivity;" in lifecycleIntegrityClassTypes)
        assertTrue("Ljp/syoboi/a2chMate/activity/ResListActivity;" in lifecycleIntegrityClassTypes)
        assertTrue("Ljp/syoboi/a2chMate/activity/ResEditActivity;" in lifecycleIntegrityClassTypes)
        assertTrue("Ljp/syoboi/a2chMate/activity/SearchActivity;" in lifecycleIntegrityClassTypes)
        assertTrue("Ljp/syoboi/a2chMate/activity/ThreadListActivity;" in lifecycleIntegrityClassTypes)
        assertTrue("Ljp/syoboi/a2chMate/activity/WebViewActivity;" in lifecycleIntegrityClassTypes)
        assertTrue(hasLifecycleTrapOpcode(listOf(Opcode.THROW)))
        assertTrue(hasLifecycleTrapOpcode(listOf(Opcode.DIV_INT_LIT8)))
        assertTrue(hasLifecycleTrapOpcode(listOf(Opcode.NEW_ARRAY)))
        assertFalse(hasLifecycleTrapOpcode(listOf(Opcode.INVOKE_SUPER, Opcode.RETURN_VOID)))
    }

    @Test
    fun `permits a constant between array cast and read`() {
        assertTrue(isIntegrityArrayIndexSetup(listOf(Opcode.CONST_4)))
        assertFalse(isIntegrityArrayIndexSetup(listOf(Opcode.INVOKE_STATIC)))
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
    fun `detects an obfuscated integrity divide trap`() {
        assertTrue(
            matchesObfuscatedDivideTrap(
                Opcode.DIV_INT_2ADDR,
                divideResultRegister = 2,
                Opcode.CONST,
                numeratorRegister = 2,
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
