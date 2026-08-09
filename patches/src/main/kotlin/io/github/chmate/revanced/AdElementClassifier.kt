package io.github.chmate.revanced

internal object AdElementClassifier {
    private val exactNames = setOf(
        "ad",
        "ads",
        "ad_view",
        "adview",
        "ad_container",
        "adcontainer",
        "ad_banner",
        "adbanner",
        "banner_ad",
        "bannerad",
        "advertisement",
    )

    private val sdkClassMarkers = listOf(
        "com.google.android.gms.ads",
        "com.google.android.gms.internal.ads",
        "com.google.ads",
        "com.amazon.device.ads",
        "com.applovin",
        "com.unity3d.ads",
        "com.ironsource",
        "com.bytedance.sdk.openadsdk",
        "com.facebook.ads",
        "com.inmobi",
        "net.nend.android",
    )

    fun isAdvertisement(tagName: String, idValue: String?, tagValue: String?): Boolean {
        val normalizedTag = tagName.lowercase()
        if (
            isAdSdkClass(normalizedTag) ||
            normalizedTag.endsWith(".adview") ||
            normalizedTag.contains("bannerad")
        ) {
            return true
        }

        return sequenceOf(idValue, tagValue)
            .filterNotNull()
            .map(::resourceName)
            .any(exactNames::contains)
    }

    fun isAdSdkClass(className: String): Boolean {
        val normalized = className
            .lowercase()
            .replace('/', '.')
            .removePrefix("l")
            .removeSuffix(";")
        return sdkClassMarkers.any { marker ->
            normalized == marker || normalized.startsWith("$marker.") || normalized.contains(".$marker.")
        }
    }

    fun isAdSdkRequestMethod(className: String, methodName: String, returnType: String): Boolean {
        if (returnType != "V" || !isAdSdkClass(className)) return false

        val normalizedName = methodName.lowercase()
        return normalizedName == "initialize" ||
            normalizedName.contains("loadad") ||
            normalizedName.contains("requestad") ||
            normalizedName.contains("fetchad")
    }

    private fun resourceName(value: String) = value
        .substringAfterLast('/')
        .lowercase()
        .replace(Regex("[^a-z0-9_]+"), "_")
}
