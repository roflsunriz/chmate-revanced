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
    private val rawHexResourceReference = Regex("""@0x([0-9A-Fa-f]{8})(?![0-9A-Fa-f])""")
    private val nullResourceReference = Regex("""@(?:0x0{1,8}|0)(?![0-9A-Fa-f])""")
    private val rawAttributeReference = Regex(
        """\?(?:(?:android:)?attr/)?([0-9]{8,10})(?![0-9])""",
    )
    private val rawHexAttributeReference = Regex("""\?0x([0-9A-Fa-f]{8})(?![0-9A-Fa-f])""")
    private val frameworkAttributeReferences = mapOf(
        0x01010030 to "?android:attr/colorForeground",
        0x01010033 to "?android:attr/disabledAlpha",
        0x01010042 to "?android:attr/textAppearanceSmall",
        0x0101005b to "?android:attr/windowTitleStyle",
        0x01010078 to "?android:attr/progressBarStyleHorizontal",
        0x01010079 to "?android:attr/progressBarStyleSmall",
        0x01010086 to "?android:attr/dropDownItemStyle",
        0x01010208 to "?android:attr/listSeparatorTextViewStyle",
        0x01010429 to "?android:attr/colorControlNormal",
        0x0101042a to "?android:attr/colorControlActivated",
        0x0101042b to "?android:attr/colorButtonNormal",
        0x0101042c to "?android:attr/colorControlHighlight",
        0x01010435 to "?android:attr/colorAccent",
    )
    private val numericXmlAttributeName = Regex(
        """(\s(?:[A-Za-z_][A-Za-z0-9_.-]*:)?)(213[0-9]{7})(=\")""",
    )
    private val shortColor = Regex("""(?<==\")#([0-9A-Fa-f])(?=\")""")
    private val frameworkResourceReferences = mapOf(
        0x01020000 to "@android:id/background",
        0x0102000d to "@android:id/progress",
        0x0102000f to "@android:id/secondaryProgress",
        0x0102002e to "@android:id/mask",
        0x01030006 to "@android:style/Theme.NoTitleBar",
        0x01030007 to "@android:style/Theme.NoTitleBar.Fullscreen",
        0x01030009 to "@android:style/Theme.Black.NoTitleBar",
        0x0103000a to "@android:style/Theme.Black.NoTitleBar.Fullscreen",
        0x0103000f to "@android:style/Theme.Translucent",
        0x01030010 to "@android:style/Theme.Translucent.NoTitleBar",
        0x0103022e to "@android:style/Theme.Material.NoActionBar",
        0x01030232 to "@android:style/Theme.Material.Panel",
        0x01030241 to "@android:style/Theme.Material.Light.NoActionBar",
        0x0106000b to "@android:color/white",
        0x0106000c to "@android:color/black",
        0x0106000d to "@android:color/transparent",
        0x010a0004 to "@android:anim/accelerate_decelerate_interpolator",
        0x010a0005 to "@android:anim/accelerate_interpolator",
        0x010a0006 to "@android:anim/decelerate_interpolator",
        0x010a000b to "@android:anim/linear_interpolator",
        0x010c0000 to "@android:interpolator/accelerate_quad",
        0x010c0001 to "@android:interpolator/decelerate_quad",
        0x010c000b to "@android:interpolator/linear",
        0x010c000d to "@android:interpolator/fast_out_slow_in",
        0x010e0000 to "@android:integer/config_shortAnimTime",
        0x010e0001 to "@android:integer/config_mediumAnimTime",
    )
    private val runtimeFrameworkResourceReferences: Map<Int, String> by lazy {
        listOf("anim", "color", "drawable", "id", "integer", "interpolator", "layout", "string", "style")
            .flatMap { type ->
                runCatching {
                    Class.forName("android.R\$$type").fields.map { field ->
                        field.getInt(null) to "@android:$type/${field.name}"
                    }
                }.getOrDefault(emptyList())
            }
            .toMap()
    }
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
        .replace(nullResourceReference, "@null")
        .replace(rawHexResourceReference) { match ->
            val id = match.groupValues[1].toLong(16).toInt()
            runtimeFrameworkResourceReferences[id]
                ?: frameworkResourceReferences[id]
                ?: match.value
        }
        .replace(rawResourceReference) { match ->
            resourceIdReferences[match.groupValues[1]]
                ?: runtimeFrameworkResourceReferences[match.groupValues[1].toLong().toInt()]
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
            ?: runtimeFrameworkAttributeName(id.toInt())?.let { "?android:attr/$it" }
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
            "shape" to mapOf("0" to "rectangle", "1" to "oval", "2" to "line", "3" to "ring"),
            "tileMode" to mapOf(
                "0" to "clamp", "1" to "repeat", "2" to "mirror", "-1" to "disabled",
            ),
            "tileModeX" to mapOf(
                "0" to "clamp", "1" to "repeat", "2" to "mirror", "-1" to "disabled",
            ),
            "clipOrientation" to mapOf(
                "0x1" to "horizontal", "0x2" to "vertical", "0x3" to "horizontal|vertical",
            ),
            "gravity" to mapOf(
                "0x1" to "center_horizontal",
                "0x11" to "center",
                "0x13" to "left|center_vertical",
                "0x800003" to "start",
            ),
            "layout_width" to mapOf("-1" to "match_parent", "-2" to "wrap_content"),
            "layout_height" to mapOf("-1" to "match_parent", "-2" to "wrap_content"),
            "orientation" to mapOf("0" to "horizontal", "1" to "vertical"),
            "visibility" to mapOf("0" to "visible", "1" to "invisible", "2" to "gone"),
            "ellipsize" to mapOf(
                "0" to "none", "1" to "start", "2" to "middle", "3" to "end", "4" to "marquee",
            ),
            "layoutDirection" to mapOf("0" to "ltr", "1" to "rtl", "2" to "inherit", "3" to "locale"),
            "scaleType" to mapOf(
                "0" to "matrix", "1" to "fitXY", "2" to "fitStart", "3" to "fitCenter",
                "4" to "fitEnd", "5" to "center", "6" to "centerCrop", "7" to "centerInside",
            ),
            "dropDownHeight" to mapOf("-1" to "match_parent", "-2" to "wrap_content"),
            "overScrollMode" to mapOf("0" to "always", "1" to "ifContentScrolls", "2" to "never"),
            "imeOptions" to mapOf(
                "0x0" to "actionUnspecified", "0x1" to "actionNone", "0x2" to "actionGo",
                "0x3" to "actionSearch", "0x4" to "actionSend", "0x5" to "actionNext",
                "0x6" to "actionDone", "0x7" to "actionPrevious",
            ),
            "inputType" to mapOf(
                "0x1" to "text",
                "0x11" to "textUri",
                "0x21" to "textEmailAddress",
                "0x81" to "textPassword",
                "0x20001" to "textMultiLine",
                "0x20011" to "textUri|textMultiLine",
                "0x90001" to "textAutoComplete|textNoSuggestions",
            ),
            "importantForAutofill" to mapOf(
                "0x0" to "auto", "0x1" to "yes", "0x2" to "no",
                "0x4" to "yesExcludeDescendants", "0x8" to "noExcludeDescendants",
            ),
            "textStyle" to mapOf(
                "0x0" to "normal", "0x1" to "bold", "0x2" to "italic", "0x3" to "bold|italic",
            ),
            "ringtoneType" to mapOf(
                "0x1" to "ringtone", "0x2" to "notification", "0x4" to "alarm", "0x7" to "all",
            ),
        )
        var result = sanitizeFrameworkFlags(xml)
        mappings.forEach { (attribute, values) ->
            val pattern = Regex("""(\b(?:android:)?${Regex.escape(attribute)}=\")(-?(?:0x[0-9A-Fa-f]+|\d+))(\")""")
            result = pattern.replace(result) { match ->
                val symbolic = values[match.groupValues[2]] ?: return@replace match.value
                "${match.groupValues[1]}$symbolic${match.groupValues[3]}"
            }
        }
        return result
    }

    private fun sanitizeFrameworkFlags(xml: String): String {
        var result = replaceFlagAttribute(xml, "gravity", ::decodeGravity)
        result = replaceFlagAttribute(result, "layout_gravity", ::decodeGravity)
        result = replaceFlagAttribute(result, "foregroundGravity", ::decodeGravity)
        result = replaceFlagAttribute(result, "fadingEdge") { value ->
            decodeFlags(value, listOf(0x1000 to "horizontal", 0x2000 to "vertical"))
        }
        result = replaceFlagAttribute(result, "scrollbars") { value ->
            decodeFlags(value, listOf(0x100 to "horizontal", 0x200 to "vertical"))
        }
        result = replaceFlagAttribute(result, "showAsAction") { value ->
            decodeFlags(value, listOf(
                0x1 to "ifRoom", 0x2 to "always", 0x4 to "withText", 0x8 to "collapseActionView",
            ))
        }
        result = replaceFlagAttribute(result, "scrollIndicators") { value ->
            decodeFlags(value, listOf(
                0x01 to "top", 0x02 to "bottom", 0x04 to "left",
                0x08 to "right", 0x10 to "start", 0x20 to "end",
            ))
        }
        return result
    }

    private fun replaceFlagAttribute(xml: String, attribute: String, decoder: (Int) -> String?): String {
        val pattern = Regex("""(\b(?:android:)?${Regex.escape(attribute)}=\")(-?(?:0x[0-9A-Fa-f]+|\d+))(\")""")
        return pattern.replace(xml) { match ->
            val raw = match.groupValues[2]
            val value = if (raw.startsWith("0x")) raw.substring(2).toLong(16).toInt() else raw.toInt()
            val symbolic = decoder(value) ?: return@replace match.value
            "${match.groupValues[1]}$symbolic${match.groupValues[3]}"
        }
    }

    private fun decodeGravity(value: Int): String? {
        if (value == 0) return "no_gravity"
        val parts = mutableListOf<String>()
        when (value and 0x00800007) {
            0x00800003 -> parts += "start"
            0x00800005 -> parts += "end"
            0x01 -> parts += "center_horizontal"
            0x03 -> parts += "left"
            0x05 -> parts += "right"
            0x07 -> parts += "fill_horizontal"
        }
        when (value and 0x70) {
            0x10 -> parts += "center_vertical"
            0x30 -> parts += "top"
            0x50 -> parts += "bottom"
            0x70 -> parts += "fill_vertical"
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("|")
    }

    private fun decodeFlags(value: Int, flags: List<Pair<Int, String>>): String? {
        if (value == 0) return "none"
        var remaining = value
        val parts = flags.mapNotNull { (mask, name) ->
            if (remaining and mask == 0) null else name.also { remaining = remaining and mask.inv() }
        }
        return parts.takeIf { remaining == 0 && it.isNotEmpty() }?.joinToString("|")
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
