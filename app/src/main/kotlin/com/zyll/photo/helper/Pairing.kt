package com.zyll.photo.helper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PairingState {
    data object Idle : PairingState
    data object WaitingForCode : PairingState
    data object Pairing : PairingState
    data object Paired : PairingState
    data class Failed(val reason: String) : PairingState
}

/**
 * The pairing code is typed into a notification: the system "Pair device with pairing code"
 * dialog closes (and its port changes) as soon as you switch apps, so the user stays on it
 * and types the 6 digits from the notification shade.
 */
object Pairing {
    private const val CHANNEL = "adb_pairing"
    private const val NOTIFICATION_ID = 0x2ADB
    internal const val KEY_CODE = "pairing_code"

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun showCodeInputNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.pair_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val input = RemoteInput.Builder(KEY_CODE).setLabel(context.getString(R.string.pair_code_label)).build()
        val reply = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, PairingCodeReceiver::class.java).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(context, android.R.drawable.ic_menu_edit),
            context.getString(R.string.pair_enter_code),
            reply,
        ).addRemoteInput(input).build()
        nm.notify(
            NOTIFICATION_ID,
            builder(context)
                .setContentTitle(context.getString(R.string.pair_notif_title))
                .setContentText(context.getString(R.string.pair_notif_text))
                .setStyle(Notification.BigTextStyle().bigText(context.getString(R.string.pair_notif_text)))
                .addAction(action)
                .setOngoing(true)
                .build(),
        )
        _state.value = PairingState.WaitingForCode
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        if (_state.value != PairingState.Paired) _state.value = PairingState.Idle
    }

    /** Manual entry inside the app, e.g. in split screen. */
    fun pairManually(context: Context, port: Int, code: String) {
        _state.value = PairingState.Pairing
        scope.launch { doPair(context.applicationContext, code, "127.0.0.1" to port) }
    }

    internal fun doPair(context: Context, code: String, target: Pair<String, Int>?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        _state.value = PairingState.Pairing
        val result = runCatching {
            val (host, port) = target ?: LocalAdb.discoverPairingPort(context, 8_000)
                ?: error(context.getString(R.string.pair_no_port))
            LocalAdb.get(context).pair(host, port, code.trim())
        }
        result.onSuccess {
            _state.value = PairingState.Paired
            nm.notify(
                NOTIFICATION_ID,
                builder(context)
                    .setContentTitle(context.getString(R.string.pair_ok_title))
                    .setContentText(context.getString(R.string.pair_ok_text))
                    .setAutoCancel(true)
                    .setContentIntent(
                        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                            PendingIntent.getActivity(context, 1, it, PendingIntent.FLAG_IMMUTABLE)
                        },
                    )
                    .build(),
            )
        }.onFailure { e ->
            val reason = e.message ?: e.javaClass.simpleName
            _state.value = PairingState.Failed(reason)
            nm.notify(
                NOTIFICATION_ID,
                builder(context)
                    .setContentTitle(context.getString(R.string.pair_fail_title))
                    .setContentText(reason)
                    .setStyle(Notification.BigTextStyle().bigText(reason + "\n\n" + context.getString(R.string.pair_fail_hint)))
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private fun builder(context: Context): Notification.Builder =
        Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_lock)
}

/** Receives the code typed in the notification and pairs right away (a few seconds, no foreground service needed). */
class PairingCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Pairing.KEY_CODE)?.toString() ?: return
        val pending = goAsync()
        val app = context.applicationContext
        Pairing.scope.launch {
            try {
                Pairing.doPair(app, code, null)
            } finally {
                pending.finish()
            }
        }
    }
}
