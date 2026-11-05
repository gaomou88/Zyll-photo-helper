package com.zyll.photo.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerAllowlistTest {
    private val pkg = PickerAllowlist.TARGET_PACKAGE

    @Test
    fun parsesUnsetAndLists() {
        assertEquals(emptyList<String>(), PickerAllowlist.parse("null\n"))
        assertEquals(emptyList<String>(), PickerAllowlist.parse(""))
        assertEquals(listOf("com.google.android.apps.photos"), PickerAllowlist.parse("com.google.android.apps.photos\n"))
    }

    @Test
    fun keepsExistingEntriesAndAppendsOnce() {
        val steps = PickerAllowlist.planApply(listOf("com.google.android.apps.photos", pkg))
        assertEquals(
            "device_config override mediaprovider allowed_cloud_providers com.google.android.apps.photos,com.zyll.photo",
            steps[0].command,
        )
        assertEquals("device_config override mediaprovider cloud_media_feature_enabled true", steps[1].command)
    }

    @Test
    fun dropsEntriesThatCouldInjectShell() {
        val parsed = PickerAllowlist.parse("com.a.b,x; reboot,\$(id),com.c.d")
        assertEquals(listOf("com.a.b", "com.c.d"), parsed)
        val cmd = PickerAllowlist.planApply(listOf("com.a.b", "a.b; reboot")).first().command
        assertEquals("device_config override mediaprovider allowed_cloud_providers com.a.b,com.zyll.photo", cmd)
    }

    @Test
    fun undoClearsBothOverrides() {
        val cmds = PickerAllowlist.planUndo().map { it.command }
        assertEquals(
            listOf(
                "device_config clear_override mediaprovider allowed_cloud_providers",
                "device_config clear_override mediaprovider cloud_media_feature_enabled",
            ),
            cmds,
        )
    }

    @Test
    fun removesLeftoverBaseEntryOnlyWhenPresent() {
        assertNull(PickerAllowlist.planRemoveFromBase(listOf("com.google.android.apps.photos")))
        assertEquals(
            "device_config put mediaprovider allowed_cloud_providers com.google.android.apps.photos",
            PickerAllowlist.planRemoveFromBase(listOf("com.google.android.apps.photos", pkg))!!.command,
        )
        assertEquals(
            "device_config delete mediaprovider allowed_cloud_providers",
            PickerAllowlist.planRemoveFromBase(listOf(pkg))!!.command,
        )
    }

    @Test
    fun everyCommandIsADeviceConfigOrAdbWifiCommand() {
        val all = PickerAllowlist.planApply(listOf("com.a.b")) + PickerAllowlist.planUndo() +
            PickerAllowlist.readAllowlist() + PickerAllowlist.disableWirelessDebugging()
        all.forEach {
            assertTrue(it.command, it.command.startsWith("device_config ") || it.command == "settings put global adb_wifi_enabled 0")
            assertTrue(it.command, it.command.none { c -> c in ";|&`$<>\n" })
        }
    }
}
