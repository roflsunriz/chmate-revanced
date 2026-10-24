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
import java.util.Locale
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
        val config = Config.getDefaultConfig().apply {
            frameworkDirectory = prepareFrameworkDirectory(apkFile)
        }
        val resourcesDecoder = ResourcesDecoder(config, ApkInfo(extFile))
        resourcesDecoder.loadMainPkg()
        val parser = object : AXmlResourceParser(resourcesDecoder.resTable) {
            private val stringBlockField = AXmlResourceParser::class.java
                .getDeclaredField("mStringBlock")
                .apply { isAccessible = true }

            override fun getAttributeName(index: Int): String {
                val decodedName = super.getAttributeName(index)
                if (decodedName.isNotEmpty() && !decodedName.startsWith("APKTOOL_MISSING_")) {
                    return decodedName
                }

                val resourceId = getAttributeNameResource(index)
                val resolvedName = runCatching { decodeFromResourceId(resourceId) }.getOrNull()
                return binaryXmlFallbackAttributeName(resourceId, resolvedName)
            }

            override fun getAttributeValue(index: Int): String {
                val decodedValue = super.getAttributeValue(index)
                if (decodedValue != null) return decodedValue

                val type = getAttributeValueType(index)
                val data = getAttributeValueData(index)
                if (type == 0x03) {
                    val stringBlock = stringBlockField.get(this) as? brut.androlib.res.decoder.StringBlock
                    stringBlock?.getString(data)?.let { return it }
                }
                return binaryXmlFallbackValue(type, data)
            }
        }
        decoder = ResXmlPullStreamDecoder(
            parser,
            resourcesDecoder.newXmlSerializer(),
        )
    }

    fun decode(bytes: ByteArray): String {
        val output = ByteArrayOutputStream()
        decoder.decode(ByteArrayInputStream(bytes), output)
        return removeUnnamedXmlAttributes(output.toString(Charsets.UTF_8))
    }
}

internal fun prepareFrameworkDirectory(
    apkFile: File,
    temporaryDirectory: String? = System.getProperty("java.io.tmpdir"),
): String {
    val candidates = buildList {
        if (!temporaryDirectory.isNullOrBlank()) add(File(temporaryDirectory, "framework"))
        apkFile.parentFile?.let { add(it.resolve(".apktool-framework")) }
    }
    return candidates.firstOrNull { directory ->
        (directory.isDirectory || directory.mkdirs()) && directory.canWrite()
    }?.absolutePath ?: throw IllegalStateException("Could not create a writable apktool framework directory")
}

internal fun removeUnnamedXmlAttributes(xml: String): String = Regex(
    "\\s+(?:[A-Za-z_][A-Za-z0-9_.-]*:)?=\"[^\"]*\"",
).replace(xml, "")

private val frameworkBinaryXmlAttributeNames = mapOf(
    0x01010000 to "theme",
    0x01010001 to "label",
    0x01010002 to "icon",
    0x01010003 to "name",
    0x01010006 to "permission",
    0x01010009 to "protectionLevel",
    0x0101000e to "enabled",
    0x01010012 to "taskAffinity",
    0x01010013 to "multiprocess",
    0x01010017 to "excludeFromRecents",
    0x01010018 to "authorities",
    0x0101001a to "initOrder",
    0x0101001b to "grantUriPermissions",
    0x0101001d to "launchMode",
    0x0101001e to "screenOrientation",
    0x0101001f to "configChanges",
    0x01010020 to "description",
    0x01010021 to "targetPackage",
    0x01010024 to "value",
    0x01010025 to "resource",
    0x0101002a to "path",
    0x0101002d to "action",
    0x0101002e to "data",
    0x0101002f to "targetClass",
    0x0101009c to "state_focused",
    0x0101009d to "state_window_focused",
    0x0101009e to "state_enabled",
    0x010100a0 to "state_checked",
    0x010100a1 to "state_selected",
    0x010100a7 to "state_pressed",
    0x010100af to "gravity",
    0x010100b2 to "entries",
    0x010100c4 to "orientation",
    0x010100d0 to "id",
    0x010100f4 to "layout_width",
    0x010100f5 to "layout_height",
    0x01010119 to "src",
    0x01010121 to "tint",
    0x0101013f to "minWidth",
    0x01010140 to "minHeight",
    0x01010150 to "hint",
    0x01010155 to "height",
    0x01010159 to "width",
    0x0101017b to "prompt",
    0x01010196 to "constantSize",
    0x01010199 to "drawable",
    0x0101019a to "shape",
    0x0101019b to "innerRadiusRatio",
    0x0101019c to "thicknessRatio",
    0x0101019f to "useLevel",
    0x010101a5 to "color",
    0x010101a8 to "radius",
    0x010101a9 to "topLeftRadius",
    0x010101aa to "topRightRadius",
    0x010101ab to "bottomLeftRadius",
    0x010101ac to "bottomRightRadius",
    0x010101ad to "left",
    0x010101ae to "top",
    0x010101af to "right",
    0x010101b0 to "bottom",
    0x010101b7 to "insetLeft",
    0x010101b8 to "insetRight",
    0x010101b9 to "insetTop",
    0x010101ba to "insetBottom",
    0x010101cd to "animation",
    0x010101e1 to "title",
    0x010101e8 to "key",
    0x010101e9 to "summary",
    0x010101ec to "dependency",
    0x010101ed to "defaultValue",
    0x010101ef to "summaryOn",
    0x010101f0 to "summaryOff",
    0x010101f8 to "entryValues",
    0x010101f9 to "ringtoneType",
    0x010101fa to "showDefault",
    0x01010201 to "tileMode",
    0x0101020a to "clipOrientation",
    0x0101020c to "minSdkVersion",
    0x0101021c to "versionName",
    0x01010220 to "inputType",
    0x0101022b to "windowSoftInputMode",
    0x01010250 to "updatePeriodMillis",
    0x01010251 to "initialLayout",
    0x01010270 to "targetSdkVersion",
    0x01010280 to "allowBackup",
    0x0101028e to "required",
    0x010102d3 to "hardwareAccelerated",
    0x010102fe to "state_activated",
    0x0101030d to "exitFadeDuration",
    0x0101031f to "alpha",
    0x0101035a to "largeHeap",
    0x0101036a to "stopWithTask",
    0x01010449 to "toId",
    0x0101044a to "fromId",
    0x01010477 to "tileModeX",
    0x010104eb to "fullBackupContent",
    0x010104ec to "usesCleartextTraffic",
    0x010104f6 to "resizeableActivity",
    0x01010505 to "directBootAware",
    0x01010528 to "shortcutId",
    0x01010529 to "shortcutShortLabel",
    0x0101052a to "shortcutLongLabel",
    0x01010545 to "appCategory",
    0x01010572 to "compileSdkVersion",
    0x01010573 to "compileSdkVersionCodename",
    0x0101057a to "appComponentFactory",
    0x0101059a to "hasFragileUserData",
    0x01010603 to "requestLegacyExternalStorage",
    0x01010616 to "gwpAsanMode",
    0x0101063e to "dataExtractionRules",
    0x0101066c to "enableOnBackInvokedCallback",
)

private val runtimeFrameworkBinaryXmlAttributeNames: Map<Int, String> by lazy {
    runCatching {
        Class.forName("android.R\$attr").fields.associate { field ->
            field.getInt(null) to field.name
        }
    }.getOrDefault(emptyMap())
}

internal fun runtimeFrameworkAttributeName(resourceId: Int): String? =
    runtimeFrameworkBinaryXmlAttributeNames[resourceId]

internal fun binaryXmlFallbackAttributeName(resourceId: Int, decodedName: String?): String =
    decodedName?.takeIf { it.isNotEmpty() && !it.startsWith("APKTOOL_MISSING_") }
        ?: runtimeFrameworkAttributeName(resourceId)
        ?: frameworkBinaryXmlAttributeNames[resourceId]
        ?: if (resourceId != 0) "APKTOOL_MISSING_${resourceId.toUInt().toString(16)}" else ""

internal fun binaryXmlFallbackValue(type: Int, data: Int): String = when (type) {
    0x01 -> "@0x%08x".format(Locale.ROOT, data)
    0x02 -> "?0x%08x".format(Locale.ROOT, data)
    0x04 -> Float.fromBits(data).toString()
    0x10 -> data.toString()
    0x11 -> "0x%08x".format(Locale.ROOT, data)
    0x12 -> if (data != 0) "true" else "false"
    in 0x1c..0x1f -> "#%08x".format(Locale.ROOT, data)
    else -> "0x%08x".format(Locale.ROOT, data)
}
