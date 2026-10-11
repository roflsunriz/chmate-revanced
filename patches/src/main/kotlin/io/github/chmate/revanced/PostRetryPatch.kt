package io.github.chmate.revanced

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.instructionsOrNull
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val BBS_URL_INFO = "Ljp/syoboi/a2chMate/client/BBSUrlInfo;"
private const val BRIDGE = "Lapp/revanced/extension/chmate/PostRetryBridge;"

internal fun BytecodePatchContext.patchPostRetries() {
    val candidates = classDefs.flatMap { classDef ->
        classDef.methods.filter { method ->
            AccessFlags.PUBLIC.isSet(method.accessFlags) &&
                !AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.parameterTypes.size in 5..6 &&
                method.parameterTypes[0].toString() == BBS_URL_INFO &&
                (method.parameterTypes.map(CharSequence::toString).let { parameters ->
                    (parameters.size == 5 && parameters[2] == "Z") ||
                        (parameters.size == 6 && parameters[2] == "Ljava/lang/String;" && parameters[3] == "Z")
                }) &&
                method.returnType.startsWith("L") &&
                method.instructionsOrNull?.mapNotNull { instruction ->
                    ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string
                }?.toSet()?.let { literals ->
                    if (method.parameterTypes.size == 5) {
                        "SUBJECT" in literals && "MESSAGE" in literals
                    } else {
                        "MESSAGE" in literals && "TIME" in literals && "FROM" in literals
                    }
                } == true
        }
    }
    val replies = candidates.filter { it.parameterTypes.size == 5 }
    val threads = candidates.filter { it.parameterTypes.size == 6 }
    if (replies.size != 1 || threads.size != 1) {
        throw PatchException("Could not identify post methods: replies=${replies.size}, threads=${threads.size}")
    }
    patchSubmission(replies.single(), "enter", 5,
        "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;ZLjava/lang/Object;Ljava/lang/Object;")
    patchSubmission(threads.single(), "enterThread", 6,
        "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;ZLjava/lang/Object;Ljava/lang/Object;")
}

private fun BytecodePatchContext.patchSubmission(
    method: Method,
    entryName: String,
    lastParameter: Int,
    entryParameters: String,
) {
    val mutableMethod = firstMethod(method)
    val instructions = mutableMethod.implementation!!.instructions.toList()
    val finalThrowIndex = instructions.indices.lastOrNull { index ->
        instructions[index].opcode == Opcode.THROW &&
            instructions.subList(index + 1, instructions.size).all {
                it.opcode == Opcode.GOTO || it.opcode == Opcode.GOTO_16 || it.opcode == Opcode.GOTO_32
            }
    } ?: throw PatchException("Post submission has no terminal exception path: ${method.definingClass}")
    val finalThrow = instructions[finalThrowIndex] as OneRegisterInstruction
    val resultScratch = if (finalThrow.registerA == 0) 1 else 0
    val resultType = method.returnType
    mutableMethod.replaceInstruction(
        finalThrowIndex,
        "invoke-static/range {v${finalThrow.registerA} .. v${finalThrow.registerA}}, $BRIDGE->retryOrThrow(Ljava/lang/Throwable;)Ljava/lang/Object;",
    )
    mutableMethod.addInstructions(
        finalThrowIndex + 1,
        """
            move-result-object v$resultScratch
            check-cast v$resultScratch, $resultType
            return-object v$resultScratch
        """.trimIndent(),
    )

    val returns = instructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode == Opcode.RETURN_OBJECT) {
            index to (instruction as OneRegisterInstruction).registerA
        } else null
    }
    if (returns.isEmpty()) throw PatchException("Post submission has no result return")
    returns.asReversed().forEach { (index, register) ->
        mutableMethod.replaceInstruction(index, "invoke-static {}, $BRIDGE->clear()V")
        mutableMethod.addInstructions(index + 1, "return-object v$register")
    }
    mutableMethod.addInstructions(
        0,
        "invoke-static/range {p0 .. p$lastParameter}, $BRIDGE->$entryName($entryParameters)V",
    )
}
