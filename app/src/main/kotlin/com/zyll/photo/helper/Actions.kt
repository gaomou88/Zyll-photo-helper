package com.zyll.photo.helper

import android.content.Context
import com.zyll.photo.helper.PickerAllowlist.Kind

/** What the user asked the helper to do. */
enum class Action { Enable, Disable }

fun Kind.describe(context: Context): String = context.getString(
    when (this) {
        Kind.ReadAllowlist -> R.string.step_read
        Kind.AddToAllowlist -> R.string.step_add
        Kind.EnableCloudMedia -> R.string.step_enable
        Kind.ClearAllowlistOverride -> R.string.step_clear_allowlist
        Kind.ClearFeatureOverride -> R.string.step_clear_feature
        Kind.RemoveFromBaseAllowlist -> R.string.step_remove_base
        Kind.DeleteBaseAllowlist -> R.string.step_delete_base
        Kind.DisableWirelessDebugging -> R.string.step_adb_off
    },
)

/** The commands shown on the disclosure screen before anything runs. */
fun previewSteps(action: Action): List<PickerAllowlist.Step> = when (action) {
    Action.Enable -> listOf(PickerAllowlist.readAllowlist()) +
        PickerAllowlist.planApply(emptyList()).map {
            // The real command keeps whatever entries the system already has
            if (it.kind == Kind.AddToAllowlist) it.copy(command = it.command.replace(" ${PickerAllowlist.TARGET_PACKAGE}", " <…>,${PickerAllowlist.TARGET_PACKAGE}")) else it
        }
    Action.Disable -> PickerAllowlist.planUndo() + PickerAllowlist.readAllowlist()
}

private fun runStep(context: Context, adb: LocalAdb, step: PickerAllowlist.Step, log: (String) -> Unit): String {
    log("▶ ${step.kind.describe(context)}\n$ ${step.command}")
    val r = adb.exec(step.command)
    // device_config sometimes prints "Error: ..." and still exits with 0
    if (r.exitCode != 0 || r.output.startsWith("Error")) {
        error(context.getString(R.string.log_cmd_failed, r.exitCode, r.output))
    }
    log(if (r.output.isEmpty()) "  " + context.getString(R.string.log_ok) else "  ${r.output}")
    return r.output
}

private fun readList(context: Context, adb: LocalAdb, log: (String) -> Unit): List<String> =
    PickerAllowlist.parse(runStep(context, adb, PickerAllowlist.readAllowlist(), log))

/** Returns true when Zyll Photo ends up in the allowlist. */
fun applyAllowlist(context: Context, turnOffAdb: Boolean, log: (String) -> Unit): Boolean = guarded(context, log) {
    val adb = LocalAdb.get(context)
    val current = readList(context, adb, log)
    PickerAllowlist.planApply(current).forEach { runStep(context, adb, it, log) }
    val after = readList(context, adb, log)
    check(PickerAllowlist.TARGET_PACKAGE in after) { context.getString(R.string.log_verify_failed, after.joinToString(",")) }
    log("✓ " + context.getString(R.string.log_verified))
    if (turnOffAdb) finishAndDisconnect(context, adb, log)
}

/** Returns true when Zyll Photo is no longer in the allowlist. */
fun undoAllowlist(context: Context, turnOffAdb: Boolean, log: (String) -> Unit): Boolean = guarded(context, log) {
    val adb = LocalAdb.get(context)
    PickerAllowlist.planUndo().forEach { runStep(context, adb, it, log) }
    PickerAllowlist.planRemoveFromBase(readList(context, adb, log))?.let { runStep(context, adb, it, log) }
    val after = readList(context, adb, log)
    check(PickerAllowlist.TARGET_PACKAGE !in after) { context.getString(R.string.log_verify_failed, after.joinToString(",")) }
    log("✓ " + context.getString(R.string.log_restored))
    if (turnOffAdb) finishAndDisconnect(context, adb, log)
}

private inline fun guarded(context: Context, log: (String) -> Unit, block: () -> Unit): Boolean = try {
    block()
    true
} catch (e: LocalAdb.IncompleteOutputException) {
    log("✗ " + context.getString(R.string.log_incomplete))
    false
} catch (e: Exception) {
    log("✗ " + context.getString(R.string.log_error, e.message ?: e.javaClass.simpleName))
    false
}

private fun finishAndDisconnect(context: Context, adb: LocalAdb, log: (String) -> Unit) {
    val step = PickerAllowlist.disableWirelessDebugging()
    log("▶ ${step.kind.describe(context)}\n$ ${step.command}")
    // Turning wireless debugging off drops the connection at once, so don't wait for output
    adb.execNoWait(step.command)
    runCatching { adb.disconnect() }
    log("  " + context.getString(R.string.log_disconnected))
}
