package io.github.chmate.revanced

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val chMatePatch = bytecodePatch(
    name = "ChMate ReVanced",
    description = "Collapses ad slots, blocks ad network traffic, and adds configurable User-Agent and restart controls.",
) {
    compatibleWith("jp.co.airfront.android.a2chMate")
    dependsOn(chMateResourcePatch)
    extendWith("extensions/chmate.rve")

    apply {
        patchNetworkBoundaries()
        renameSanitizedManifestClasses()
    }
}

private fun BytecodePatchContext.renameSanitizedManifestClasses() {
    ManifestClassNameSanitizer.replacements().forEach { (originalName, sanitizedName) ->
        val originalType = "L${originalName.replace('.', '/')};"
        val sanitizedType = "L${sanitizedName.replace('.', '/')};"
        val classDef = classDefs.firstOrNull { it.type == originalType }
            ?: throw PatchException("Manifest component class not found: $originalName")
        val mutableClass = classDefs.getOrReplaceMutable(classDef)

        mutableClass.type = sanitizedType
        mutableClass.methods.forEach { it.definingClass = sanitizedType }
        mutableClass.fields.forEach { it.definingClass = sanitizedType }
    }
}
