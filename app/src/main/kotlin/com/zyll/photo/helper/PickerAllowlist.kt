package com.zyll.photo.helper

/**
 * Every command this helper can run over ADB is defined here, and nothing else.
 * No command text ever comes from user input or the network; package names read back
 * from the device are validated so they can't be turned into extra shell statements.
 *
 * Settings come from AOSP MediaProvider's ConfigStore (namespace "mediaprovider"):
 *   - allowed_cloud_providers: packages allowed to act as a cloud media app, comma separated
 *   - cloud_media_feature_enabled: the photo picker's cloud media switch
 *
 * The helper only runs on Android 15+, so it always writes with `device_config override`.
 * An override is kept locally and is not replaced by server config syncs, and
 * `clear_override` puts the key back to exactly what the system had before.
 */
object PickerAllowlist {
    const val TARGET_PACKAGE = "com.zyll.photo"
    const val NAMESPACE = "mediaprovider"
    const val KEY_ALLOWLIST = "allowed_cloud_providers"
    const val KEY_FEATURE = "cloud_media_feature_enabled"

    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    enum class Kind {
        ReadAllowlist,
        AddToAllowlist,
        EnableCloudMedia,
        ClearAllowlistOverride,
        ClearFeatureOverride,
        RemoveFromBaseAllowlist,
        DeleteBaseAllowlist,
        DisableWirelessDebugging,
    }

    /** One command, plus what it does (the UI turns [kind] into a translated description). */
    data class Step(val kind: Kind, val command: String)

    fun readAllowlist() = Step(Kind.ReadAllowlist, "device_config get $NAMESPACE $KEY_ALLOWLIST")

    /** `device_config get` prints "null" when the key is not set. */
    fun parse(output: String): List<String> {
        val text = output.trim()
        if (text.isEmpty() || text == "null") return emptyList()
        return text.split(',').map { it.trim() }.filter { PACKAGE.matches(it) }
    }

    /** Add [packageName] to the allowlist, keeping whatever the system already had (e.g. Google Photos). */
    fun planApply(current: List<String>, packageName: String = TARGET_PACKAGE): List<Step> {
        require(PACKAGE.matches(packageName)) { "invalid package name" }
        val merged = (current.filter { PACKAGE.matches(it) } + packageName).distinct().joinToString(",")
        return listOf(
            Step(Kind.AddToAllowlist, "device_config override $NAMESPACE $KEY_ALLOWLIST $merged"),
            Step(Kind.EnableCloudMedia, "device_config override $NAMESPACE $KEY_FEATURE true"),
        )
    }

    /** First part of undo: drop both overrides, which restores the system's own values. */
    fun planUndo(): List<Step> = listOf(
        Step(Kind.ClearAllowlistOverride, "device_config clear_override $NAMESPACE $KEY_ALLOWLIST"),
        Step(Kind.ClearFeatureOverride, "device_config clear_override $NAMESPACE $KEY_FEATURE"),
    )

    /**
     * Second part of undo, only needed when the package is still listed after the overrides are gone
     * (for example an older Zyll build wrote it with `device_config put`). Returns null when nothing is left to do.
     */
    fun planRemoveFromBase(afterClear: List<String>, packageName: String = TARGET_PACKAGE): Step? {
        if (packageName !in afterClear) return null
        val rest = afterClear.filter { it != packageName && PACKAGE.matches(it) }
        return if (rest.isEmpty()) {
            Step(Kind.DeleteBaseAllowlist, "device_config delete $NAMESPACE $KEY_ALLOWLIST")
        } else {
            Step(Kind.RemoveFromBaseAllowlist, "device_config put $NAMESPACE $KEY_ALLOWLIST ${rest.joinToString(",")}")
        }
    }

    /** Optional last step: turn wireless debugging off. The ADB connection drops right after. */
    fun disableWirelessDebugging() = Step(Kind.DisableWirelessDebugging, "settings put global adb_wifi_enabled 0")
}
