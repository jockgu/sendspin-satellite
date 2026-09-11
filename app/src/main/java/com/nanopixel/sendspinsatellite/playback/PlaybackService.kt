package com.nanopixel.sendspinsatellite.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
    private var session: SendspinSession? = null
    private var sessionGeneration = 0L
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val focusPolicy = AudioFocusPolicy()
    private var focusRequest: AudioFocusRequest? = null
    private var deviceCallbackRegistered = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val change = when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> AudioFocusPolicy.Change.GAIN
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> AudioFocusPolicy.Change.LOSS_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                AudioFocusPolicy.Change.LOSS_TRANSIENT_CAN_DUCK
            AudioManager.AUDIOFOCUS_LOSS -> AudioFocusPolicy.Change.LOSS
            else -> return@OnAudioFocusChangeListener
        }
        when (focusPolicy.onFocusChange(change)) {
            AudioFocusPolicy.Action.SUSPEND -> {
                session?.suspendForFocus()
                publish(status.value.copy(
                    connectionState = ConnectionState.RECOVERING,
                    detail = "Audio focus is temporarily unavailable.",
                ))
            }
            AudioFocusPolicy.Action.RESUME -> {
                session?.resumeFromFocus()
                publish(status.value.copy(
                    connectionState = ConnectionState.RECOVERING,
                    detail = "Audio focus returned. Re-buffering playback.",
                ))
            }
            AudioFocusPolicy.Action.STOP -> stopPlayback()
            else -> Unit
        }
    }
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            if (hasOutputDevice(addedDevices)) session?.requestOutputRecovery()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            if (hasOutputDevice(removedDevices)) session?.requestOutputRecovery()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                startPlaybackForeground()
                val address = intent.getStringExtra(EXTRA_SERVER_ADDRESS).orEmpty()
                val playerName = intent.getStringExtra(EXTRA_PLAYER_NAME).orEmpty()
                if (address.isBlank() || playerName.isBlank()) {
                    publish(PlaybackStatus(ConnectionState.ERROR, "A server address and player name are required."))
                    return START_NOT_STICKY
                }
                focusPolicy.onConnect()
                if (!requestAudioFocus()) {
                    focusPolicy.onFocusRequestResult(false)
                    publish(PlaybackStatus(ConnectionState.ERROR, "Audio focus is unavailable."))
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                focusPolicy.onFocusRequestResult(true)
                val activeSession = replaceSession(playerName)
                registerAudioDeviceCallback()
                publish(PlaybackStatus(ConnectionState.CONNECTING, "Opening a Sendspin connection."))
                activeSession.connect(address)
            }
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterAudioDeviceCallback()
        shutdownSession()
        abandonAudioFocus()
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
        focusPolicy.onStop()
        unregisterAudioDeviceCallback()
        shutdownSession()
        abandonAudioFocus()
        publish(PlaybackStatus())
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusListener, mainHandler)
                .build()
            focusRequest = request
            return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return audioManager.requestAudioFocus(
            focusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN,
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private fun registerAudioDeviceCallback() {
        if (deviceCallbackRegistered) return
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        deviceCallbackRegistered = true
    }

    private fun unregisterAudioDeviceCallback() {
        if (!deviceCallbackRegistered) return
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        deviceCallbackRegistered = false
    }

    private fun hasOutputDevice(devices: Array<AudioDeviceInfo>): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            devices.any { it.isSink }
        } else {
            devices.isNotEmpty()
        }
    }

    private fun replaceSession(playerName: String): SendspinSession {
        val generation = ++sessionGeneration
        session?.shutdown()
        return SendspinSession(applicationContext, playerName, object : SendspinSession.Listener {
            override fun onState(state: SendspinSession.SessionState) {
                if (generation != sessionGeneration) return
                publish(status.value.copy(connectionState = state.toConnectionState(), detail = state.detail()))
            }

            override fun onDiagnostics(diagnostics: SendspinSession.Diagnostics) {
                if (generation != sessionGeneration) return
                publish(status.value.copy(
                    serverName = diagnostics.serverName ?: status.value.serverName,
                    roundTripUs = diagnostics.roundTripUs.takeIf { it > 0 },
                    clockOffsetUs = diagnostics.offsetUs.takeIf { diagnostics.samples > 0 },
                    clockSamples = diagnostics.samples,
                    detail = diagnostics.message ?: status.value.detail,
                ))
            }
        }).also { session = it }
    }

    private fun shutdownSession() {
        sessionGeneration++
        session?.shutdown()
        session = null
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
        const val EXTRA_PLAYER_NAME = "player_name"
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

        internal fun connect(context: Context, address: String, playerName: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_CONNECT)
                    .putExtra(EXTRA_SERVER_ADDRESS, address)
                    .putExtra(EXTRA_PLAYER_NAME, playerName),
            )
        }

        internal fun stop(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_STOP))
        }

        internal val state: StateFlow<PlaybackStatus> = status.asStateFlow()
    }
}
