package io.github.chmate.revanced

internal object ResourceNameSanitizer {
    private val numericNameAttribute = Regex("""(\bname=")([0-9]+)(")""")
    private val numericTypedReference = Regex("""([@?](?:\+)?(?:\*)?[A-Za-z0-9_.:]+/)([0-9]+)(?![A-Za-z0-9_])""")
    private val numericStyleParent = Regex("""(\bparent=")([0-9]+)(")""")
    private val numericFileName = Regex("""^([0-9]+)(\..+)?$""")
    private val numericAttributeSymbol = Regex("""<(?:enum|flag)\b[^>]*\bname="([0-9]+)"""")
    private val numericValueToken = Regex("""(?<![A-Za-z0-9_])(213[0-9]{7})(?![A-Za-z0-9_])""")
    private val missingValue = Regex("""(?:APKTOOL_MISSING_0x|missing_)([0-9A-Fa-f]+)""")
    private val dummyResourceName = Regex("""APKTOOL_DUMMYVAL_0x([0-9A-Fa-f]+)""")
    private val rawResourceReference = Regex("""@([0-9]{8,10})(?![0-9])""")
    private val rawAttributeReference = Regex(
        """\?(?:(?:android:)?attr/)?([0-9]{8,10})(?![0-9])""",
    )
    private val rawHexAttributeReference = Regex("""\?0x([0-9A-Fa-f]{8})(?![0-9A-Fa-f])""")
    private val frameworkAttributeReferences = mapOf(
        0x01010042 to "?android:attr/textAppearanceSmall",
        0x0101005b to "?android:attr/windowTitleStyle",
        0x01010078 to "?android:attr/progressBarStyleHorizontal",
        0x01010079 to "?android:attr/progressBarStyleSmall",
        0x01010086 to "?android:attr/dropDownItemStyle",
        0x01010208 to "?android:attr/listSeparatorTextViewStyle",
        0x01010429 to "?android:attr/colorControlNormal",
    )
    private val numericXmlAttributeName = Regex(
        """(\s(?:[A-Za-z_][A-Za-z0-9_.-]*:)?)(213[0-9]{7})(=\")""",
    )
    private val shortColor = Regex("""(?<==\")#([0-9A-Fa-f])(?=\")""")
    private val frameworkResourceReferences = mapOf(
        0x0106000b to "@android:color/white",
        0x0106000c to "@android:color/black",
        0x010a0004 to "@android:anim/accelerate_decelerate_interpolator",
        0x010a0005 to "@android:anim/accelerate_interpolator",
        0x010a0006 to "@android:anim/decelerate_interpolator",
        0x010a000b to "@android:anim/linear_interpolator",
        0x010c0000 to "@android:interpolator/accelerate_quad",
        0x010c0001 to "@android:interpolator/decelerate_quad",
        0x010c000b to "@android:interpolator/linear",
        0x010c000d to "@android:interpolator/fast_out_slow_in",
        0x010e0000 to "@android:integer/config_shortAnimTime",
    )
    private val xmlAttribute = Regex("""(?:[A-Za-z0-9_.-]+:)?([A-Za-z0-9_.-]+)="([^"]*)"""")
    private val styleItem = Regex("""<item\b[^>]*\bname="([^"]+)"[^>]*>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    private val attributeDefinition = Regex(
        """<attr\b(?=[^>]*\bname="([^"]+)")[^>]*(?<!/)>(.*?)</attr>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val publicAttribute = Regex(
        """<public\b[^>]*\btype="attr"[^>]*\bname="([^"]+)"[^>]*\bid="0x([0-9A-Fa-f]+)"[^>]*/>""",
    )
    private val unqualifiedXmlAttribute = Regex(
        """(\s)([A-Za-z_][A-Za-z0-9_.-]*)(=")""",
    )
    private val rootElement = Regex("""(<\?xml[^>]*>\s*)?<([A-Za-z_][A-Za-z0-9_.${'$'}-]*)""")

    fun numericAttributeSymbols(attrsXml: String): Set<String> = numericAttributeSymbol.findAll(attrsXml)
        .map { it.groupValues[1] }
        .toSet()

    fun applicationAttributeNames(attrsXml: String): Set<String> = Regex(
        """<attr\b[^>]*\bname="([^"]+)"""",
    ).findAll(attrsXml).map { it.groupValues[1] }.toSet()

    fun attributeResourceIds(publicXml: String): Map<String, Int> = publicAttribute.findAll(publicXml)
        .associate { match -> match.groupValues[1] to match.groupValues[2].toLong(16).toInt() }

    fun qualifyApplicationAttributes(xml: String, attributeNames: Set<String>): String {
        var changed = false
        var result = unqualifiedXmlAttribute.replace(xml) { match ->
            val name = match.groupValues[2]
            if (name !in attributeNames || isFrameworkSyntaxAttribute(xml, match.range.first, name)) {
                match.value
            } else {
                changed = true
                "${match.groupValues[1]}app:$name${match.groupValues[3]}"
            }
        }
        if (!changed || "xmlns:app=" in result) return result

        val match = rootElement.find(result) ?: return result
        return result.replaceRange(
            match.range,
            "${match.groupValues[1]}<${match.groupValues[2]} xmlns:app=\"http://schemas.android.com/apk/res-auto\"",
        )
    }

    private fun isFrameworkSyntaxAttribute(xml: String, attributeOffset: Int, name: String): Boolean {
        if (name != "layout") return false

        val tagStart = xml.lastIndexOf('<', attributeOffset)
        val precedingTagEnd = xml.lastIndexOf('>', attributeOffset)
        if (tagStart <= precedingTagEnd) return false

        return xml.regionMatches(tagStart + 1, "include", 0, "include".length) &&
            xml.getOrNull(tagStart + 1 + "include".length)?.let { it.isWhitespace() || it == '>' } != false
    }

    fun sanitizeXml(
        xml: String,
        numericSymbols: Set<String> = emptySet(),
        resourceIdReferences: Map<String, String> = emptyMap(),
    ): String = xml
        .replace(numericXmlAttributeName) { match ->
            val reference = resourceIdReferences[match.groupValues[2]]
            val name = reference?.takeIf { it.startsWith("@attr/") }?.substringAfter('/')
                ?: return@replace match.value
            "${match.groupValues[1]}$name${match.groupValues[3]}"
        }
        .replace(rawHexAttributeReference) { match ->
            resolveRawAttributeReference(match.groupValues[1].toLong(16), resourceIdReferences)
        }
        .replace(rawAttributeReference) { match ->
            resolveRawAttributeReference(match.groupValues[1].toLong(), resourceIdReferences)
        }
        .replace(shortColor) { match -> "#0000000${match.groupValues[1]}" }
        .replace(dummyResourceName) { match -> "res_${match.groupValues[1].lowercase()}" }
        .replace(numericNameAttribute) { match -> prefixed(match) }
        .replace(numericTypedReference) { match -> prefixed(match) }
        .replace(numericStyleParent) { match -> prefixed(match) }
        .replace(numericValueToken) { match ->
            if (match.groupValues[1] in numericSymbols) "res_${match.groupValues[1]}" else match.value
        }
        .replace(missingValue) { match -> "missing_${match.groupValues[1].lowercase()}" }
        .replace(rawResourceReference) { match ->
            resourceIdReferences[match.groupValues[1]]
                ?: frameworkResourceReferences[match.groupValues[1].toLong().toInt()]
                ?: "@0x${match.groupValues[1].toLong().toString(16).padStart(8, '0')}"
        }
        .let(::sanitizeFrameworkAnimationEnums)

    private fun resolveRawAttributeReference(id: Long, resourceIdReferences: Map<String, String>): String {
        val decimal = id.toString()
        val appReference = resourceIdReferences[decimal]
            ?.takeIf { it.startsWith("@attr/") }
            ?.let { "?attr/${it.substringAfter('/')}" }
        return appReference
            ?: frameworkAttributeReferences[id.toInt()]
            ?: "?0x${id.toString(16).padStart(8, '0')}"
    }

    private fun sanitizeFrameworkAnimationEnums(xml: String): String {
        val mappings = mapOf(
            "zAdjustment" to mapOf("-1" to "bottom", "0" to "normal", "1" to "top"),
            "valueType" to mapOf(
                "0" to "floatType", "1" to "intType", "2" to "pathType", "3" to "colorType",
            ),
            "ordering" to mapOf("0" to "together", "1" to "sequentially"),
            "repeatMode" to mapOf("1" to "restart", "2" to "reverse"),
            "fillType" to mapOf("0" to "nonZero", "1" to "evenOdd"),
        )
        var result = xml
        mappings.forEach { (attribute, values) ->
            val pattern = Regex("""(\b(?:android:)?${Regex.escape(attribute)}=\")(-?\d+)(\")""")
            result = pattern.replace(result) { match ->
                val symbolic = values[match.groupValues[2]] ?: return@replace match.value
                "${match.groupValues[1]}$symbolic${match.groupValues[3]}"
            }
        }
        return result
    }

    fun sanitizeFileName(fileName: String): String = numericFileName.matchEntire(fileName)?.let { match ->
        "res_${match.groupValues[1]}${match.groupValues[2]}"
    } ?: fileName

    fun sanitizeResourceName(resourceName: String): String = when {
        resourceName.all(Char::isDigit) -> "res_$resourceName"
        dummyResourceName.matches(resourceName) -> dummyResourceName.replace(resourceName) { match ->
            "res_${match.groupValues[1].lowercase()}"
        }
        else -> resourceName.lowercase()
    }

    fun resourceIdReferences(publicXml: String): Map<String, String> {
        val publicEntry = Regex("""<public\b[^>]*\btype="([^"]+)"[^>]*\bname="([^"]+)"[^>]*\bid="0x([0-9A-Fa-f]+)"[^>]*/>""")
        return publicEntry.findAll(publicXml).associate { match ->
            val type = match.groupValues[1]
            val name = if (type == "attr") match.groupValues[2]
            else sanitizeResourceName(match.groupValues[2])
            match.groupValues[3].toLong(16).toString() to
                "@$type/$name"
        }
    }

    fun findMissingAttributeValues(xml: String): Map<String, Set<String>> {
        val result = linkedMapOf<String, MutableSet<String>>()

        xmlAttribute.findAll(xml).forEach { attribute ->
            missingValue.findAll(attribute.groupValues[2]).forEach { missing ->
                result.getOrPut(attribute.groupValues[1]) { linkedSetOf() }.add(missing.groupValues[1].lowercase())
            }
        }
        styleItem.findAll(xml).forEach { item ->
            missingValue.findAll(item.groupValues[2]).forEach { missing ->
                result.getOrPut(item.groupValues[1]) { linkedSetOf() }.add(missing.groupValues[1].lowercase())
            }
        }

        return result
    }

    fun addMissingAttributeDefinitions(
        attrsXml: String,
        definitions: Map<String, Set<String>>,
        originalValues: Map<String, Map<String, Int>>,
    ): String {
        var result = attrsXml
        definitions.forEach { (attributeName, values) ->
            val expanded = Regex(
                """(<attr\b[^>]*\bname="${Regex.escape(attributeName)}"[^>]*(?<!/)>)(.*?)(</attr>)""",
                RegexOption.DOT_MATCHES_ALL,
            )
            val selfClosing = Regex("""<attr\b([^>]*\bname="${Regex.escape(attributeName)}"[^>]*)\s*/>""")
            val kind = symbolicAttributes(attrsXml)[attributeName] ?: "flag"
            val flags = values.filterNot { hex -> "name=\"missing_$hex\"" in result }.joinToString(separator = "") { hex ->
                val value = originalValues[attributeName]?.get(hex)
                    ?: error("Could not recover value for $attributeName/missing_$hex")
                "\n        <$kind name=\"missing_$hex\" value=\"0x${value.toUInt().toString(16).padStart(8, '0')}\" />"
            }

            result = when {
                expanded.containsMatchIn(result) -> expanded.replace(result) { match ->
                    "${match.groupValues[1]}${match.groupValues[2]}$flags\n    ${match.groupValues[3]}"
                }
                selfClosing.containsMatchIn(result) -> selfClosing.replace(result) { match ->
                    "<attr${match.groupValues[1]}>$flags\n    </attr>"
                }
                else -> result
            }
        }
        return result
    }

    fun symbolicAttributes(attrsXml: String): Map<String, String> = attributeDefinition.findAll(attrsXml).mapNotNull { match ->
        val body = match.groupValues[2]
        val kind = when {
            "<enum" in body -> "enum"
            "<flag" in body -> "flag"
            else -> return@mapNotNull null
        }
        match.groupValues[1] to kind
    }.toMap()

    fun findRawSymbolicValues(xml: String, symbolicAttributes: Map<String, String>): Map<String, Set<String>> {
        val result = linkedMapOf<String, MutableSet<String>>()
        xmlAttribute.findAll(xml).forEach { match ->
            val name = match.groupValues[1]
            val value = match.groupValues[2]
            if (name in symbolicAttributes && value.matches(Regex("-?[0-9]+"))) {
                result.getOrPut(name) { linkedSetOf() }.add(value)
            }
        }
        styleItem.findAll(xml).forEach { match ->
            val name = match.groupValues[1]
            val value = match.groupValues[2].trim()
            if (name in symbolicAttributes && value.matches(Regex("-?[0-9]+"))) {
                result.getOrPut(name) { linkedSetOf() }.add(value)
            }
        }
        return result
    }

    fun sanitizeRawSymbolicValues(xml: String, symbolicAttributes: Map<String, String>): String {
        val attributesSanitized = xmlAttribute.replace(xml) { match ->
            val name = match.groupValues[1]
            val value = match.groupValues[2]
            if (name in symbolicAttributes && value.matches(Regex("-?[0-9]+"))) {
                match.value.replace("\"$value\"", "\"${rawSymbol(value)}\"")
            } else {
                match.value
            }
        }
        return styleItem.replace(attributesSanitized) { match ->
            val name = match.groupValues[1]
            val value = match.groupValues[2].trim()
            if (name in symbolicAttributes && value.matches(Regex("-?[0-9]+"))) {
                match.value.replace(match.groupValues[2], rawSymbol(value))
            } else {
                match.value
            }
        }
    }

    fun addRawSymbolicDefinitions(
        attrsXml: String,
        definitions: Map<String, Set<String>>,
        symbolicAttributes: Map<String, String>,
    ): String {
        var result = attrsXml
        definitions.forEach { (attributeName, values) ->
            val definition = Regex(
                """(<attr\b[^>]*\bname="${Regex.escape(attributeName)}"[^>]*(?<!/)>)(.*?)(</attr>)""",
                RegexOption.DOT_MATCHES_ALL,
            )
            val kind = symbolicAttributes[attributeName] ?: return@forEach
            result = definition.replace(result) { match ->
                val entries = values.filterNot { value -> "name=\"${rawSymbol(value)}\"" in match.groupValues[2] }
                    .joinToString(separator = "") { value ->
                        "\n        <$kind name=\"${rawSymbol(value)}\" value=\"$value\" />"
                    }
                "${match.groupValues[1]}${match.groupValues[2]}$entries\n    ${match.groupValues[3]}"
            }
        }
        return result
    }

    private fun rawSymbol(value: String) = "raw_${value.replace('-', 'n')}"

    private fun prefixed(match: MatchResult) =
        "${match.groupValues[1]}res_${match.groupValues[2]}${match.groupValues.getOrElse(3) { "" }}"
}
