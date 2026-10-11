package io.github.chmate.revanced

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.instructionsOrNull
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val METADATA_BRIDGE = "Lapp/revanced/extension/chmate/VideoMetadataBridge;"
private const val VIEW_GROUP = "Landroid/view/ViewGroup;"
private const val IMAGE_LINKS = "[Ljava/lang/CharSequence;"

internal fun BytecodePatchContext.patchVideoMetadata() {
    val candidates = classDefs.mapNotNull { classDef ->
        if (classDef.superclass != VIEW_GROUP) return@mapNotNull null
        val links = classDef.methods.singleOrNull { method ->
            method.parameterTypes.map(CharSequence::toString) == listOf(IMAGE_LINKS, "Z", "Z") &&
                method.returnType == "V" &&
                method.instructionsOrNull?.mapNotNull { instruction ->
                    ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.name
                }?.toSet()?.let { "addView" in it && "removeView" in it } == true
        } ?: return@mapNotNull null
        val measure = classDef.methods.singleOrNull { it.name == "onMeasure" && it.parameterTypes.map(CharSequence::toString) == listOf("I", "I") }
            ?: return@mapNotNull null
        val layout = classDef.methods.singleOrNull { it.name == "onLayout" && it.parameterTypes.map(CharSequence::toString) == listOf("Z", "I", "I", "I", "I") }
            ?: return@mapNotNull null
        Triple(links, measure, layout)
    }
    if (candidates.size != 1) {
        throw PatchException("Could not identify the response image row: ${candidates.size} candidates")
    }

    val (links, measure, layout) = candidates.single()
    firstMethod(links).addInstructions(
        0,
        "invoke-static/range {p0 .. p1}, $METADATA_BRIDGE->setLinks($VIEW_GROUP$IMAGE_LINKS)V",
    )

    val mutableMeasure = firstMethod(measure)
    val measureInstructions = mutableMeasure.implementation!!.instructions
    val dimensionCallIndex = measureInstructions.indexOfLast { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference?.name == "setMeasuredDimension" &&
            reference.parameterTypes.map(CharSequence::toString) == listOf("I", "I") &&
            reference.returnType == "V"
    }
    if (dimensionCallIndex < 0) throw PatchException("Response image row has no dimension call")
    val argumentRegisters = measureInstructions[dimensionCallIndex].argumentRegisters()
        ?: throw PatchException("Unsupported response image row dimension call")
    if (argumentRegisters.size != 3) throw PatchException("Unexpected response image row dimension arguments")
    val localCount = mutableMeasure.implementation!!.registerCount - 3
    val scratch = (0 until minOf(localCount, 16)).filter { it !in argumentRegisters }.take(2)
    if (scratch.size != 2) throw PatchException("Response image row has too few scratch registers")
    val receiverScratch = scratch[0]
    val heightScratch = scratch[1]
    val width = argumentRegisters[1]
    val height = argumentRegisters[2]
    mutableMeasure.addInstructions(
        dimensionCallIndex,
        """
            move-object/from16 v$receiverScratch, p0
            move/from16 v$heightScratch, v$width
            invoke-static {v$receiverScratch, v$heightScratch}, $METADATA_BRIDGE->extraHeight(${VIEW_GROUP}I)I
            move-result v$heightScratch
            add-int v$height, v$height, v$heightScratch
        """.trimIndent(),
    )

    firstMethod(layout).addInstructions(
        0,
        "invoke-static/range {p0 .. p0}, $METADATA_BRIDGE->layout($VIEW_GROUP)V",
    )
}

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.argumentRegisters(): List<Int>? = when (this) {
    is Instruction35c -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    else -> null
}
