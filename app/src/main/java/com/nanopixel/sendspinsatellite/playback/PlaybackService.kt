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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nanopixel.sendspinsatellite.MainActivity
import com.nanopixel.sendspinsatellite.R
import com.nanopixel.sendspinsatellite.connection.ConnectionPreferences
import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.connection.SavedServer
import com.nanopixel.sendspinsatellite.protocol.NativePlaybackEngine
import com.nanopixel.sendspinsatellite.protocol.SendspinSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackService : Service() {
    private var session: SendspinSession? = null
    private var sessionGeneration = 0L
    private var activeServer: SavedServer? = null
    private val connectionPreferences by lazy { ConnectionPreferences(applicationContext) }
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val focusPolicy = AudioFocusPolicy()
    private var focusRequest: AudioFocusRequest? = null
    private var deviceCallbackRegistered = false
    private var networkCallbackRegistered = false
    // The first network callback is asynchronous; allow an explicitly entered
    // URL to make its initial attempt while Android reports the current state.
    // Discovery still requires a confirmed Network object below.
    private var validatedNetworkAvailable = true
    private var validatedNetwork: Network? = null
    private var serverDiscovery: SendspinServerDiscovery? = null
    private var discoveryPlayerName: String? = null
    private var discoverySelectionTimeout: Runnable? = null
    private var lastDiagnosticsLogAt = 0L
    private var lastLoggedFailure = -1
    private var lastLoggedHardResyncs = -1L
    private var lastLoggedReconnectCompletions = -1L
    private val diagnosticsCollector = AudioDiagnosticsCollector(
        clockMs = { SystemClock.elapsedRealtime() },
    )
    @Volatile private var platformDiagnosticsReady = false
    @Volatile private var platformDiagnosticsDeviceId: Int? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshValidatedNetwork()

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            refreshValidatedNetwork()
        }

        override fun onLost(network: Network) = refreshValidatedNetwork()
    }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        diagnosticsCollector.recordEvent("focus", focusChangeName(focusChange))
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
                    audioDiagnostics = diagnosticsCollector.snapshot(),
                ))
            }
            AudioFocusPolicy.Action.RESUME -> {
                session?.resumeFromFocus()
                publish(status.value.copy(
                    connectionState = ConnectionState.RECOVERING,
                    detail = "Audio focus returned. Re-buffering playback.",
                    audioDiagnostics = diagnosticsCollector.snapshot(),
                ))
            }
            AudioFocusPolicy.Action.STOP -> stopPlayback()
            else -> Unit
        }
    }
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            if (hasOutputDevice(addedDevices)) {
                diagnosticsCollector.recordEvent("route", "added=${addedDevices.size}")
                refreshAudioPlatformDiagnostics(force = true)
                session?.requestOutputRecovery()
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            if (hasOutputDevice(removedDevices)) {
                diagnosticsCollector.recordEvent("route", "removed=${removedDevices.size}")
                refreshAudioPlatformDiagnostics(force = true)
                session?.requestOutputRecovery()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connectToAddress(
                intent.getStringExtra(EXTRA_SERVER_ADDRESS).orEmpty(),
                intent.getStringExtra(EXTRA_PLAYER_NAME).orEmpty(),
                SavedServer(
                    address = intent.getStringExtra(EXTRA_SERVER_ADDRESS).orEmpty(),
                    name = intent.getStringExtra(EXTRA_SERVER_NAME),
                ),
            )
            ACTION_DISCOVER -> discoverServers(intent.getStringExtra(EXTRA_PLAYER_NAME).orEmpty())
            ACTION_SELECT_DISCOVERED -> selectDiscoveredServer(intent.getStringExtra(EXTRA_SERVER_ID).orEmpty())
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun connectToAddress(
        address: String,
        playerName: String,
        server: SavedServer = SavedServer(address),
        discoveredServer: DiscoveredServer? = null,
    ) {
        stopDiscoveryResources(clearPlayerName = true)
        activeServer = server.copy(address = address.trim())
        startPlaybackForeground()
        diagnosticsCollector.reset()
        resetPlatformDiagnostics()
        val source = if (discoveredServer == null) "manual" else "discovery"
        diagnosticsCollector.recordEvent("session", "connect target=$address source=$source")
        discoveredServer?.let { server ->
            diagnosticsCollector.recordEvent(
                "discovery-target",
                "service=${server.name} addresses=${server.addresses} selected=${server.url}",
            )
        }
        Log.i(TAG, "connect target=$address source=$source")
        refreshAudioPlatformDiagnostics(force = true)
        if (address.isBlank() || playerName.isBlank()) {
            publish(PlaybackStatus(
                connectionState = ConnectionState.ERROR,
                detail = "A server address and player name are required.",
                audioDiagnostics = diagnosticsCollector.snapshot(),
            ))
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        focusPolicy.onConnect()
        if (!requestAudioFocus()) {
            focusPolicy.onFocusRequestResult(false)
            diagnosticsCollector.recordEvent("focus", "request denied")
            publish(PlaybackStatus(
                connectionState = ConnectionState.ERROR,
                detail = "Audio focus is unavailable.",
                audioDiagnostics = diagnosticsCollector.snapshot(),
            ))
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        focusPolicy.onFocusRequestResult(true)
        val activeSession = replaceSession(playerName)
        activeSession.setNetworkAvailable(validatedNetworkAvailable)
        registerAudioDeviceCallback()
        registerNetworkCallback()
        publish(PlaybackStatus(
            connectionState = ConnectionState.CONNECTING,
            detail = "Opening a Sendspin connection.",
            server = activeServer,
            audioDiagnostics = diagnosticsCollector.snapshot(),
        ))
        activeSession.connect(address)
    }

    private fun discoverServers(playerName: String) {
        if (playerName.isBlank()) {
            publish(PlaybackStatus(
                connectionState = ConnectionState.ERROR,
                detail = "A player name is required to discover a server.",
            ))
            return
        }
        focusPolicy.onStop()
        unregisterAudioDeviceCallback()
        shutdownSession()
        abandonAudioFocus()
        stopDiscoveryResources(clearPlayerName = true)
        activeServer = null
        startPlaybackForeground()
        diagnosticsCollector.reset()
        resetPlatformDiagnostics()
        diagnosticsCollector.recordEvent("discovery", "requested")
        discoveryPlayerName = playerName
        registerNetworkCallback()
        publish(PlaybackStatus(
            connectionState = ConnectionState.DISCOVERING,
            detail = "Looking for a local Sendspin server.",
            audioDiagnostics = diagnosticsCollector.snapshot(),
        ))
        applyValidatedNetwork()
    }

    private fun startDiscoveryIfPossible() {
        if (serverDiscovery != null || discoveryPlayerName == null) return
        val network = validatedNetwork
        if (!validatedNetworkAvailable || network == null || !isLocalNetwork(network)) {
            publish(status.value.copy(
                connectionState = ConnectionState.DISCOVERING,
                detail = "Waiting for a validated local network.",
                discoveredServers = emptyList(),
            ))
            return
        }
        val discovery = SendspinServerDiscovery(
            applicationContext,
            mainHandler,
            object : SendspinServerDiscovery.Listener {
                override fun onStarted() {
                    diagnosticsCollector.recordEvent("discovery", "started")
                    publish(status.value.copy(detail = "Looking for a local Sendspin server."))
                }

                override fun onFinished(servers: List<DiscoveredServer>) {
                    if (discoveryPlayerName == null || status.value.connectionState != ConnectionState.DISCOVERING) return
                    serverDiscovery = null
                    diagnosticsCollector.recordEvent("discovery", "resolved=${servers.size}")
                    handleDiscoveryResult(servers)
                }

                override fun onFailed(detail: String) {
                    if (discoveryPlayerName == null || status.value.connectionState != ConnectionState.DISCOVERING) return
                    serverDiscovery = null
                    diagnosticsCollector.recordEvent("discovery", "failed")
                    finishDiscovery(detail)
                }
            },
        )
        serverDiscovery = discovery
        discovery.start(network)
    }

    private fun handleDiscoveryResult(servers: List<DiscoveredServer>) {
        when (val decision = decideServerDiscovery(servers)) {
            ServerDiscoveryDecision.None -> finishDiscovery(
                "No local Sendspin server was found. Enter an address or try again.",
            )
            is ServerDiscoveryDecision.AutoConnect -> {
                val playerName = discoveryPlayerName ?: return
                connectToAddress(
                    decision.server.url,
                    playerName,
                    SavedServer(decision.server.url, decision.server.name),
                    decision.server,
                )
            }
            is ServerDiscoveryDecision.Select -> {
                discoverySelectionTimeout?.let(mainHandler::removeCallbacks)
                val timeout = Runnable {
                    if (status.value.connectionState == ConnectionState.DISCOVERING) {
                        finishDiscovery("Server selection timed out. Enter an address or try again.")
                    }
                }
                discoverySelectionTimeout = timeout
                publish(status.value.copy(
                    detail = "Select a discovered Sendspin server.",
                    discoveredServers = decision.servers,
                ))
                mainHandler.postDelayed(timeout, DISCOVERY_SELECTION_TIMEOUT_MS)
            }
        }
    }

    private fun selectDiscoveredServer(serverId: String) {
        if (status.value.connectionState != ConnectionState.DISCOVERING) return
        val server = status.value.discoveredServers.firstOrNull { it.id == serverId } ?: return
        val playerName = discoveryPlayerName ?: return
        connectToAddress(server.url, playerName, SavedServer(server.url, server.name), server)
    }

    private fun finishDiscovery(detail: String) {
        stopDiscoveryResources(clearPlayerName = true)
        unregisterNetworkCallback()
        publish(PlaybackStatus(
            connectionState = ConnectionState.DISCONNECTED,
            detail = detail,
            audioDiagnostics = diagnosticsCollector.snapshot(),
        ))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopDiscoveryResources(clearPlayerName: Boolean) {
        serverDiscovery?.stop()
        serverDiscovery = null
        discoverySelectionTimeout?.let(mainHandler::removeCallbacks)
        discoverySelectionTimeout = null
        if (clearPlayerName) discoveryPlayerName = null
    }

    override fun onDestroy() {
        val hadActiveResources = session != null || serverDiscovery != null || networkCallbackRegistered
        stopDiscoveryResources(clearPlayerName = true)
        unregisterAudioDeviceCallback()
        unregisterNetworkCallback()
        shutdownSession()
        abandonAudioFocus()
        if (hadActiveResources) publish(PlaybackStatus())
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
        stopDiscoveryResources(clearPlayerName = true)
        unregisterAudioDeviceCallback()
        unregisterNetworkCallback()
        shutdownSession()
        abandonAudioFocus()
        activeServer = null
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

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        networkCallbackRegistered = true
        refreshValidatedNetwork()
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        connectivityManager.unregisterNetworkCallback(networkCallback)
        networkCallbackRegistered = false
    }

    private fun refreshValidatedNetwork() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyValidatedNetwork()
        } else {
            mainHandler.post(::applyValidatedNetwork)
        }
    }

    private fun applyValidatedNetwork() {
        val network: Network? = connectivityManager.activeNetwork
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        val available = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val changed = available != validatedNetworkAvailable ||
            (available && network != validatedNetwork)
        validatedNetworkAvailable = available
        validatedNetwork = network.takeIf { available }
        if (!changed) {
            if (status.value.connectionState == ConnectionState.DISCOVERING) startDiscoveryIfPossible()
            return
        }
        diagnosticsCollector.recordEvent(
            "network",
            if (available) "validated network available" else "validated network unavailable",
        )
        session?.setNetworkAvailable(available)
        if (status.value.connectionState == ConnectionState.DISCOVERING) {
            serverDiscovery?.stop()
            serverDiscovery = null
            discoverySelectionTimeout?.let(mainHandler::removeCallbacks)
            discoverySelectionTimeout = null
            val local = available && isLocalNetwork(network)
            publish(status.value.copy(
                connectionState = ConnectionState.DISCOVERING,
                detail = if (local) {
                    "Looking for a local Sendspin server."
                } else {
                    "Waiting for a validated local network."
                },
                discoveredServers = emptyList(),
            ))
            if (local) startDiscoveryIfPossible()
            return
        }
        if (status.value.connectionState != ConnectionState.DISCONNECTED) {
            publish(status.value.copy(
                connectionState = ConnectionState.RECOVERING,
                detail = if (available) {
                    "Validated network available. Reconnecting."
                } else {
                    "Waiting for a validated network."
                },
            ))
        }
    }

    private fun isLocalNetwork(network: Network?): Boolean {
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
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
                diagnosticsCollector.recordEvent("session-state", state.name)
                val connectionState = state.toConnectionState()
                if (connectionState in setOf(
                        ConnectionState.READY,
                        ConnectionState.BUFFERING,
                        ConnectionState.PLAYING,
                    )
                ) {
                    activeServer?.let(connectionPreferences::saveServer)
                }
                publish(status.value.copy(
                    connectionState = connectionState,
                    detail = if (!validatedNetworkAvailable && state != SendspinSession.SessionState.DISCONNECTED) {
                        "Waiting for a validated network."
                    } else {
                        state.detail()
                    },
                    server = activeServer,
                    audioDiagnostics = diagnosticsCollector.snapshot(),
                ))
            }

            override fun onDiagnostics(diagnostics: SendspinSession.Diagnostics) {
                if (generation != sessionGeneration) return
                val nativeSnapshot = diagnostics.nativeSnapshot
                val audioDiagnostics = if (nativeSnapshot == null) {
                    diagnosticsCollector.snapshot()
                } else {
                    refreshAudioPlatformDiagnostics(nativeSnapshot.outputDeviceId)
                    diagnosticsCollector.updateNative(nativeSnapshot)
                }
                publish(status.value.copy(
                    serverName = diagnostics.serverName ?: status.value.serverName,
                    roundTripUs = diagnostics.roundTripUs.takeIf { it > 0 } ?: status.value.roundTripUs,
                    clockOffsetUs = diagnostics.offsetUs.takeIf { diagnostics.samples > 0 }
                        ?: status.value.clockOffsetUs,
                    clockSamples = diagnostics.samples.takeIf { it > 0 } ?: status.value.clockSamples,
                    detail = diagnostics.message ?: status.value.detail,
                    nativeDiagnostics = nativeSnapshot ?: status.value.nativeDiagnostics,
                    audioDiagnostics = audioDiagnostics,
                ))
                nativeSnapshot?.let(::logDiagnostics)
            }

            override fun onNowPlaying(snapshot: NowPlayingSnapshot) {
                if (generation != sessionGeneration) return
                publish(status.value.copy(nowPlaying = snapshot))
            }

            override fun onArtwork(snapshot: ArtworkSnapshot) {
                if (generation != sessionGeneration) return
                publish(status.value.copy(artwork = snapshot))
            }
        }).also { session = it }
    }

    private fun logDiagnostics(snapshot: NativePlaybackEngine.Diagnostics) {
        val now = SystemClock.elapsedRealtime()
        val important = snapshot.lastFailure != lastLoggedFailure ||
            snapshot.hardResyncs != lastLoggedHardResyncs ||
            snapshot.reconnectCompletions != lastLoggedReconnectCompletions
        if (!important && now - lastDiagnosticsLogAt < DIAGNOSTICS_LOG_INTERVAL_MS) return
        lastDiagnosticsLogAt = now
        lastLoggedFailure = snapshot.lastFailure
        lastLoggedHardResyncs = snapshot.hardResyncs
        lastLoggedReconnectCompletions = snapshot.reconnectCompletions
        Log.i(
            TAG,
            "state=${snapshot.state} generation=${snapshot.generation} " +
                "buffer=${snapshot.queuedFrames}/${snapshot.fifoCapacityFrames} " +
                "underruns=${snapshot.underruns} outputRestarts=${snapshot.outputRestarts} " +
                "xrun=${snapshot.outputXruns} output=${snapshot.outputSampleRate}Hz/" +
                "${snapshot.outputChannelCount}ch burst=${snapshot.outputFramesPerBurst} " +
                "bufferFrames=${snapshot.outputBufferSizeFrames}/${snapshot.outputBufferCapacityFrames} " +
                "latencyUs=${snapshot.outputLatencyUs} " +
                "hardResyncs=${snapshot.hardResyncs} reconnects=" +
                "${snapshot.reconnectCompletions}/${snapshot.reconnectAttempts} " +
                "lastFailure=${snapshot.lastFailure}",
        )
    }

    private fun refreshAudioPlatformDiagnostics(
        nativeOutputDeviceId: Int? = null,
        force: Boolean = false,
    ) {
        val requestedDeviceId = nativeOutputDeviceId?.takeIf { it >= 0 }
        mainHandler.post {
            if (!force && platformDiagnosticsReady && requestedDeviceId == platformDiagnosticsDeviceId) return@post
            runCatching {
                diagnosticsCollector.updatePlatform(
                    AudioPlatformDiagnosticsReader.read(this, audioManager, requestedDeviceId),
                )
            }.onSuccess {
                platformDiagnosticsDeviceId = requestedDeviceId
                platformDiagnosticsReady = true
                if (status.value.connectionState != ConnectionState.DISCONNECTED) {
                    publish(status.value.copy(audioDiagnostics = diagnosticsCollector.snapshot()))
                }
            }.onFailure {
                diagnosticsCollector.recordEvent("diagnostics", "platform snapshot unavailable")
            }
        }
    }

    private fun resetPlatformDiagnostics() {
        platformDiagnosticsDeviceId = null
        platformDiagnosticsReady = false
    }

    private fun focusChangeName(change: Int): String = when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> "gain"
        AudioManager.AUDIOFOCUS_LOSS -> "loss"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "loss transient"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "loss transient can duck"
        else -> "change=$change"
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
        .setSmallIcon(R.drawable.ic_stat_sendspin)
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
        const val ACTION_DISCOVER = "com.nanopixel.sendspinsatellite.action.DISCOVER"
        const val ACTION_SELECT_DISCOVERED = "com.nanopixel.sendspinsatellite.action.SELECT_DISCOVERED"
        const val ACTION_STOP = "com.nanopixel.sendspinsatellite.action.STOP"
        const val EXTRA_SERVER_ADDRESS = "server_address"
        const val EXTRA_PLAYER_NAME = "player_name"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_SERVER_ID = "server_id"
        const val NOTIFICATION_CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1
        private const val TAG = "PlaybackService"
        private const val DIAGNOSTICS_LOG_INTERVAL_MS = 30_000L
        private const val DISCOVERY_SELECTION_TIMEOUT_MS = 30_000L

        val status = MutableStateFlow(PlaybackStatus())

        fun SendspinSession.SessionState.toConnectionState() = when (this) {
            SendspinSession.SessionState.CONNECTING -> ConnectionState.CONNECTING
            SendspinSession.SessionState.HANDSHAKING -> ConnectionState.HANDSHAKING
            SendspinSession.SessionState.SYNCHRONISING -> ConnectionState.SYNCHRONISING
            SendspinSession.SessionState.RECOVERING -> ConnectionState.RECOVERING
            SendspinSession.SessionState.SYNCHRONISED -> ConnectionState.READY
            SendspinSession.SessionState.BUFFERING -> ConnectionState.BUFFERING
            SendspinSession.SessionState.PLAYING -> ConnectionState.PLAYING
            SendspinSession.SessionState.DISCONNECTED -> ConnectionState.DISCONNECTED
            SendspinSession.SessionState.ERROR -> ConnectionState.ERROR
        }

        fun SendspinSession.SessionState.detail() = when (this) {
            SendspinSession.SessionState.CONNECTING -> "Opening a Sendspin connection."
            SendspinSession.SessionState.HANDSHAKING -> "Establishing the Sendspin session."
            SendspinSession.SessionState.SYNCHRONISING -> "Measuring the server clock."
            SendspinSession.SessionState.RECOVERING -> "Recovering audio playback."
            SendspinSession.SessionState.SYNCHRONISED -> "Clock synchronised. Native PCM playback is ready."
            SendspinSession.SessionState.BUFFERING -> "Buffering audio playback."
            SendspinSession.SessionState.PLAYING -> "Playing audio."
            SendspinSession.SessionState.DISCONNECTED -> "Disconnected from Sendspin server."
            SendspinSession.SessionState.ERROR -> "The Sendspin connection failed."
        }

        internal fun connect(
            context: Context,
            address: String,
            playerName: String,
            serverName: String? = null,
        ) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_CONNECT)
                    .putExtra(EXTRA_SERVER_ADDRESS, address)
                    .putExtra(EXTRA_PLAYER_NAME, playerName)
                    .putExtra(EXTRA_SERVER_NAME, serverName),
            )
        }

        internal fun discover(context: Context, playerName: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_DISCOVER)
                    .putExtra(EXTRA_PLAYER_NAME, playerName),
            )
        }

        internal fun selectDiscoveredServer(context: Context, serverId: String) {
            context.startService(
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_SELECT_DISCOVERED)
                    .putExtra(EXTRA_SERVER_ID, serverId),
            )
        }

        internal fun stop(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_STOP))
        }

        internal val state: StateFlow<PlaybackStatus> = status.asStateFlow()
    }
}
