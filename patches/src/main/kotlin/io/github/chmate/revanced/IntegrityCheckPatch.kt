package io.github.chmate.revanced

import app.revanced.patcher.extensions.instructionsOrNull
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val REQUEST_WINDOW_FEATURE =
    "Landroid/app/Activity;->requestWindowFeature(I)Z"

internal fun BytecodePatchContext.patchIntegrityChecks() {
    classDefs.flatMap { classDef ->
        classDef.methods.mapNotNull { method ->
            val instructions = method.instructionsOrNull?.toList() ?: return@mapNotNull null
            if (instructions.size < 100) {
                return@mapNotNull null
            }
            val failureBranchIndices = instructions.indices.filter { index ->
                instructions.isIntegrityFailureBranch(index)
            }
            val windowFeatureTrapIndices = if (failureBranchIndices.isEmpty()) {
                emptyList()
            } else {
                instructions.indices.filter { index -> instructions.isWindowFeatureTrap(index) }
            }
            val divideTrapIndices = instructions.indices.filter { index ->
                instructions.isObfuscatedDivideTrap(index)
            }
            if (failureBranchIndices.isEmpty() && windowFeatureTrapIndices.isEmpty() && divideTrapIndices.isEmpty()) {
                null
            } else {
                method to IntegrityPatchIndices(failureBranchIndices, windowFeatureTrapIndices, divideTrapIndices)
            }
        }
    }.forEach { (method, indices) ->
        val mutableMethod = firstMethod(method)
        val originalInstructions = method.instructionsOrNull!!.toList()
        indices.failureBranches.forEach { index -> mutableMethod.replaceInstruction(index, "nop") }
        (indices.windowFeatureTraps + indices.divideTraps).distinct().forEach { index ->
            val divide = originalInstructions[index] as TwoRegisterInstruction
            mutableMethod.replaceInstruction(index, "const/16 v${divide.registerA}, 0x1")
        }
    }
    patchActivityLifecycleIntegrity()
}

internal val lifecycleIntegrityClassTypes = setOf(
    "Ljp/syoboi/a2chMate/activity/BoardList2Activity;",
    "Ljp/syoboi/a2chMate/activity/HomeActivity;",
    "Ljp/syoboi/a2chMate/activity/ResListActivity;",
    "Ljp/syoboi/a2chMate/activity/ResEditActivity;",
    "Ljp/syoboi/a2chMate/activity/SearchActivity;",
    "Ljp/syoboi/a2chMate/activity/ThreadListActivity;",
    "Ljp/syoboi/a2chMate/activity/WebViewActivity;",
)

private val lifecycleTrapOpcodes = setOf(
    Opcode.THROW,
    Opcode.DIV_INT,
    Opcode.DIV_INT_2ADDR,
    Opcode.DIV_INT_LIT8,
    Opcode.DIV_INT_LIT16,
    Opcode.NEW_ARRAY,
)

private fun BytecodePatchContext.patchActivityLifecycleIntegrity() {
    classDefs.filter { it.type in lifecycleIntegrityClassTypes }.forEach { classDef ->
        val superType = classDef.superclass
        classDef.methods.filter { method ->
            method.name in setOf("onStart", "onResume", "onPause") &&
                method.parameterTypes.isEmpty() && method.returnType == "V" &&
                method.instructionsOrNull?.toList().orEmpty().let { instructions ->
                    instructions.any { it.opcode in lifecycleTrapOpcodes } &&
                        instructions.any { instruction ->
                            if (instruction.opcode !in setOf(Opcode.INVOKE_SUPER, Opcode.INVOKE_SUPER_RANGE)) {
                                return@any false
                            }
                            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            reference?.let { it.definingClass == superType && it.name == method.name } == true
                        }
                }
        }.forEach { method ->
            firstMethod(method).addInstructions(
                0,
                """
                    invoke-super {p0}, $superType->${method.name}()V
                    return-void
                """.trimIndent(),
            )
        }
    }
}

private data class IntegrityPatchIndices(
    val failureBranches: List<Int>,
    val windowFeatureTraps: List<Int>,
    val divideTraps: List<Int>,
)

private fun List<Instruction>.isIntegrityFailureBranch(index: Int): Boolean {
    val comparison = getOrNull(index) as? TwoRegisterInstruction ?: return false
    if (comparison.opcode != Opcode.IF_NE) return false

    // Newer obfuscators insert substantially more register setup between the paired reads.
    val reads = (maxOf(2, index - 64) until index).mapNotNull(::integerArrayReadEndingAt)
    val left = reads.lastOrNull { it.resultRegister == comparison.registerA } ?: return false
    val right = reads.lastOrNull { it.resultRegister == comparison.registerB } ?: return false
    if (left == right) return false

    val (first, second) = listOf(left, right).sortedBy(IntegrityArrayRead::startIndex)
    if (first.endIndex >= second.startIndex) return false
    val interveningOpcodes = subList(first.endIndex + 1, second.startIndex).map(Instruction::getOpcode)
    return matchesIntegrityComparison(
        first,
        second,
        comparison.registerA,
        comparison.registerB,
        interveningOpcodes,
    )
}

private fun List<Instruction>.isWindowFeatureTrap(index: Int): Boolean {
    val divide = getOrNull(index) as? TwoRegisterInstruction ?: return false
    val followingReference = (getOrNull(index + 1) as? ReferenceInstruction)?.reference as? MethodReference
    return matchesWindowFeatureTrap(divide.opcode, followingReference?.toString())
}

private fun List<Instruction>.isObfuscatedDivideTrap(index: Int): Boolean {
    val divide = getOrNull(index) as? TwoRegisterInstruction ?: return false
    val numerator = getOrNull(index - 1) as? OneRegisterInstruction ?: return false
    return matchesObfuscatedDivideTrap(
        divide.opcode,
        divide.registerA,
        numerator.opcode,
        numerator.registerA,
    )
}

internal fun matchesObfuscatedDivideTrap(
    divideOpcode: Opcode,
    divideResultRegister: Int,
    numeratorOpcode: Opcode,
    numeratorRegister: Int,
): Boolean = divideOpcode == Opcode.DIV_INT_2ADDR &&
    numeratorOpcode in setOf(Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST, Opcode.CONST_HIGH16) &&
    numeratorRegister == divideResultRegister

internal fun matchesWindowFeatureTrap(divideOpcode: Opcode, followingMethod: String?): Boolean =
    divideOpcode == Opcode.DIV_INT_2ADDR && followingMethod == REQUEST_WINDOW_FEATURE

private fun List<Instruction>.integerArrayReadEndingAt(index: Int): IntegrityArrayRead? {
    val read = getOrNull(index) as? ThreeRegisterInstruction ?: return null
    if (read.opcode != Opcode.AGET) return null

    val castIndex = (maxOf(1, index - 3) until index).lastOrNull { candidate ->
        val instruction = getOrNull(candidate)
        instruction?.opcode == Opcode.CHECK_CAST &&
            (instruction as? OneRegisterInstruction)?.registerA == read.registerB &&
            isIntegrityArrayIndexSetup(subList(candidate + 1, index).map(Instruction::getOpcode))
    } ?: return null
    val cast = getOrNull(castIndex) as? OneRegisterInstruction ?: return null
    val castType = ((cast as? ReferenceInstruction)?.reference as? TypeReference)?.type
    if (cast.opcode != Opcode.CHECK_CAST || cast.registerA != read.registerB || castType != "[I") {
        return null
    }

    val outerRead = getOrNull(castIndex - 1) as? ThreeRegisterInstruction ?: return null
    if (outerRead.opcode != Opcode.AGET_OBJECT || outerRead.registerA != read.registerB) return null

    return IntegrityArrayRead(
        startIndex = castIndex - 1,
        endIndex = index,
        resultRegister = read.registerA,
        sourceRegister = outerRead.registerB,
    )
}

internal data class IntegrityArrayRead(
    val startIndex: Int,
    val endIndex: Int,
    val resultRegister: Int,
    val sourceRegister: Int,
)

private val ALLOWED_INTERVENING_OPCODES = setOf(
    Opcode.CONST_4,
    Opcode.CONST_16,
    Opcode.CONST,
    Opcode.CONST_HIGH16,
)

internal fun isIntegrityArrayIndexSetup(opcodes: List<Opcode>): Boolean =
    opcodes.all(ALLOWED_INTERVENING_OPCODES::contains)

internal fun hasLifecycleTrapOpcode(opcodes: List<Opcode>): Boolean =
    opcodes.any(lifecycleTrapOpcodes::contains)

internal fun matchesIntegrityComparison(
    first: IntegrityArrayRead,
    second: IntegrityArrayRead,
    comparedRegisterA: Int,
    comparedRegisterB: Int,
    interveningOpcodes: List<Opcode>,
): Boolean = first.sourceRegister == second.sourceRegister &&
    setOf(first.resultRegister, second.resultRegister) == setOf(comparedRegisterA, comparedRegisterB) &&
    interveningOpcodes.all(ALLOWED_INTERVENING_OPCODES::contains)
