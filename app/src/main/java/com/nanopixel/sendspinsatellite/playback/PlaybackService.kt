package com.nanopixel.sendspinsatellite.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nanopixel.sendspinsatellite.MainActivity
import com.nanopixel.sendspinsatellite.R
import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.protocol.SendspinSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackService : Service() {
    private lateinit var session: SendspinSession

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        session = SendspinSession(applicationContext, object : SendspinSession.Listener {
            override fun onState(state: SendspinSession.SessionState) {
                publish(status.value.copy(connectionState = state.toConnectionState(), detail = state.detail()))
            }

            override fun onDiagnostics(diagnostics: SendspinSession.Diagnostics) {
                publish(status.value.copy(
                    serverName = diagnostics.serverName ?: status.value.serverName,
                    roundTripUs = diagnostics.roundTripUs.takeIf { it > 0 },
                    clockOffsetUs = diagnostics.offsetUs.takeIf { diagnostics.samples > 0 },
                    clockSamples = diagnostics.samples,
                    detail = diagnostics.message ?: status.value.detail,
                ))
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                startPlaybackForeground()
                publish(PlaybackStatus(ConnectionState.CONNECTING, "Opening a Sendspin connection."))
                session.connect(intent.getStringExtra(EXTRA_SERVER_ADDRESS).orEmpty())
            }
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        session.shutdown()
        publish(PlaybackStatus())
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPlaybackForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun stopPlayback() {
        session.close()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publish(nextStatus: PlaybackStatus) {
        status.value = nextStatus
        if (isForegroundService) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
    }

    private fun notification() = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(status.value.connectionState.label)
        .setContentIntent(PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ))
        .addAction(
            0,
            "Stop",
            PendingIntent.getService(
                this,
                1,
                Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private val isForegroundService: Boolean
        get() = status.value.connectionState != ConnectionState.DISCONNECTED

    companion object {
        const val ACTION_CONNECT = "com.nanopixel.sendspinsatellite.action.CONNECT"
        const val ACTION_STOP = "com.nanopixel.sendspinsatellite.action.STOP"
        const val EXTRA_SERVER_ADDRESS = "server_address"
        const val NOTIFICATION_CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1

        val status = MutableStateFlow(PlaybackStatus())

        fun SendspinSession.SessionState.toConnectionState() = when (this) {
            SendspinSession.SessionState.CONNECTING -> ConnectionState.CONNECTING
            SendspinSession.SessionState.HANDSHAKING -> ConnectionState.HANDSHAKING
            SendspinSession.SessionState.SYNCHRONISING -> ConnectionState.SYNCHRONISING
            SendspinSession.SessionState.RECOVERING -> ConnectionState.RECOVERING
            SendspinSession.SessionState.SYNCHRONISED -> ConnectionState.READY
            SendspinSession.SessionState.DISCONNECTED -> ConnectionState.DISCONNECTED
            SendspinSession.SessionState.ERROR -> ConnectionState.ERROR
        }

        fun SendspinSession.SessionState.detail() = when (this) {
            SendspinSession.SessionState.CONNECTING -> "Opening a Sendspin connection."
            SendspinSession.SessionState.HANDSHAKING -> "Establishing the Sendspin session."
            SendspinSession.SessionState.SYNCHRONISING -> "Measuring the server clock."
            SendspinSession.SessionState.RECOVERING -> "Recovering audio playback."
            SendspinSession.SessionState.SYNCHRONISED -> "Clock synchronised. Native PCM playback is ready."
            SendspinSession.SessionState.DISCONNECTED -> "Disconnected from Sendspin server."
            SendspinSession.SessionState.ERROR -> "The Sendspin connection failed."
        }

        internal fun connect(context: Context, address: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_CONNECT)
                    .putExtra(EXTRA_SERVER_ADDRESS, address),
            )
        }

        internal fun stop(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_STOP))
        }

        internal val state: StateFlow<PlaybackStatus> = status.asStateFlow()
    }
}
