package io.github.chmate.revanced

import brut.androlib.Config
import brut.androlib.apk.ApkInfo
import brut.androlib.res.ResourcesDecoder
import brut.androlib.res.decoder.AXmlResourceParser
import brut.androlib.res.decoder.ResXmlPullStreamDecoder
import brut.directory.ExtFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

internal data class ObfuscatedFileResource(
    val type: String,
    val name: String,
    val sourcePath: String,
    val qualifiers: String,
)

internal object ObfuscatedFileResources {
    private val fileTypes = setOf(
        "anim", "animator", "color", "drawable", "font", "interpolator", "layout",
        "menu", "mipmap", "navigation", "raw", "transition", "xml",
    )
    private val item = Regex("""<item\b([^>]*)>([^<]*)</item>""")
    private val attribute = Regex("""\b([A-Za-z0-9_-]+)="([^"]*)"""")

    fun find(xml: String, valuesDirectoryName: String): List<ObfuscatedFileResource> = item.findAll(xml).mapNotNull { match ->
        val attributes = attribute.findAll(match.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
        val type = attributes["type"] ?: return@mapNotNull null
        val name = attributes["name"] ?: return@mapNotNull null
        val sourcePath = match.groupValues[2].trim()
        if (type !in fileTypes || sourcePath.isEmpty() || sourcePath.first() in setOf('@', '?', '#')) {
            return@mapNotNull null
        }
        ObfuscatedFileResource(type, name, sourcePath, valuesDirectoryName.removePrefix("values"))
    }.toList()

    fun removeAliases(xml: String): String = item.replace(xml) { match ->
        if (find(match.value, "values").isEmpty()) match.value else ""
    }

    fun materialize(
        apkFile: File,
        resourceRoot: File,
        aliases: List<ObfuscatedFileResource>,
        numericSymbols: Set<String>,
        resourceIdReferences: Map<String, String>,
    ) {
        if (aliases.isEmpty()) return
        val binaryXmlDecoder = BinaryXmlDecoder(apkFile)

        ZipFile(apkFile).use { zip ->
            aliases.forEach { alias ->
                val entry = zip.getEntry(alias.sourcePath)
                    ?: error("Obfuscated resource is missing from APK: ${alias.sourcePath}")
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val extension = detectExtension(alias, bytes)
                val directory = resourceRoot.resolve("${alias.type}${alias.qualifiers}").apply { mkdirs() }
                val output = directory.resolve("${ResourceNameSanitizer.sanitizeResourceName(alias.name)}.$extension")

                if (extension == "xml" && isBinaryXml(bytes)) {
                    val decoded = binaryXmlDecoder.decode(bytes)
                    output.writeText(
                        ResourceNameSanitizer.sanitizeXml(decoded, numericSymbols, resourceIdReferences),
                    )
                } else {
                    output.writeBytes(bytes)
                }
            }
        }
    }

    private fun detectExtension(alias: ObfuscatedFileResource, bytes: ByteArray): String = when {
        isBinaryXml(bytes) -> "xml"
        bytes.startsWith(0x89, 0x50, 0x4e, 0x47) -> "png"
        bytes.startsWith(0xff, 0xd8) -> "jpg"
        bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> "webp"
        alias.type == "raw" -> alias.sourcePath.substringAfterLast('.', "bin").lowercase()
        else -> error("Unsupported obfuscated resource format: ${alias.sourcePath}")
    }

    private fun isBinaryXml(bytes: ByteArray) = bytes.startsWith(0x03, 0x00, 0x08, 0x00)

    private fun ByteArray.startsWith(vararg prefix: Int) =
        size >= prefix.size && prefix.indices.all { index -> this[index].toInt() and 0xff == prefix[index] }
}

private class BinaryXmlDecoder(apkFile: File) {
    private val decoder: ResXmlPullStreamDecoder

    init {
        val extFile = ExtFile(apkFile)
        val resourcesDecoder = ResourcesDecoder(Config.getDefaultConfig(), ApkInfo(extFile))
        resourcesDecoder.loadMainPkg()
        decoder = ResXmlPullStreamDecoder(
            AXmlResourceParser(resourcesDecoder.resTable),
            resourcesDecoder.newXmlSerializer(),
        )
    }

    fun decode(bytes: ByteArray): String {
        val output = ByteArrayOutputStream()
        decoder.decode(ByteArrayInputStream(bytes), output)
        return output.toString(Charsets.UTF_8)
    }
}
