package io.github.chmate.revanced

import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.instructionsOrNull
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val RESOLVER = "Lapp/revanced/extension/chmate/NiconicoThumbnailResolver;"
private const val INPUT_STREAM_READER =
    "Ljava/io/InputStreamReader;-><init>(Ljava/io/InputStream;Ljava/lang/String;)V"
private const val PATTERN_COMPILE =
    "Ljava/util/regex/Pattern;->compile(Ljava/lang/String;I)Ljava/util/regex/Pattern;"

internal fun BytecodePatchContext.patchNiconicoThumbnails() {
    val candidates = classDefs.flatMap { classDef ->
        classDef.methods.filter { method ->
            AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "Ljava/lang/String;" &&
                method.parameterTypes.size == 3 &&
                method.parameterTypes[1].toString() == "Ljava/lang/String;" &&
                method.implementation?.registerCount?.let { it > 3 } == true &&
                method.instructionsOrNull?.mapNotNull { instruction ->
                    ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.toString()
                }?.toSet()?.let { INPUT_STREAM_READER in it && PATTERN_COMPILE in it } == true
        }
    }
    if (candidates.size != 1) {
        throw PatchException("Could not identify the thumbnail metadata reader: ${candidates.size} candidates")
    }
    val method = candidates.single()
    val mutableMethod = firstMethod(method)
    val firstInstruction = mutableMethod.implementation!!.instructions.first()
    mutableMethod.addInstructionsWithLabels(
        0,
        """
            invoke-static/range {p1 .. p1}, $RESOLVER->resolve(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v0
            if-eqz v0, :chmate_revanced_original_thumbnail
            return-object v0
        """.trimIndent(),
        ExternalLabel("chmate_revanced_original_thumbnail", firstInstruction),
    )
}

internal fun BytecodePatchContext.patchNiconicoVideoLinks() {
    var updatedPatterns = 0
    transformInstructions(
        match = { classDef, _, instruction, index ->
            if (classDef.type.startsWith("Lapp/revanced/extension/")) return@transformInstructions null
            if (instruction.opcode != Opcode.CONST_STRING && instruction.opcode != Opcode.CONST_STRING_JUMBO) {
                return@transformInstructions null
            }
            val original = ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string
                ?: return@transformInstructions null
            val expanded = expandNiconicoVideoPattern(original) ?: return@transformInstructions null
            val register = (instruction as? OneRegisterInstruction)?.registerA ?: return@transformInstructions null
            updatedPatterns++
            Triple(index, register, expanded)
        },
        transform = { method, (index, register, pattern) ->
            val escaped = pattern.replace("\\", "\\\\").replace("\"", "\\\"")
            method.replaceInstruction(index, "const-string/jumbo v$register, \"$escaped\"")
        },
    )
    if (updatedPatterns < 2) {
        throw PatchException("Could not update both Niconico video link patterns: $updatedPatterns")
    }
}

internal fun expandNiconicoVideoPattern(original: String): String? {
    val watch = "www\\.nicovideo\\.jp/watch/sm[0-9]+"
    val short = "nico\\.ms/sm[0-9]+"
    if (watch !in original || short !in original) return null
    return original
        .replace(watch, "(?:www|sp)\\.nicovideo\\.jp/watch/(?:sm|nm|so)[0-9]+")
        .replace(short, "nico\\.ms/(?:sm|nm|so)[0-9]+")
}
