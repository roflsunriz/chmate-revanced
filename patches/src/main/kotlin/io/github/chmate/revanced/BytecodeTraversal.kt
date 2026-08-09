package io.github.chmate.revanced

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.instructionsOrNull
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction

internal fun <T> BytecodePatchContext.transformInstructions(
    match: (ClassDef, Method, Instruction, Int) -> T?,
    transform: (MutableMethod, T) -> Unit,
) {
    classDefs.flatMap { classDef ->
        classDef.methods.mapNotNull { method ->
            val matches = method.instructionsOrNull
                ?.mapIndexedNotNull { index, instruction -> match(classDef, method, instruction, index) }
                .orEmpty()
            if (matches.isEmpty()) null else method to matches
        }
    }.forEach { (method, matches) ->
        val mutableMethod = firstMethod(method)
        matches.asReversed().forEach { transform(mutableMethod, it) }
    }
}
