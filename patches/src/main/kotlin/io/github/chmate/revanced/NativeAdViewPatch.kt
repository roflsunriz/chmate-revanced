package io.github.chmate.revanced

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.instructionsOrNull
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

// Compose hosts measure this legacy banner View too. Collapse it before the first
// measurement and stop its factory before it creates an SDK or self-promotion View.
internal fun BytecodePatchContext.patchNativeAdViews() {
    classDefs.filter { classDef ->
        matchesNativeAdView(classDef.superclass, classDef.methods.flatMap { method ->
            method.instructionsOrNull?.mapNotNull { instruction ->
                ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string
            }?.toList().orEmpty()
        }.toSet())
    }.forEach { classDef ->
        classDef.methods.forEach methodLoop@ { method ->
            val instructions = method.instructionsOrNull?.toList() ?: return@methodLoop
            val mutableMethod = firstMethod(method)
            if (method.name == "<init>") {
                instructions.indices.filter { instructions[it].opcode == Opcode.RETURN_VOID }
                    .asReversed().forEach { index ->
                        mutableMethod.addInstructions(
                            index,
                            "invoke-static/range {p0 .. p0}, Lapp/revanced/extension/chmate/AdViewCollapser;->collapseNativeAd(Landroid/view/View;)V",
                        )
                    }
            } else if (method.returnType == "V" && instructions.any { instruction ->
                    ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string ==
                        "createAdView2 request=%s"
                }) {
                mutableMethod.addInstructions(0, "return-void")
            }
        }
    }
}

internal fun matchesNativeAdView(superclass: String?, strings: Set<String>): Boolean =
    superclass == "Landroid/widget/FrameLayout;" &&
        "setBannerAdRequest %s" in strings && "createAdView2 request=%s" in strings
