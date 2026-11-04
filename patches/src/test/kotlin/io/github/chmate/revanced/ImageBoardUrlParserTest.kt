package io.github.chmate.revanced

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageBoardUrlParserTest {
    private val owner = "Lsample/Parser;"
    private fun parser(parameters: List<String>, parsesUri: Boolean) = ImmutableMethod(
        owner, "obfuscated", parameters.map { ImmutableMethodParameter(it, emptySet(), null) },
        "Ljp/syoboi/a2chMate/client/BBSUrlInfo;", 0, emptySet(), emptySet(),
        ImmutableMethodImplementation(4, if (parsesUri) listOf(ImmutableInstruction35c(
            Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0,
            ImmutableMethodReference("Landroid/net/Uri;", "parse", listOf("Ljava/lang/String;"), "Landroid/net/Uri;"),
        )) else emptyList(), emptyList(), emptyList()),
    )

    @Test
    fun `hooks both legacy parsers and synthetic parsers with an explicit companion parameter`() {
        assertTrue(isImageBoardCoreParser(parser(listOf("Ljava/lang/String;"), true)))
        assertTrue(isImageBoardCoreParser(parser(listOf(owner, "Ljava/lang/String;"), true)))
        assertFalse(isImageBoardCoreParser(parser(listOf("Landroid/content/Context;", "Ljava/lang/String;"), false)))
        assertFalse(isImageBoardCoreParser(parser(listOf("Ljava/lang/String;"), false)))
        assertFalse(isImageBoardCoreParser(parser(listOf("Ljava/lang/String;", "Ljava/lang/String;"), true)))
    }
}
