package io.github.chmate.revanced

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.instructionsOrNull
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val AUTO_CHECK_WORK = "Ljp/syoboi/a2chMate/autocheck/AutoCheckWork;"
private const val LEGACY_RELOAD_TASK = "Ljp/syoboi/a2chMate/task/ReloadBookmarkTask2;"
private const val PARALLEL_REFRESH_BRIDGE =
    "Lapp/revanced/extension/chmate/ParallelRefreshBridge;"

internal fun BytecodePatchContext.patchParallelRefresh() {
    val classDef = classDefs.singleOrNull { it.type == AUTO_CHECK_WORK }
    if (classDef == null) {
        patchLegacyParallelRefresh()
        return
    }
    val candidates = classDef.methods.filter { method ->
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            (method.returnType.startsWith("L") || method.returnType == "I") &&
            method.parameterTypes.size == 3 &&
            method.parameterTypes[1].toString() in setOf("Ljava/util/List;", "Ljava/util/ArrayList;") &&
            method.parameterTypes[2].toString() == "Z" &&
            method.instructionsOrNull?.any { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                reference?.definingClass == "Ljava/lang/Thread;" && reference.name == "sleep"
            } == true
    }
    if (candidates.size != 1) {
        throw PatchException("Could not identify AutoCheckWork check method: ${candidates.size} candidates")
    }
    val method = candidates.single()
    val implementation = method.implementation
        ?: throw PatchException("AutoCheckWork check method has no implementation")
    val parameterWords = 1 + method.parameterTypes.sumOf { type ->
        if (type.toString() == "J" || type.toString() == "D") 2 else 1
    }
    if (implementation.registerCount <= parameterWords) {
        throw PatchException("AutoCheckWork check method has no local register for bridge result")
    }
    injectParallelBridge(method, """
            invoke-static/range {p0 .. p3}, $PARALLEL_REFRESH_BRIDGE->run(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/List;Z)Ljava/lang/Object;
        """.trimIndent())
}

private fun BytecodePatchContext.patchLegacyParallelRefresh() {
    val classDef = classDefs.singleOrNull { it.type == LEGACY_RELOAD_TASK }
        ?: return
    val candidates = classDef.methods.filter { method ->
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.returnType == "I" &&
            method.parameterTypes.size == 4 &&
            method.parameterTypes[2].toString() in setOf("Ljava/util/List;", "Ljava/util/ArrayList;") &&
            method.parameterTypes[3].toString() == "Z" &&
            method.instructionsOrNull?.any { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                reference?.definingClass == "Ljava/lang/Thread;" && reference.name == "sleep"
            } == true
    }
    if (candidates.size != 1) {
        throw PatchException("Could not identify legacy bookmark reload method: ${candidates.size} candidates")
    }
    injectParallelBridge(candidates.single(), """
        invoke-static/range {p0 .. p4}, $PARALLEL_REFRESH_BRIDGE->runLegacy(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/util/List;Z)Ljava/lang/Object;
    """.trimIndent())
}

private fun BytecodePatchContext.injectParallelBridge(
    method: com.android.tools.smali.dexlib2.iface.Method,
    invocation: String,
) {
    val implementation = method.implementation
        ?: throw PatchException("Refresh method has no implementation")
    val parameterWords = 1 + method.parameterTypes.sumOf { type ->
        if (type.toString() == "J" || type.toString() == "D") 2 else 1
    }
    if (implementation.registerCount <= parameterWords) {
        throw PatchException("Refresh method has no local register for bridge result")
    }
    val returnInstructions = if (method.returnType == "I") {
        """
            check-cast v0, Ljava/lang/Integer;
            invoke-virtual {v0}, Ljava/lang/Integer;->intValue()I
            move-result v0
            return v0
        """.trimIndent()
    } else {
        """
            check-cast v0, ${method.returnType}
            return-object v0
        """.trimIndent()
    }
    val mutableMethod = firstMethod(method)
    mutableMethod.addInstructions(0, """
        $invocation
        move-result-object v0
        if-eqz v0, :revanced_parallel_continue
        $returnInstructions
        :revanced_parallel_continue
    """.trimIndent())
}
