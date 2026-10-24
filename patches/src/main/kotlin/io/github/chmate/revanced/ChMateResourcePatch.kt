package io.github.chmate.revanced

import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.ResourcePatchContext
import app.revanced.patcher.patch.resourcePatch
import java.io.File
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val SETTINGS_ACTIVITY = "app.revanced.extension.chmate.SettingsActivity"
private const val IMAGE_BOARD_ACTIVITY = "app.revanced.extension.chmate.ImageBoardActivity"
private const val IMAGE_BOARD_SOURCES_ACTIVITY = "app.revanced.extension.chmate.ImageBoardSourcesActivity"
private const val ADVANCED_NG_ACTIVITY = "app.revanced.extension.chmate.AdvancedNgActivity"
private const val SETTINGS_PREFERENCE_KEY = "chmateRevancedSettings"
private const val BOOTSTRAP_PROVIDER = "app.revanced.extension.chmate.BootstrapProvider"
private const val MAIN_ACTIVITY_METADATA = "app.revanced.extension.chmate.MAIN_ACTIVITY"
private const val ACTION_MAIN = "android.intent.action.MAIN"
private const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"

internal val chMateResourcePatch = resourcePatch {
    compatibleWith("jp.co.airfront.android.a2chMate")

    apply {
        ManifestClassNameSanitizer.reset()
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as? Element
                ?: throw PatchException("AndroidManifest.xml does not contain an application element")
            application.sanitizeComponentClassNames()
            val mainActivity = application.findLauncherActivity()
                ?: throw PatchException("AndroidManifest.xml does not contain a launcher activity")
            application.disableAdSdkComponents()
            application.disableTelemetryCollection(document)

            if (!application.hasComponent("activity", SETTINGS_ACTIVITY)) {
                application.addSettingsActivity(document, mainActivity)
            }
            if (!application.hasComponent("activity", IMAGE_BOARD_ACTIVITY)) {
                application.appendChild(document.createElement("activity").apply {
                    setAttribute("android:name", IMAGE_BOARD_ACTIVITY)
                    setAttribute("android:exported", "false")
                    setAttribute("android:excludeFromRecents", "true")
                    setAttribute("android:label", "Imageboards")
                    setAttribute("android:theme", "@android:style/Theme.Material.Light.NoActionBar")
                })
            }
            if (!application.hasComponent("activity", IMAGE_BOARD_SOURCES_ACTIVITY)) {
                application.appendChild(document.createElement("activity").apply {
                    setAttribute("android:name", IMAGE_BOARD_SOURCES_ACTIVITY)
                    setAttribute("android:exported", "false")
                    setAttribute("android:excludeFromRecents", "true")
                    setAttribute("android:label", "Imageboard sources")
                    setAttribute("android:theme", "@android:style/Theme.Material.Light.NoActionBar")
                })
            }
            if (!application.hasComponent("activity", ADVANCED_NG_ACTIVITY)) {
                application.appendChild(document.createElement("activity").apply {
                    setAttribute("android:name", ADVANCED_NG_ACTIVITY)
                    setAttribute("android:exported", "false")
                    setAttribute("android:excludeFromRecents", "true")
                    setAttribute("android:label", "Advanced NG")
                    setAttribute("android:theme", "@android:style/Theme.Material.Light.NoActionBar")
                })
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

        val manifestFile = get("AndroidManifest.xml")
        val resourceFiles = get("res").walkTopDown().filter { it.isFile }.toList()
        val xmlFiles = resourceFiles.filter { it.extension == "xml" }
        val xmlContents = (xmlFiles + manifestFile).associateWith { it.readText() }
        val attrsFile = get("res/values/attrs.xml")
        val publicFile = get("res/values/public.xml")
        val numericSymbols = xmlContents[attrsFile]
            ?.let(ResourceNameSanitizer::numericAttributeSymbols)
            .orEmpty()
        val resourceIdReferences = xmlContents[publicFile]
            ?.let(ResourceNameSanitizer::resourceIdReferences)
            .orEmpty()
        val obfuscatedFileResources = xmlContents.entries.flatMap { (file, contents) ->
            if (!file.parentFile.name.startsWith("values")) emptyList()
            else ObfuscatedFileResources.find(contents, file.parentFile.name)
        }

        xmlContents.forEach { (file, contents) ->
            var sanitized = ObfuscatedFileResources.removeAliases(contents)
            sanitized = ResourceNameSanitizer.sanitizeXml(sanitized, numericSymbols, resourceIdReferences)
            file.writeText(sanitized)
        }
        ObfuscatedFileResources.materialize(
            sourceApkFile(),
            get("res"),
            obfuscatedFileResources,
            numericSymbols,
            resourceIdReferences,
        )

        val allXmlFiles = (get("res").walkTopDown().filter { it.isFile && it.extension == "xml" }.toList() + manifestFile)
            .distinct()
        val allXmlContents = allXmlFiles.associateWith { it.readText() }
        val symbolicAttributes = ResourceNameSanitizer.symbolicAttributes(attrsFile.readText())
        val missingDefinitions = allXmlContents.values
            .map(ResourceNameSanitizer::findMissingAttributeValues)
            .flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.flatten().toSet() }
        val originalMissingValues = try {
            OriginalAttributeValues.resolve(
                sourceApkFile(),
                ResourceNameSanitizer.attributeResourceIds(publicFile.readText()),
                missingDefinitions,
            )
        } catch (exception: Exception) {
            throw PatchException("Could not recover original custom attribute values", exception)
        }
        val rawSymbolicDefinitions = allXmlContents.values
            .map { ResourceNameSanitizer.findRawSymbolicValues(it, symbolicAttributes) }
            .flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.flatten().toSet() }

        allXmlContents.forEach { (file, contents) ->
            file.writeText(ResourceNameSanitizer.sanitizeRawSymbolicValues(contents, symbolicAttributes))
        }
        var sanitizedAttrs = attrsFile.readText()
        sanitizedAttrs = ResourceNameSanitizer.addMissingAttributeDefinitions(
            sanitizedAttrs,
            missingDefinitions,
            originalMissingValues,
        )
        sanitizedAttrs = ResourceNameSanitizer.addRawSymbolicDefinitions(
            sanitizedAttrs,
            rawSymbolicDefinitions,
            symbolicAttributes,
        )
        attrsFile.writeText(sanitizedAttrs)

        val applicationAttributeNames = ResourceNameSanitizer.applicationAttributeNames(sanitizedAttrs)
        allXmlFiles
            .filter { file -> file.parentFile.name == "layout" || file.parentFile.name.startsWith("layout-") }
            .forEach { file ->
                file.writeText(
                    ResourceNameSanitizer.qualifyApplicationAttributes(file.readText(), applicationAttributeNames),
                )
            }

        val xmlResourcePaths = get("res").walkTopDown()
            .filter { file ->
                file.isFile && file.extension == "xml" && file.parentFile.name.startsWith("xml")
            }
            .map { file -> "res/${file.relativeTo(get("res")).invariantSeparatorsPath}" }
            .toList()
        val rootSettingsPaths = xmlResourcePaths.filter { path ->
            document(path).use(Document::isEmptyPreferenceScreen)
        }
        rootSettingsPaths.forEach { path ->
            document(path).use(Document::addSettingsPreference)
        }
        val boardSettingsPaths = xmlResourcePaths.filter { path ->
            document(path).use(Document::isBoardSettingsScreen)
        }
        boardSettingsPaths.forEach { path ->
            document(path).use(Document::addImageBoardSourcesPreference)
        }

        resourceFiles.forEach { file ->
            val sanitizedName = ResourceNameSanitizer.sanitizeFileName(file.name)
            if (sanitizedName != file.name && !file.renameTo(file.resolveSibling(sanitizedName))) {
                throw PatchException("Could not sanitize resource file name: ${file.path}")
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
            val file = get(path)
            val original = file.readText()
            val sanitized = LayoutXmlSanitizer.sanitize(original)
            if (sanitized != original) file.writeText(sanitized)
        }

        val toolbarTopAdSlotIds = ToolbarTopAdSlotDetector.detect(
            layoutPaths.associateWith { path -> get(path).readText() },
        )

        layoutPaths.forEach { path ->
                document(path).use { document ->
                    val elements = document.getElementsByTagName("*")
                    for (index in 0 until elements.length) {
                        val element = elements.item(index) as? Element ?: continue
                        val resourceId = element.getAttribute("android:id").substringAfterLast('/')
                        val isToolbarTopAdSlot = resourceId in toolbarTopAdSlotIds
                        if (!isToolbarTopAdSlot && !AdElementClassifier.isAdvertisement(
                                element.getAttribute("class").takeIf { element.tagName == "view" && it.isNotEmpty() }
                                    ?: element.tagName,
                                element.getAttribute("android:id").ifEmpty { null },
                                element.getAttribute("android:tag").ifEmpty { null },
                            )
                        ) {
                            continue
                        }

                        if (isToolbarTopAdSlot && element.getAttribute("android:tag").isEmpty()) {
                            element.setAttribute("android:tag", ToolbarTopAdSlotDetector.MARKER_TAG)
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

internal fun Element.addSettingsActivity(document: Document, mainActivity: String) {
    appendChild(document.createElement("activity").apply {
        setAttribute("android:name", SETTINGS_ACTIVITY)
        setAttribute("android:exported", "false")
        setAttribute("android:excludeFromRecents", "true")
        setAttribute("android:label", "ChMate ReVanced")
        setAttribute("android:theme", "@android:style/Theme.Material.Light.NoActionBar")

        appendChild(document.createElement("meta-data").apply {
            setAttribute("android:name", MAIN_ACTIVITY_METADATA)
            setAttribute("android:value", mainActivity)
        })
    })
}

internal fun Document.isEmptyPreferenceScreen(): Boolean {
    val root = documentElement ?: return false
    if (root.tagName != "PreferenceScreen") return false
    return (0 until root.childNodes.length).none { index ->
        root.childNodes.item(index) is Element
    }
}

internal fun Document.addSettingsPreference() {
    documentElement.appendChild(createElement("Preference").apply {
        setAttribute("android:key", SETTINGS_PREFERENCE_KEY)
        setAttribute("android:title", "ChMate ReVanced")
        appendChild(createElement("intent").apply {
            setAttribute("android:targetPackage", "jp.co.airfront.android.a2chMate")
            setAttribute("android:targetClass", SETTINGS_ACTIVITY)
        })
    })
}

internal fun Document.isBoardSettingsScreen(): Boolean {
    val root = documentElement ?: return false
    if (root.tagName != "PreferenceScreen") return false
    val elements = root.getElementsByTagName("*")
    var hasBoardCategory = false
    var hasBbsMenuUrl = false
    var bbsMenuPreferenceCount = 0
    for (index in 0 until elements.length) {
        val element = elements.item(index) as? Element ?: continue
        if (element.tagName.endsWith("BbsMenuUrlPreference")) bbsMenuPreferenceCount++
        when (element.getAttribute("android:key")) {
            "bbs" -> hasBoardCategory = true
            "bbsMenuUrl" -> hasBbsMenuUrl = true
        }
    }
    return (hasBoardCategory && hasBbsMenuUrl) || bbsMenuPreferenceCount >= 2
}

internal fun Document.addImageBoardSourcesPreference() {
    val root = documentElement ?: return
    val existing = root.getElementsByTagName("Preference")
    if ((0 until existing.length).any { index ->
            (existing.item(index) as? Element)?.getAttribute("android:key") == "imageBoardSources"
        }
    ) return

    val preference = createElement("Preference").apply {
        setAttribute("android:key", "imageBoardSources")
        setAttribute("android:title", "画像掲示板（4chan・ふたば・jschan）")
        setAttribute("android:summary", "ChMateの板として追加し、標準の板一覧・スレッド一覧・レス画面で開きます")
        appendChild(createElement("intent").apply {
            setAttribute("android:targetPackage", "jp.co.airfront.android.a2chMate")
            setAttribute("android:targetClass", IMAGE_BOARD_SOURCES_ACTIVITY)
        })
    }
    val categories = root.getElementsByTagName("PreferenceCategory")
    val boardCategory = (0 until categories.length)
        .mapNotNull { categories.item(it) as? Element }
        .firstOrNull { it.getAttribute("android:key") == "bbs" }
    if (boardCategory?.nextSibling != null) {
        root.insertBefore(preference, boardCategory.nextSibling)
    } else {
        root.appendChild(preference)
    }
}

private fun ResourcePatchContext.sourceApkFile(): File {
    val field = ResourcePatchContext::class.java.getDeclaredField("apkFile")
    field.isAccessible = true
    return field.get(this) as? File ?: throw PatchException("Could not access source APK")
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

private fun Element.disableAdSdkComponents() {
    listOf("activity", "provider", "receiver", "service").forEach { tagName ->
        val components = getElementsByTagName(tagName)
        for (index in 0 until components.length) {
            val component = components.item(index) as? Element ?: continue
            if (AdElementClassifier.isAdSdkClass(component.getAttribute("android:name"))) {
                component.setAttribute("android:enabled", "false")
            }
        }
    }
}

internal fun Element.disableTelemetryCollection(document: Document) {
    mapOf(
        "firebase_analytics_collection_deactivated" to "true",
        "firebase_analytics_collection_enabled" to "false",
        "google_analytics_adid_collection_enabled" to "false",
        "google_analytics_automatic_screen_reporting_enabled" to "false",
        "firebase_crashlytics_collection_enabled" to "false",
        "firebase_performance_collection_enabled" to "false",
    ).forEach { (name, value) ->
        val existing = (0 until childNodes.length)
            .mapNotNull { childNodes.item(it) as? Element }
            .firstOrNull { it.tagName == "meta-data" && it.getAttribute("android:name") == name }
        val metadata = existing ?: document.createElement("meta-data").also(::appendChild)
        metadata.setAttribute("android:name", name)
        metadata.setAttribute("android:value", value)
    }
}

private fun Element.sanitizeComponentClassNames() {
    listOf("activity", "activity-alias", "provider", "receiver", "service").forEach { tagName ->
        val components = getElementsByTagName(tagName)
        for (index in 0 until components.length) {
            val component = components.item(index) as? Element ?: continue
            val name = component.getAttribute("android:name")
            if (name.isNotEmpty()) {
                component.setAttribute("android:name", ManifestClassNameSanitizer.sanitize(name))
            }
        }
    }
}
