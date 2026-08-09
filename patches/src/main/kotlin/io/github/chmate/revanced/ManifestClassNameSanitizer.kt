package io.github.chmate.revanced

internal object ManifestClassNameSanitizer {
    private val replacements = linkedMapOf<String, String>()

    fun reset() = replacements.clear()

    fun sanitize(className: String): String {
        if ('-' !in className) return className

        return className.replace('-', '_').also { sanitized ->
            replacements[className] = sanitized
        }
    }

    fun replacements(): Map<String, String> = replacements.toMap()
}
