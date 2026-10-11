package io.github.chmate.revanced

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.instructionsOrNull
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val BRIDGE = "Lapp/revanced/extension/chmate/AdvancedNgBridge;"
private const val STRING = "Ljava/lang/String;"
private val markers = setOf(8, 16, 32, 4096, 16384, 262144)

internal fun BytecodePatchContext.patchAdvancedNg() {
    val candidates = classDefs.flatMap { classDef ->
        classDef.methods.filter { method ->
            !AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "I" &&
                method.parameterTypes.size in 2..3 &&
                method.parameterTypes[0].startsWith("L") &&
                method.parameterTypes[1].toString() == "Z" &&
                (method.parameterTypes.size == 2 ||
                    method.parameterTypes[2].toString() == "Landroid/util/SparseIntArray;") &&
                method.instructionsOrNull?.mapNotNull {
                    (it as? NarrowLiteralInstruction)?.narrowLiteral
                }?.toSet()?.containsAll(markers) == true
        }
    }
    if (candidates.size != 1) {
        throw PatchException("Could not identify NG decision method: ${candidates.size} candidates")
    }
    val method = candidates.single()
    val responseType = method.parameterTypes[0].toString()
    val responseClass = classDefs.singleOrNull { it.type == responseType }
        ?: throw PatchException("Could not find NG response class: $responseType")
    val configuration = responseClass.responseFieldConfiguration()
    val mutableMethod = firstMethod(method)
    val instructions = mutableMethod.implementation!!.instructions.toList()
    val bodyStart = instructions.indexOfFirst {
        (it as? NarrowLiteralInstruction)?.narrowLiteral == 16384
    }
    if (bodyStart < 0) throw PatchException("NG decision has no body marker")
    val returns = instructions.indices.filter { index -> instructions[index].opcode == Opcode.RETURN }
    if (returns.isEmpty()) throw PatchException("NG decision has no result return")
    returns.asReversed().forEach { index ->
        val resultRegister = (instructions[index] as OneRegisterInstruction).registerA
        mutableMethod.replaceInstruction(index, "move/from16 v0, v$resultRegister")
        mutableMethod.addInstructions(index + 1, """
            const-string v1, "$configuration"
            const/4 v2, ${if (index > bodyStart) "0x1" else "0x0"}
            invoke-static {v0, v1, v2}, $BRIDGE->finish(ILjava/lang/String;Z)I
            move-result v0
            return v0
        """.trimIndent())
    }
    mutableMethod.addInstructions(0, """
        move-object/from16 v0, p1
        move-object/from16 v1, p0
        invoke-static {v0, v1}, $BRIDGE->enter(Ljava/lang/Object;Ljava/lang/Object;)V
    """.trimIndent())
}

private fun ClassDef.responseFieldConfiguration(): String {
    val constructors = methods.filter { method ->
        method.name == "<init>" && method.parameterTypes.size >= 6 &&
            method.parameterTypes[0].toString() == "I" &&
            (1..5).all { method.parameterTypes[it].toString() == STRING }
    }
    constructors.forEach { constructor ->
        val implementation = constructor.implementation ?: return@forEach
        val parameterWords = 1 + constructor.parameterTypes.sumOf { type ->
            if (type.toString() == "J" || type.toString() == "D") 2 else 1
        }
        val parameterStart = implementation.registerCount - parameterWords
        val fields = mutableMapOf<Int, String>()
        implementation.instructions.forEach { instruction ->
            if (instruction.opcode != Opcode.IPUT && instruction.opcode != Opcode.IPUT_OBJECT) return@forEach
            val registers = instruction as? TwoRegisterInstruction ?: return@forEach
            if (registers.registerB != parameterStart) return@forEach
            val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                ?: return@forEach
            val parameter = registers.registerA - parameterStart
            val expectedType = when (parameter) {
                1 -> "I"
                in 2..6 -> STRING
                9 -> "[I"
                else -> null
            }
            if (reference.type == expectedType) fields.putIfAbsent(parameter, reference.name)
        }
        if ((1..6).all { it in fields } && 9 in fields) {
            return ((1..6).toList() + 9).joinToString("|") { fields.getValue(it) }
        }
    }
    throw PatchException("Could not identify response fields in $type")
}
