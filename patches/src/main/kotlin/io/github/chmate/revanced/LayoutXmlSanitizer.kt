package io.github.chmate.revanced

internal object LayoutXmlSanitizer {
    private val nestedClassTag = Regex(
        """<(/?)([A-Za-z_][A-Za-z0-9_.]*[${'$'}][A-Za-z0-9_.${'$'}-]*)(?=\s|/?>)""",
    )

    fun sanitize(xml: String): String {
        if ('$' !in xml) return xml

        return nestedClassTag.replace(xml) { match ->
            if (match.groupValues[1].isEmpty()) {
                "<view class=\"${match.groupValues[2]}\""
            } else {
                "</view"
            }
        }
    }
}
