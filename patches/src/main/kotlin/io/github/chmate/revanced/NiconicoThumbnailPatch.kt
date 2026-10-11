package io.github.chmate.revanced

import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.instructionsOrNull
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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
