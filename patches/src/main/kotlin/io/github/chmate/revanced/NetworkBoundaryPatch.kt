package io.github.chmate.revanced

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_PREFIX = "Lapp/revanced/extension/chmate/"
private const val AD_BLOCKER = "${EXTENSION_PREFIX}AdBlocker;"
private const val USER_AGENT = "${EXTENSION_PREFIX}UserAgentOverride;"
private const val WEB_VIEW = "${EXTENSION_PREFIX}WebViewBridge;"

private sealed class Rewrite(open val index: Int) {
    data class Replace(override val index: Int, val smali: String) : Rewrite(index)
    data class Insert(override val index: Int, val smali: String) : Rewrite(index)
}

internal fun BytecodePatchContext.patchNetworkBoundaries() {
    transformInstructions(
        match = { classDef, _, instruction, index ->
            if (classDef.type.startsWith(EXTENSION_PREFIX)) return@transformInstructions null
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                ?: return@transformInstructions null
            rewrite(index, instruction, reference, AdElementClassifier.isAdSdkClass(classDef.type))
        },
        transform = { method, rewrite ->
            when (rewrite) {
                is Rewrite.Replace -> method.replaceInstruction(rewrite.index, rewrite.smali)
                is Rewrite.Insert -> method.addInstructions(rewrite.index, rewrite.smali)
            }
        },
    )
}

private fun rewrite(
    index: Int,
    instruction: Instruction,
    reference: MethodReference,
    blockAll: Boolean,
): Rewrite? {
    val registers = instruction.argumentRegisters() ?: return null
    val signature = reference.toString()

    return when (signature) {
        "Ljava/net/InetAddress;->getAllByName(Ljava/lang/String;)[Ljava/net/InetAddress;" ->
            Rewrite.Replace(index, registers.staticInvoke(instruction, "$AD_BLOCKER->${if (blockAll) "blockGetAllByName" else "getAllByName"}(Ljava/lang/String;)[Ljava/net/InetAddress;"))

        "Ljava/net/InetAddress;->getByName(Ljava/lang/String;)Ljava/net/InetAddress;" ->
            Rewrite.Replace(index, registers.staticInvoke(instruction, "$AD_BLOCKER->${if (blockAll) "blockGetByName" else "getByName"}(Ljava/lang/String;)Ljava/net/InetAddress;"))

        "Ljava/net/URL;->openConnection()Ljava/net/URLConnection;" ->
            Rewrite.Replace(index, registers.staticInvoke(instruction, "$AD_BLOCKER->${if (blockAll) "blockOpenConnection" else "openConnection"}(Ljava/net/URL;)Ljava/net/URLConnection;"))

        "Ljava/net/URL;->openConnection(Ljava/net/Proxy;)Ljava/net/URLConnection;" ->
            Rewrite.Replace(index, registers.staticInvoke(instruction, "$AD_BLOCKER->${if (blockAll) "blockOpenConnection" else "openConnection"}(Ljava/net/URL;Ljava/net/Proxy;)Ljava/net/URLConnection;"))

        "Ljava/lang/System;->getProperty(Ljava/lang/String;)Ljava/lang/String;" ->
            Rewrite.Replace(index, registers.staticInvoke(instruction, "$USER_AGENT->getSystemProperty(Ljava/lang/String;)Ljava/lang/String;"))

        "Landroid/webkit/WebSettings;->getDefaultUserAgent(Landroid/content/Context;)Ljava/lang/String;" ->
            Rewrite.Replace(index, registers.staticInvoke(instruction, "$WEB_VIEW->getDefaultUserAgent(Landroid/content/Context;)Ljava/lang/String;"))

        "Landroid/webkit/WebSettings;->setUserAgentString(Ljava/lang/String;)V" ->
            Rewrite.Replace(index, registers.staticInvoke(instruction, "$WEB_VIEW->setUserAgentString(Landroid/webkit/WebSettings;Ljava/lang/String;)V"))

        "Landroid/webkit/WebView;->loadUrl(Ljava/lang/String;)V" ->
            Rewrite.Replace(index, registers.staticInvoke(instruction, "$WEB_VIEW->${if (blockAll) "blockLoadUrl" else "loadUrl"}(Landroid/webkit/WebView;Ljava/lang/String;)V"))

        "Landroid/webkit/WebView;->loadUrl(Ljava/lang/String;Ljava/util/Map;)V" ->
            Rewrite.Replace(index, registers.staticInvoke(instruction, "$WEB_VIEW->${if (blockAll) "blockLoadUrl" else "loadUrl"}(Landroid/webkit/WebView;Ljava/lang/String;Ljava/util/Map;)V"))

        else -> headerRewrite(index, instruction, reference, registers)
            ?: urlRewrite(index, instruction, reference, registers, blockAll)
    }
}

private fun headerRewrite(
    index: Int,
    instruction: Instruction,
    reference: MethodReference,
    registers: List<Int>,
    blockAll: Boolean,
): Rewrite? {
    if (reference.name !in setOf("header", "addHeader", "setHeader", "setRequestProperty", "addRequestProperty")) {
        return null
    }
    if (reference.parameterTypes.map { it.toString() } != listOf("Ljava/lang/String;", "Ljava/lang/String;")) {
        return null
    }

    val parameterOffset = if (instruction.isStaticInvoke()) 0 else 1
    val key = registers.getOrNull(parameterOffset) ?: return null
    val value = registers.getOrNull(parameterOffset + 1) ?: return null
    if (value > 255) return null

    val invoke = if (instruction is RegisterRangeInstruction) "invoke-static/range { v$key .. v$value }" else "invoke-static { v$key, v$value }"
    return Rewrite.Insert(
        index,
        """
            $invoke, $USER_AGENT->overrideHeader(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
            move-result-object v$value
        """.trimIndent(),
    )
}

private fun urlRewrite(
    index: Int,
    instruction: Instruction,
    reference: MethodReference,
    registers: List<Int>,
): Rewrite? {
    if (reference.name !in setOf("url", "newUrlRequestBuilder")) return null
    val firstParameter = reference.parameterTypes.firstOrNull()?.toString() ?: return null
    if (firstParameter != "Ljava/lang/String;") return null

    val parameterOffset = if (instruction.isStaticInvoke()) 0 else 1
    val value = registers.getOrNull(parameterOffset) ?: return null
    if (value > 255) return null
    val invoke = if (instruction is RegisterRangeInstruction) "invoke-static/range { v$value .. v$value }" else "invoke-static { v$value }"

    return Rewrite.Insert(
        index,
        """
            $invoke, $AD_BLOCKER->${if (blockAll) "blockNetworkUrl" else "sanitizeNetworkUrl"}(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v$value
        """.trimIndent(),
    )
}

private fun Instruction.argumentRegisters(): List<Int>? = when (this) {
    is Instruction35c -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    else -> null
}

private fun Instruction.isStaticInvoke() = opcode == Opcode.INVOKE_STATIC || opcode == Opcode.INVOKE_STATIC_RANGE

private fun List<Int>.smaliRange() = joinToString(", ") { "v$it" }

private fun List<Int>.staticInvoke(instruction: Instruction, target: String): String {
    val mnemonic = if (instruction is RegisterRangeInstruction) "invoke-static/range" else "invoke-static"
    val arguments = if (instruction is RegisterRangeInstruction && isNotEmpty()) {
        "v${first()} .. v${last()}"
    } else {
        smaliRange()
    }
    return "$mnemonic { $arguments }, $target"
}
