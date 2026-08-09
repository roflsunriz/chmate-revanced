package io.github.chmate.revanced

import app.revanced.patcher.extensions.instructionsOrNull
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
            if (failureBranchIndices.isEmpty() && windowFeatureTrapIndices.isEmpty()) {
                null
            } else {
                method to IntegrityPatchIndices(failureBranchIndices, windowFeatureTrapIndices)
            }
        }
    }.forEach { (method, indices) ->
        val mutableMethod = firstMethod(method)
        val originalInstructions = method.instructionsOrNull!!.toList()
        indices.failureBranches.forEach { index -> mutableMethod.replaceInstruction(index, "nop") }
        indices.windowFeatureTraps.forEach { index ->
            val divide = originalInstructions[index] as TwoRegisterInstruction
            mutableMethod.replaceInstruction(index, "const/16 v${divide.registerA}, 0x1")
        }
    }
}

private data class IntegrityPatchIndices(
    val failureBranches: List<Int>,
    val windowFeatureTraps: List<Int>,
)

private fun List<Instruction>.isIntegrityFailureBranch(index: Int): Boolean {
    val comparison = getOrNull(index) as? TwoRegisterInstruction ?: return false
    if (comparison.opcode != Opcode.IF_NE) return false

    val reads = (maxOf(2, index - 12) until index).mapNotNull(::integerArrayReadEndingAt)
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

internal fun matchesWindowFeatureTrap(divideOpcode: Opcode, followingMethod: String?): Boolean =
    divideOpcode == Opcode.DIV_INT_2ADDR && followingMethod == REQUEST_WINDOW_FEATURE

private fun List<Instruction>.integerArrayReadEndingAt(index: Int): IntegrityArrayRead? {
    val read = getOrNull(index) as? ThreeRegisterInstruction ?: return null
    if (read.opcode != Opcode.AGET) return null

    val cast = getOrNull(index - 1) as? OneRegisterInstruction ?: return null
    val castType = ((cast as? ReferenceInstruction)?.reference as? TypeReference)?.type
    if (cast.opcode != Opcode.CHECK_CAST || cast.registerA != read.registerB || castType != "[I") {
        return null
    }

    val outerRead = getOrNull(index - 2) as? ThreeRegisterInstruction ?: return null
    if (outerRead.opcode != Opcode.AGET_OBJECT || outerRead.registerA != read.registerB) return null

    return IntegrityArrayRead(
        startIndex = index - 2,
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

internal fun matchesIntegrityComparison(
    first: IntegrityArrayRead,
    second: IntegrityArrayRead,
    comparedRegisterA: Int,
    comparedRegisterB: Int,
    interveningOpcodes: List<Opcode>,
): Boolean = first.sourceRegister == second.sourceRegister &&
    setOf(first.resultRegister, second.resultRegister) == setOf(comparedRegisterA, comparedRegisterB) &&
    interveningOpcodes.all(ALLOWED_INTERVENING_OPCODES::contains)
