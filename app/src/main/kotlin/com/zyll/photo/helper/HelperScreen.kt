package com.zyll.photo.helper

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val HELP_URL = "https://gaomou88.github.io/Zyll-photo-help/"

private sealed interface Stage {
    data object Home : Stage
    data class Disclosure(val action: Action) : Stage
    data class Pair(val action: Action) : Stage
    data class Run(val action: Action) : Stage
    data class Done(val action: Action) : Stage
}

@Composable
fun HelperScreen() {
    var stage by remember { mutableStateOf<Stage>(Stage.Home) }
    val log = remember { mutableStateListOf<String>() }
    val home = { log.clear(); stage = Stage.Home }

    BackHandler(enabled = stage != Stage.Home) { home() }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        when (val s = stage) {
            Stage.Home -> Home(onPick = { stage = Stage.Disclosure(it) })
            is Stage.Disclosure -> Disclosure(s.action, onAgree = { stage = Stage.Pair(s.action) }, onCancel = home)
            is Stage.Pair -> PairStep(onConnected = { stage = Stage.Run(s.action) }, onCancel = home)
            is Stage.Run -> RunStep(s.action, log, onDone = { stage = Stage.Done(s.action) })
            is Stage.Done -> DoneStep(s.action, log, onClose = home)
        }
    }
}

@Composable
private fun Home(onPick: (Action) -> Unit) {
    val context = LocalContext.current
    val installed = remember { isZyllInstalled(context) }

    Text(stringResource(R.string.home_intro))
    if (!installed) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_not_installed))
                OutlinedButton(onClick = { openStore(context) }) { Text(stringResource(R.string.home_get_zyll)) }
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_enable_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.home_enable_text), style = MaterialTheme.typography.bodyMedium)
            Button(enabled = installed, onClick = { onPick(Action.Enable) }) { Text(stringResource(R.string.home_enable_button)) }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_disable_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.home_disable_text), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { onPick(Action.Disable) }) { Text(stringResource(R.string.home_disable_button)) }
        }
    }
    TextButton(onClick = { openUrl(context, HELP_URL) }) { Text(stringResource(R.string.home_help)) }
    Text(stringResource(R.string.home_privacy), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Disclosure(action: Action, onAgree: () -> Unit, onCancel: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (action == Action.Enable) {
        Text(stringResource(R.string.disc_why_title), fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.disc_why_text))
    }
    Text(stringResource(R.string.disc_what_title), fontWeight = FontWeight.SemiBold)
    Text(stringResource(if (action == Action.Enable) R.string.disc_what_enable else R.string.disc_what_disable))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (previewSteps(action) + PickerAllowlist.disableWirelessDebugging()).forEach { step ->
                Column {
                    Text(step.kind.describe(context), style = MaterialTheme.typography.bodySmall)
                    Text(step.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Text(stringResource(R.string.disc_undo_text))
    Text(stringResource(R.string.disc_you_title), fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.disc_you_text))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = agreed, onCheckedChange = { agreed = it })
        Text(stringResource(R.string.disc_agree))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(enabled = agreed, onClick = onAgree) { Text(stringResource(R.string.disc_continue)) }
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun PairStep(onConnected: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pairing by Pairing.state.collectAsState()
    var status by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("") }
    var manualCode by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Pairing.showCodeInputNotification(context)
        openWirelessDebugging(context)
    }

    fun tryConnect(quiet: Boolean = false) {
        if (!quiet) status = context.getString(R.string.pair_connecting)
        scope.launch {
            val result = try {
                val ok = withContext(Dispatchers.IO) { LocalAdb.get(context).let { it.isConnected || it.connectTls(context, 8_000) } }
                if (ok) {
                    Pairing.cancel(context)
                    onConnected()
                    ""
                } else {
                    context.getString(R.string.pair_no_service)
                }
            } catch (e: AdbPairingRequiredException) {
                context.getString(R.string.pair_required)
            } catch (e: Exception) {
                context.getString(R.string.pair_connect_failed, e.message ?: e.javaClass.simpleName)
            }
            if (!quiet || result.isEmpty()) status = result
        }
    }

    LaunchedEffect(Unit) { tryConnect(quiet = true) }
    LaunchedEffect(pairing) { if (pairing == PairingState.Paired) tryConnect() }

    Text(stringResource(R.string.pair_title), fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.pair_steps))
    Text(stringResource(R.string.pair_dev_options), style = MaterialTheme.typography.bodySmall)
    Button(onClick = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Pairing.showCodeInputNotification(context)
            openWirelessDebugging(context)
        }
    }) { Text(stringResource(R.string.pair_start)) }

    when (val p = pairing) {
        PairingState.WaitingForCode -> Text(stringResource(R.string.pair_waiting))
        PairingState.Pairing -> Text(stringResource(R.string.pair_pairing))
        PairingState.Paired -> Text(stringResource(R.string.pair_paired))
        is PairingState.Failed -> Text(stringResource(R.string.pair_failed, p.reason), color = MaterialTheme.colorScheme.error)
        PairingState.Idle -> Unit
    }

    OutlinedButton(onClick = { tryConnect() }) { Text(stringResource(R.string.pair_connect_direct)) }
    if (status.isNotEmpty()) Text(status)

    TextButton(onClick = { showManual = !showManual }) {
        Text(stringResource(if (showManual) R.string.pair_manual_hide else R.string.pair_manual_show))
    }
    if (showManual) {
        Text(stringResource(R.string.pair_manual_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            manualPort, { manualPort = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.pair_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
        )
        OutlinedTextField(
            manualCode, { manualCode = it.filter(Char::isDigit).take(6) }, label = { Text(stringResource(R.string.pair_code_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
        )
        Button(
            enabled = manualPort.isNotEmpty() && manualCode.length == 6,
            onClick = { Pairing.pairManually(context, manualPort.toInt(), manualCode) },
        ) { Text(stringResource(R.string.pair_manual_button)) }
    }
    OutlinedButton(onClick = { Pairing.cancel(context); onCancel() }) { Text(stringResource(R.string.cancel)) }
}

@Composable
private fun RunStep(action: Action, log: MutableList<String>, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var turnOffAdb by remember { mutableStateOf(true) }

    Text(stringResource(R.string.run_title), fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.run_text))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = turnOffAdb, onCheckedChange = { turnOffAdb = it })
        Text(stringResource(R.string.run_turn_off_adb))
    }
    Button(enabled = !running, onClick = {
        running = true
        log.clear()
        scope.launch {
            val append: (String) -> Unit = { line -> scope.launch { log += line } }
            val ok = withContext(Dispatchers.IO) {
                when (action) {
                    Action.Enable -> applyAllowlist(context, turnOffAdb, append)
                    Action.Disable -> undoAllowlist(context, turnOffAdb, append)
                }
            }
            running = false
            if (ok) onDone()
        }
    }) {
        Text(
            stringResource(
                when {
                    running -> R.string.run_running
                    action == Action.Enable -> R.string.home_enable_button
                    else -> R.string.home_disable_button
                },
            ),
        )
    }
    LogCard(log)
}

@Composable
private fun DoneStep(action: Action, log: List<String>, onClose: () -> Unit) {
    val context = LocalContext.current
    if (action == Action.Enable) {
        Text(stringResource(R.string.done_enable_title), fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.done_enable_text))
        Button(onClick = { openPickerSettings(context) }) { Text(stringResource(R.string.done_open_picker_settings)) }
    } else {
        Text(stringResource(R.string.done_disable_title), fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.done_disable_text))
    }
    Text(stringResource(R.string.done_uninstall_text))
    OutlinedButton(onClick = { openAppDetails(context) }) { Text(stringResource(R.string.done_uninstall_button)) }
    TextButton(onClick = onClose) { Text(stringResource(R.string.done_back)) }
    LogCard(log)
}

@Composable
private fun LogCard(log: List<String>) {
    if (log.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Text(log.joinToString("\n"), Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}

private fun isZyllInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(PickerAllowlist.TARGET_PACKAGE, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

private fun start(context: Context, intent: Intent, fallback: Intent? = null) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        fallback?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}

private fun openUrl(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun openStore(context: Context) = start(
    context,
    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${PickerAllowlist.TARGET_PACKAGE}")),
    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${PickerAllowlist.TARGET_PACKAGE}")),
)

private fun openWirelessDebugging(context: Context) = start(
    context,
    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).putExtra(":settings:fragment_args_key", "toggle_adb_wireless"),
    Intent(Settings.ACTION_SETTINGS),
)

/** The photo picker's settings page (AOSP MediaProvider); falls back to opening the picker itself. */
private fun openPickerSettings(context: Context) = start(
    context,
    Intent("android.provider.action.PICK_IMAGES_SETTINGS"),
    Intent(MediaStore.ACTION_PICK_IMAGES),
)

private fun openAppDetails(context: Context) = start(
    context,
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
)
