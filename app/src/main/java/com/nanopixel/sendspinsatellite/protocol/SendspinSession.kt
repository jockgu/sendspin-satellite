package com.nanopixel.sendspinsatellite.protocol

import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

class SendspinSession(
    context: Context,
    private val listener: Listener,
) : WebSocketListener() {
    interface Listener {
        fun onState(state: SessionState)
        fun onDiagnostics(diagnostics: Diagnostics)
    }

    enum class SessionState { CONNECTING, HANDSHAKING, SYNCHRONISING, SYNCHRONISED, DISCONNECTED, ERROR }

    data class Diagnostics(
        val serverName: String? = null,
        val roundTripUs: Long = 0,
        val offsetUs: Long = 0,
        val samples: Int = 0,
        val message: String? = null,
    )

    private val identity = SecureIdentityStore(context).getOrCreatePrivateKey()
    private val clientId = NoiseTransport.base64Url(
        org.bouncycastle.crypto.params.X25519PrivateKeyParameters(identity, 0).generatePublicKey().encoded,
    )
    private val clock = ClockSynchronizer()
    private val httpClient = OkHttpClient()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var socket: WebSocket? = null
    private var transport: NoiseTransport? = null
    private var clientInitBytes: ByteArray? = null
    private var serverInitBytes: ByteArray? = null
    private var serverId: ByteArray? = null
    private var isActivated = false
    private var sentUnavailableState = false
    private var serverName: String? = null
    private var failed = false
    private var clockSyncTask: ScheduledFuture<*>? = null

    fun connect(address: String) {
        close()
        failed = false
        clock.reset()
        listener.onState(SessionState.CONNECTING)
        socket = httpClient.newWebSocket(Request.Builder().url(address).build(), this)
    }

    fun close() {
        clockSyncTask?.cancel(true)
        clockSyncTask = null
        socket?.close(1000, "Client disconnect")
        socket = null
        transport = null
        isActivated = false
        sentUnavailableState = false
    }

    fun shutdown() {
        close()
        scheduler.shutdownNow()
        httpClient.dispatcher.executorService.shutdown()
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        listener.onState(SessionState.HANDSHAKING)
        val init = JSONObject()
            .put("type", "client/init")
            .put("payload", JSONObject()
                .put("client_id", clientId)
                .put("version", 1)
                .put("suite", NoiseTransport.SUITE))
            .toString()
        clientInitBytes = init.toByteArray(StandardCharsets.UTF_8)
        webSocket.send(init)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val message = JSONObject(text)
            when (message.getString("type")) {
                "server/init" -> handleServerInit(text.toByteArray(StandardCharsets.UTF_8), message)
                "noise/handshake" -> handleInitialHandshake(message)
                else -> fail("Unexpected cleartext message: ${message.getString("type")}")
            }
        } catch (exception: Exception) {
            fail("Handshake failed: ${exception.message}")
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        try {
            val plainText = transport?.decrypt(bytes.toByteArray()) ?: run {
                fail("Received encrypted data before the handshake completed")
                return
            }
            require(plainText.isNotEmpty() && plainText[0].toInt() == JSON_MESSAGE_TYPE) {
                "Phase 1 accepts JSON protocol messages only"
            }
            handleEncryptedJson(String(plainText, 1, plainText.size - 1, StandardCharsets.UTF_8))
        } catch (exception: Exception) {
            fail("Protocol error: ${exception.message}")
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(code, null)
        if (!failed) {
            listener.onState(SessionState.DISCONNECTED)
            listener.onDiagnostics(
                Diagnostics(message = "Server closed the connection (code $code${reason.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()})."),
            )
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        fail(t.message ?: "WebSocket connection failed")
    }

    private fun handleServerInit(rawBytes: ByteArray, message: JSONObject) {
        require(message.getJSONObject("payload").getInt("version") == 1) { "Unsupported Sendspin version" }
        serverId = NoiseTransport.fromBase64Url(message.getJSONObject("payload").getString("server_id"))
        require(serverId?.size == 32) { "Server identity is invalid" }
        serverInitBytes = rawBytes
    }

    private fun handleInitialHandshake(message: JSONObject) {
        val remoteKey = requireNotNull(serverId) { "noise/handshake arrived before server/init" }
        val prologue = requireNotNull(clientInitBytes) + requireNotNull(serverInitBytes)
        val serverMessageOne = NoiseTransport.fromBase64Url(message.getJSONObject("payload").getString("data"))
        val responder = { legacyEphemeralKeyMix: Boolean ->
            NoiseTransport.createResponder(
                localPrivateKey = identity,
                remotePublicKey = remoteKey,
                prologue = prologue,
                serverMessageOne = serverMessageOne,
                psk = NoiseTransport.sentinelPsk(),
                legacyEphemeralKeyMix = legacyEphemeralKeyMix,
            )
        }
        // Servers that predate psk_category also used a short-lived key schedule
        // variant. Try the current protocol first; its authenticated failure is
        // the only condition under which the legacy form is attempted.
        val result = try {
            responder(false)
        } catch (standardFailure: IllegalArgumentException) {
            responder(true)
        }
        val handshakePayload = JSONObject(String(result.serverPayload, StandardCharsets.UTF_8))
        require(handshakePayload.getString("psk_id") == NoiseTransport.pskId(NoiseTransport.sentinelPsk())) {
            "This server needs pairing credentials; Phase 1 currently supports unpaired access only"
        }
        // psk_category was added to the newer Sendspin handshake format. Older
        // servers identify the sentinel PSK by psk_id alone, so default a
        // missing category to the only trust mode Phase 1 supports.
        require(handshakePayload.optString("psk_category", "sn") == "sn") { "Unexpected PSK category" }
        transport = result.transport
        socket?.send(JSONObject()
            .put("type", "noise/handshake")
            .put("payload", JSONObject().put("data", NoiseTransport.base64Url(result.response)))
            .toString())
    }

    private fun handleEncryptedJson(body: String) {
        val message = JSONObject(body)
        val payload = message.getJSONObject("payload")
        when (message.getString("type")) {
            "server/hello" -> {
                serverName = payload.getString("name")
                sendClientHello()
            }
            "server/activate" -> {
                isActivated = true
                listener.onState(SessionState.SYNCHRONISING)
                startClockSync()
            }
            "server/time" -> {
                clock.update(
                    payload.getLong("client_transmitted"),
                    payload.getLong("server_received"),
                    payload.getLong("server_transmitted"),
                    monotonicUs(),
                )
                listener.onDiagnostics(Diagnostics(serverName, clock.roundTripUs, clock.offsetUs, clock.sampleCount))
                if (clock.isConverged && !sentUnavailableState) {
                    sentUnavailableState = true
                    sendUnavailablePlayerState()
                    listener.onState(SessionState.SYNCHRONISED)
                }
            }
        }
    }

    private fun sendClientHello() {
        sendJson("client/hello", JSONObject()
            .put("name", "Sendspin Satellite (${Build.MODEL})")
            .put("device_info", JSONObject()
                .put("product_name", Build.MODEL)
                .put("manufacturer", Build.MANUFACTURER)
                .put("software_version", "0.1-alpha"))
            .put("supported_roles", JSONArray().put("player@v1"))
            .put("player@v1_support", JSONObject()
                .put("supported_formats", JSONArray().put(JSONObject()
                    .put("codec", "pcm")
                    .put("channels", 2)
                    .put("sample_rate", 48_000)
                    .put("bit_depth", 16)))
                // The server validates this as a positive maximum, even while
                // this Phase 1 client reports itself unavailable for playback.
                .put("buffer_capacity", 262_144)
                .put("supported_commands", JSONArray()))
            .put("supported_pair_methods", JSONArray().put(JSONObject().put("method", "pairing_psk")))
            .put("unpaired_access", JSONObject().put("enabled", true)))
    }

    private fun sendTimeProbe() {
        if (isActivated) {
            sendJson("client/time", JSONObject().put("client_transmitted", monotonicUs()))
        }
    }

    private fun startClockSync() {
        clockSyncTask?.cancel(true)
        sendTimeProbe()
        clockSyncTask = scheduler.scheduleAtFixedRate(
            { sendTimeProbe() },
            CLOCK_SYNC_INTERVAL_SECONDS,
            CLOCK_SYNC_INTERVAL_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    private fun sendUnavailablePlayerState() {
        sendJson("client/state", JSONObject()
            .put("available", false)
            .put("player", JSONObject()
                .put("static_delay_ms", 0)
                .put("required_lead_time_ms", 0)
                .put("min_buffer_ms", 0)
                .put("supported_commands", JSONArray())))
    }

    private fun sendJson(type: String, payload: JSONObject) {
        val body = JSONObject().put("type", type).put("payload", payload).toString().toByteArray(StandardCharsets.UTF_8)
        val encrypted = requireNotNull(transport) { "Encrypted transport is not ready" }
            .encrypt(byteArrayOf(JSON_MESSAGE_TYPE.toByte()) + body)
        socket?.send(ByteString.of(*encrypted))
    }

    private fun fail(detail: String) {
        if (failed) return
        failed = true
        listener.onState(SessionState.ERROR)
        listener.onDiagnostics(Diagnostics(message = detail))
        socket?.close(1002, "Sendspin protocol failure")
        clockSyncTask?.cancel(true)
        clockSyncTask = null
    }

    private fun monotonicUs(): Long = SystemClock.elapsedRealtimeNanos() / 1_000L

    private companion object {
        const val JSON_MESSAGE_TYPE = 0
        const val CLOCK_SYNC_INTERVAL_SECONDS = 1L
    }
}
