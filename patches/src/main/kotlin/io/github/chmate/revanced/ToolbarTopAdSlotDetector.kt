package io.github.chmate.revanced

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource

internal object ToolbarTopAdSlotDetector {
    private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    private const val APP_NAMESPACE = "http://schemas.android.com/apk/res-auto"
    const val MARKER_TAG = "revanced_ad_container"

    fun detect(layouts: Map<String, String>): Set<String> {
        val candidates = layouts.mapNotNull { (path, xml) ->
            findCandidate(path, parse(xml))
        }
        return candidates
            .groupBy(Candidate::id)
            .filterValues { occurrences ->
                occurrences.map(Candidate::path).distinct().size >= 2 &&
                    occurrences.any(Candidate::isGone) &&
                    occurrences.any { !it.isGone } &&
                    occurrences.all(Candidate::isStructurallyEligible)
            }
            .keys
    }

    private fun findCandidate(path: String, document: Document): Candidate? {
        val elements = document.getElementsByTagName("*")
            .let { nodes ->
                buildList {
                    for (index in 0 until nodes.length) {
                        (nodes.item(index) as? Element)?.let(::add)
                    }
                }
            }
        val byId = elements.mapNotNull { element ->
            element.androidAttribute("id").resourceName()?.let { it to element }
        }.toMap()
        val toolbar = elements.firstOrNull {
            it.androidAttribute("tag") == "toolbarContentTop"
        } ?: return null

        var current = toolbar
        val visited = mutableSetOf<String>()
        while (true) {
            val referencedId = current.appAttribute("layout_constraintTop_toBottomOf").resourceName()
                ?: break
            if (!visited.add(referencedId)) return null
            current = byId[referencedId] ?: break
        }
        if (current === toolbar) return null

        return Candidate(
            path = path,
            id = current.androidAttribute("id").resourceName() ?: return null,
            isGone = current.androidAttribute("visibility") == "gone",
            isStructurallyEligible = current.androidAttribute("tag").isEmpty() &&
                current.androidAttribute("layout_height") == "wrap_content" &&
                current.appAttribute("layout_constraintTop_toTopOf").isNotEmpty(),
        )
    }

    private fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name).ifEmpty { getAttribute("android:$name") }

    private fun Element.appAttribute(name: String): String =
        getAttributeNS(APP_NAMESPACE, name).ifEmpty { getAttribute("app:$name") }

    private fun String.resourceName(): String? =
        substringAfterLast('/').takeIf { it.isNotBlank() }

    private data class Candidate(
        val path: String,
        val id: String,
        val isGone: Boolean,
        val isStructurallyEligible: Boolean,
    )
}
