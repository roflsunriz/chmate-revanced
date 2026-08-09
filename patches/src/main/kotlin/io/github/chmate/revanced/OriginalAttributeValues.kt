package io.github.chmate.revanced

import brut.androlib.res.data.ResTable
import brut.androlib.res.data.arsc.FlagItem
import brut.androlib.res.data.value.ResEnumAttr
import brut.androlib.res.data.value.ResFlagsAttr
import brut.androlib.res.data.value.ResIntBasedValue
import brut.androlib.res.data.value.ResReferenceValue
import brut.directory.ExtFile
import brut.util.Duo
import java.io.File

internal object OriginalAttributeValues {
    fun resolve(
        apkFile: File,
        attributeIds: Map<String, Int>,
        missingDefinitions: Map<String, Set<String>>,
    ): Map<String, Map<String, Int>> {
        if (missingDefinitions.isEmpty()) return emptyMap()

        val apk = ExtFile(apkFile)
        try {
            val table = ResTable(apk)
            table.loadMainPkg(apk)
            return missingDefinitions.mapValues { (attributeName, missingNames) ->
                val attributeId = attributeIds[attributeName]
                    ?: error("Could not find resource ID for attribute $attributeName")
                val attribute = table.getResSpec(attributeId).defaultResource.value
                val values = when (attribute) {
                    is ResFlagsAttr -> flagValues(attribute)
                    is ResEnumAttr -> enumValues(attribute)
                    else -> error("Attribute $attributeName is not an enum or flag")
                }
                missingNames.associateWith { hex ->
                    values[hex.toLong(16).toInt()]
                        ?: error("Could not find original value for $attributeName/missing_$hex")
                }
            }
        } finally {
            apk.close()
        }
    }

    private fun flagValues(attribute: ResFlagsAttr): Map<Int, Int> {
        val field = ResFlagsAttr::class.java.getDeclaredField("mItems").apply { isAccessible = true }
        return (field.get(attribute) as Array<*>)
            .filterIsInstance<FlagItem>()
            .associate { it.ref.rawIntValue to it.flag }
    }

    private fun enumValues(attribute: ResEnumAttr): Map<Int, Int> {
        val field = ResEnumAttr::class.java.getDeclaredField("mItems").apply { isAccessible = true }
        return (field.get(attribute) as Array<*>)
            .filterIsInstance<Duo<*, *>>()
            .associate { item ->
                val reference = item.m1 as ResReferenceValue
                val value = item.m2 as ResIntBasedValue
                reference.rawIntValue to value.rawIntValue
            }
    }
}
