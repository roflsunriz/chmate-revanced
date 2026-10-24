package io.github.chmate.revanced

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.instructionsOrNull
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val SETTINGS_ACTIVITY_TYPE = "Ljp/syoboi/a2chMate/activity/SettingActivity;"
private const val NG_EDITOR_ACTIVITY_TYPE = "Ljp/syoboi/a2chMate/activity/NGEditorActivity;"
private const val BBS_URL_INFO_TYPE = "Ljp/syoboi/a2chMate/client/BBSUrlInfo;"
private const val SETTINGS_INTEGRATION =
    "Lapp/revanced/extension/chmate/SettingsIntegration;"
private const val ADVANCED_NG_INTEGRATION =
    "Lapp/revanced/extension/chmate/AdvancedNgIntegration;"
private const val IMAGE_BOARD_INTEGRATION =
    "Lapp/revanced/extension/chmate/ImageBoardIntegration;"

internal fun BytecodePatchContext.patchDeepIntegrations() {
    patchActivityEntry(SETTINGS_ACTIVITY_TYPE, SETTINGS_INTEGRATION)
    patchActivityEntry(NG_EDITOR_ACTIVITY_TYPE, ADVANCED_NG_INTEGRATION)
    patchImageBoardUrlParser()
}

private fun BytecodePatchContext.patchActivityEntry(activityType: String, integrationType: String) {
    val classDef = classDefs.singleOrNull { it.type == activityType }
        ?: throw PatchException("Could not find native ChMate activity: $activityType")
    val method = classDef.methods.singleOrNull { candidate ->
        candidate.name == "onCreate" &&
            candidate.returnType == "V" &&
            candidate.parameterTypes.map(CharSequence::toString) == listOf("Landroid/os/Bundle;") &&
            !AccessFlags.STATIC.isSet(candidate.accessFlags)
    } ?: throw PatchException("Could not find onCreate in $activityType")
    val mutableMethod = firstMethod(method)
    val returnIndexes = mutableMethod.instructionsOrNull.orEmpty()
        .mapIndexedNotNull { index, instruction ->
            index.takeIf { instruction.opcode == Opcode.RETURN_VOID }
        }
    if (returnIndexes.isEmpty()) throw PatchException("onCreate has no return in $activityType")
    returnIndexes.asReversed().forEach { index ->
        mutableMethod.addInstructions(
            index,
            "invoke-static/range {p0 .. p0}, $integrationType->attach(Landroid/app/Activity;)V",
        )
    }
}

private fun BytecodePatchContext.patchImageBoardUrlParser() {
    if (classDefs.none { it.type == BBS_URL_INFO_TYPE }) {
        throw PatchException("Could not find ChMate BBSUrlInfo")
    }
    val contextCandidates = classDefs.flatMap { classDef ->
        classDef.methods.filter { method ->
            method.returnType == BBS_URL_INFO_TYPE &&
                method.parameterTypes.map(CharSequence::toString) ==
                    listOf("Landroid/content/Context;", "Ljava/lang/String;")
        }
    }
    val parser = when {
        contextCandidates.size == 1 -> contextCandidates.single()
        contextCandidates.isEmpty() -> {
            val coreCandidates = classDefs.flatMap { classDef ->
                classDef.methods.filter { method ->
                    method.returnType == BBS_URL_INFO_TYPE &&
                        method.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;") &&
                        method.instructionsOrNull?.any { instruction ->
                            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            reference?.definingClass == "Landroid/net/Uri;" &&
                                reference.name == "parse" &&
                                reference.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;")
                        } == true
                }
            }
            coreCandidates.singleOrNull() ?: throw PatchException(
                "Could not identify legacy ChMate URL parser: ${coreCandidates.size} candidates",
            )
        }
        else -> throw PatchException(
            "Could not identify ChMate URL parser: ${contextCandidates.size} candidates",
        )
    }
    val stringParameter = parser.parameterTypes.indexOfFirst { it.toString() == "Ljava/lang/String;" }
    val parameterRegister = stringParameter + if (AccessFlags.STATIC.isSet(parser.accessFlags)) 0 else 1
    val mutableMethod = firstMethod(parser)
    mutableMethod.addInstructions(
        0,
        """
            invoke-static/range {p$parameterRegister .. p$parameterRegister}, $IMAGE_BOARD_INTEGRATION->rewriteInputUrl(Ljava/lang/String;)Ljava/lang/String;
            move-result-object p$parameterRegister
        """.trimIndent(),
    )
}
