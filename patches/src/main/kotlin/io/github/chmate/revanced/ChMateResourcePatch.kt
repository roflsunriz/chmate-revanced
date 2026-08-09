package io.github.chmate.revanced

import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val SETTINGS_ACTIVITY = "app.revanced.extension.chmate.SettingsActivity"
private const val BOOTSTRAP_PROVIDER = "app.revanced.extension.chmate.BootstrapProvider"
private const val MAIN_ACTIVITY_METADATA = "app.revanced.extension.chmate.MAIN_ACTIVITY"
private const val ACTION_MAIN = "android.intent.action.MAIN"
private const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"

internal val chMateResourcePatch = resourcePatch {
    compatibleWith("jp.co.airfront.android.a2chMate")

    apply {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as? Element
                ?: throw PatchException("AndroidManifest.xml does not contain an application element")
            val mainActivity = application.findLauncherActivity()
                ?: throw PatchException("AndroidManifest.xml does not contain a launcher activity")

            if (!application.hasComponent("activity", SETTINGS_ACTIVITY)) {
                val activity = document.createElement("activity").apply {
                    setAttribute("android:name", SETTINGS_ACTIVITY)
                    setAttribute("android:exported", "true")
                    setAttribute("android:excludeFromRecents", "true")
                    setAttribute("android:label", "ChMate ReVanced")
                    setAttribute("android:process", ":revanced_settings")
                    setAttribute("android:theme", "@android:style/Theme.Material.Light.NoActionBar")

                    appendChild(document.createElement("intent-filter").apply {
                        appendChild(document.createElement("action").apply {
                            setAttribute("android:name", ACTION_MAIN)
                        })
                        appendChild(document.createElement("category").apply {
                            setAttribute("android:name", CATEGORY_LAUNCHER)
                        })
                    })
                    appendChild(document.createElement("meta-data").apply {
                        setAttribute("android:name", MAIN_ACTIVITY_METADATA)
                        setAttribute("android:value", mainActivity)
                    })
                }
                application.appendChild(activity)
            }

            if (!application.hasComponent("provider", BOOTSTRAP_PROVIDER)) {
                application.appendChild(document.createElement("provider").apply {
                    setAttribute("android:name", BOOTSTRAP_PROVIDER)
                    setAttribute("android:authorities", "jp.co.airfront.android.a2chMate.revanced.bootstrap")
                    setAttribute("android:exported", "false")
                    setAttribute("android:initOrder", "999999")
                })
            }
        }

        val layoutPaths = get("res").listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .filter { it.name == "layout" || it.name.startsWith("layout-") }
            .flatMap { directory ->
                directory.listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.extension == "xml" }
                    .map { file -> "res/${directory.name}/${file.name}" }
            }

        layoutPaths.forEach { path ->
                document(path).use { document ->
                    val elements = document.getElementsByTagName("*")
                    for (index in 0 until elements.length) {
                        val element = elements.item(index) as? Element ?: continue
                        if (!AdElementClassifier.isAdvertisement(
                                element.tagName,
                                element.getAttribute("android:id").ifEmpty { null },
                                element.getAttribute("android:tag").ifEmpty { null },
                            )
                        ) {
                            continue
                        }

                        element.setAttribute("android:layout_height", "0dp")
                        element.setAttribute("android:minHeight", "0dp")
                        element.setAttribute("android:visibility", "gone")
                        element.setAttribute("android:layout_marginTop", "0dp")
                        element.setAttribute("android:layout_marginBottom", "0dp")
                    }
                }
            }
    }
}

private fun Element.hasComponent(tagName: String, className: String): Boolean {
    val elements = getElementsByTagName(tagName)
    return (0 until elements.length).any { index ->
        (elements.item(index) as? Element)?.getAttribute("android:name") == className
    }
}

private fun Element.findLauncherActivity(): String? {
    for (tagName in listOf("activity", "activity-alias")) {
        val components = getElementsByTagName(tagName)
        for (componentIndex in 0 until components.length) {
            val component = components.item(componentIndex) as? Element ?: continue
            val filters = component.getElementsByTagName("intent-filter")
            val isLauncher = (0 until filters.length).any { filterIndex ->
                val filter = filters.item(filterIndex) as? Element ?: return@any false
                filter.hasNamedChild("action", ACTION_MAIN) &&
                    filter.hasNamedChild("category", CATEGORY_LAUNCHER)
            }
            if (isLauncher) {
                return component.getAttribute(
                    if (tagName == "activity-alias") "android:targetActivity" else "android:name",
                ).ifEmpty { null }
            }
        }
    }
    return null
}

private fun Element.hasNamedChild(tagName: String, name: String): Boolean {
    val children = getElementsByTagName(tagName)
    return (0 until children.length).any { index ->
        (children.item(index) as? Element)?.getAttribute("android:name") == name
    }
}
