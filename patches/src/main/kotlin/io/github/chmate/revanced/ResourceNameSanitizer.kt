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
    private val xmlAttribute = Regex("""(?:[A-Za-z0-9_.-]+:)?([A-Za-z0-9_.-]+)="([^"]*)"""")
    private val styleItem = Regex("""<item\b[^>]*\bname="([^"]+)"[^>]*>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    private val attributeDefinition = Regex(
        """<attr\b(?=[^>]*\bname="([^"]+)")[^>]*(?<!/)>(.*?)</attr>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun numericAttributeSymbols(attrsXml: String): Set<String> = numericAttributeSymbol.findAll(attrsXml)
        .map { it.groupValues[1] }
        .toSet()

    fun sanitizeXml(
        xml: String,
        numericSymbols: Set<String> = emptySet(),
        resourceIdReferences: Map<String, String> = emptyMap(),
    ): String = xml
        .replace(dummyResourceName) { match -> "res_${match.groupValues[1].lowercase()}" }
        .replace(numericNameAttribute) { match -> prefixed(match) }
        .replace(numericTypedReference) { match -> prefixed(match) }
        .replace(numericStyleParent) { match -> prefixed(match) }
        .replace(numericValueToken) { match ->
            if (match.groupValues[1] in numericSymbols) "res_${match.groupValues[1]}" else match.value
        }
        .replace(missingValue) { match -> "missing_${match.groupValues[1].lowercase()}" }
        .replace(rawResourceReference) { match -> resourceIdReferences[match.groupValues[1]] ?: match.value }

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
            match.groupValues[3].toLong(16).toString() to
                "@${match.groupValues[1]}/${sanitizeResourceName(match.groupValues[2])}"
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

    fun addMissingAttributeDefinitions(attrsXml: String, definitions: Map<String, Set<String>>): String {
        var result = attrsXml
        definitions.forEach { (attributeName, values) ->
            val expanded = Regex(
                """(<attr\b[^>]*\bname="${Regex.escape(attributeName)}"[^>]*(?<!/)>)(.*?)(</attr>)""",
                RegexOption.DOT_MATCHES_ALL,
            )
            val selfClosing = Regex("""<attr\b([^>]*\bname="${Regex.escape(attributeName)}"[^>]*)\s*/>""")
            val kind = symbolicAttributes(attrsXml)[attributeName] ?: "flag"
            val flags = values.filterNot { hex -> "name=\"missing_$hex\"" in result }.joinToString(separator = "") { hex ->
                "\n        <$kind name=\"missing_$hex\" value=\"0x$hex\" />"
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
