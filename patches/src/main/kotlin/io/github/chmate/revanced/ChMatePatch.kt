package io.github.chmate.revanced

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
    }
}
