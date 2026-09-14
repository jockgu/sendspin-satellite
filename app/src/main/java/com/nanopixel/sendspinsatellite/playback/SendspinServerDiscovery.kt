package com.nanopixel.sendspinsatellite.playback

import android.content.Context
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.DiscoveryRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ext.SdkExtensions
import android.util.Log
import java.util.concurrent.Executor

/**
 * One bounded browse for Sendspin services. All callbacks are serialized onto the supplied
 * handler so that stopping a scan makes late framework callbacks harmless.
 */
class SendspinServerDiscovery(
    context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val listener: Listener,
) {
    interface Listener {
        fun onStarted()
        fun onFinished(servers: List<DiscoveredServer>)
        fun onFailed(detail: String)
    }

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private val connectivityManager = appContext.getSystemService(android.net.ConnectivityManager::class.java)
    private val callbackExecutor = Executor { command ->
        if (Looper.myLooper() == handler.looper) command.run() else handler.post(command)
    }
    private val servers = linkedMapOf<String, DiscoveredServer>()
    private val resolving = linkedMapOf<String, NsdManager.ResolveListener>()
    private val lostServiceIds = mutableSetOf<String>()

    private var scanEpoch = 0L
    private var running = false
    private var browsing = false
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var serviceInfoCallback: NsdManager.ServiceInfoCallback? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var finishRunnable: Runnable? = null

    fun start(network: Network) {
        stop()
        val epoch = ++scanEpoch
        running = true
        listener.onStarted()

        try {
            acquireMulticastLockIfNeeded(network)
            if (supportsModernDiscovery()) {
                startModern(epoch, network)
            } else {
                startLegacy(epoch, network)
            }
        } catch (_: SecurityException) {
            fail(epoch, "Local network access was denied.")
            return
        } catch (_: RuntimeException) {
            fail(epoch, "Local service discovery is unavailable.")
            return
        }

        val finish = Runnable { finishScan(epoch) }
        finishRunnable = finish
        handler.postDelayed(finish, DISCOVERY_WINDOW_MS)
    }

    fun stop() {
        scanEpoch++
        running = false
        finishRunnable?.let(handler::removeCallbacks)
        finishRunnable = null
        stopBrowsing()
        if (supportsResolutionCancellation()) {
            resolving.values.forEach { resolveListener ->
                runCatching { nsdManager?.stopServiceResolution(resolveListener) }
            }
        }
        resolving.clear()
        lostServiceIds.clear()
        servers.clear()
        releaseMulticastLock()
    }

    private fun startModern(epoch: Long, network: Network) {
        val manager = nsdManager ?: throw IllegalStateException("NSD unavailable")
        val builder = DiscoveryRequest.Builder(SERVICE_TYPE).setNetwork(network)
        if (supportsNoPickerFlag()) {
            builder.setFlags(DiscoveryRequest.FLAG_NO_PICKER)
        }
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                if (!isCurrent(epoch)) return
                val id = serviceInfo.serviceName.orEmpty()
                if (id.isBlank()) return
                val metadata = sendspinDiscoveryMetadata(id, serviceInfo.attributes)
                if (metadata == null) {
                    Log.w(TAG, "ignored server service=$id without a valid path TXT record")
                    return
                }
                val addresses = serviceInfo.hostAddresses
                val host = preferredSendspinHost(addresses)
                val url = sendspinWebSocketUrl(host, serviceInfo.port, metadata.path) ?: return
                lostServiceIds.remove(id)
                val addressText = addresses.mapNotNull { it.hostAddress }.distinct()
                Log.i(TAG, "resolved service=$id addresses=$addressText selected=$url")
                servers[id] = DiscoveredServer(id, metadata.name, url, addressText)
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                fail(epoch, "Local service discovery could not start.")
            }

            override fun onServiceLost() {
                if (isCurrent(epoch)) servers.clear()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (!isCurrent(epoch)) return
                serviceInfo.serviceName?.let(servers::remove)
            }

            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        serviceInfoCallback = callback
        browsing = true
        manager.registerServiceInfoCallback(builder.build(), callbackExecutor, callback)
    }

    @Suppress("DEPRECATION")
    private fun startLegacy(epoch: Long, network: Network) {
        val manager = nsdManager ?: throw IllegalStateException("NSD unavailable")
        val callback = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!isCurrent(epoch)) return
                val id = serviceInfo.serviceName.orEmpty()
                if (id.isBlank() || resolving.containsKey(id) || servers.containsKey(id)) return
                lostServiceIds.remove(id)
                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        postIfCurrent(epoch) {
                            resolving.remove(id)
                            if (lostServiceIds.contains(id)) return@postIfCurrent
                            val metadata = sendspinDiscoveryMetadata(id, resolved.attributes)
                            if (metadata == null) {
                                Log.w(TAG, "ignored server service=$id without a valid path TXT record")
                                return@postIfCurrent
                            }
                            val host = resolved.host
                            val url = sendspinWebSocketUrl(host, resolved.port, metadata.path)
                                ?: return@postIfCurrent
                            val addresses = listOfNotNull(host?.hostAddress)
                            Log.i(TAG, "resolved service=$id addresses=$addresses selected=$url")
                            servers[id] = DiscoveredServer(id, metadata.name, url, addresses)
                        }
                    }

                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        postIfCurrent(epoch) { resolving.remove(id) }
                    }
                }
                resolving[id] = resolveListener
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        manager.resolveService(serviceInfo, callbackExecutor, resolveListener)
                    } else {
                        manager.resolveService(serviceInfo, resolveListener)
                    }
                }.onFailure {
                    resolving.remove(id)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                postIfCurrent(epoch) {
                    val id = serviceInfo.serviceName.orEmpty()
                    if (id.isNotBlank()) {
                        lostServiceIds += id
                        servers.remove(id)
                    }
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                fail(epoch, "Local service discovery could not start.")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
        }
        discoveryListener = callback
        browsing = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, network, callbackExecutor, callback)
        } else {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, callback)
        }
    }

    private fun finishScan(epoch: Long) {
        if (!isCurrent(epoch)) return
        finishRunnable = null
        stopBrowsing()
        if (resolving.isEmpty()) {
            finishNow(epoch)
        } else {
            handler.postDelayed({ finishNow(epoch) }, RESOLUTION_GRACE_MS)
        }
    }

    private fun finishNow(epoch: Long) {
        if (!isCurrent(epoch)) return
        running = false
        resolving.clear()
        releaseMulticastLock()
        val result = servers.values.toList()
        listener.onFinished(result)
    }

    private fun fail(epoch: Long, detail: String) {
        if (!isCurrent(epoch)) return
        stop()
        listener.onFailed(detail)
    }

    private fun stopBrowsing() {
        if (!browsing) return
        browsing = false
        discoveryListener?.let { listener ->
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
        serviceInfoCallback?.let { callback ->
            runCatching { nsdManager?.unregisterServiceInfoCallback(callback) }
        }
        serviceInfoCallback = null
    }

    private fun isCurrent(epoch: Long): Boolean = running && epoch == scanEpoch

    private fun postIfCurrent(epoch: Long, action: () -> Unit) {
        handler.post {
            if (isCurrent(epoch)) action()
        }
    }

    private fun acquireMulticastLockIfNeeded(network: Network) {
        if (!needsMulticastLock(network)) return
        val wifiManager = appContext.getSystemService(WifiManager::class.java) ?: return
        multicastLock = wifiManager.createMulticastLock("sendspin-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun needsMulticastLock(network: Network): Boolean {
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) < 7
    }

    private fun supportsModernDiscovery(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 12)

    private fun supportsNoPickerFlag(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 22)

    private fun supportsResolutionCancellation(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ||
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7)

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            runCatching { if (lock.isHeld) lock.release() }
        }
        multicastLock = null
    }

    companion object {
        // NSD APIs use the two-label service type without the DNS root dot.
        const val SERVICE_TYPE = "_sendspin-server._tcp"
        private const val TAG = "SendspinDiscovery"
        private const val DISCOVERY_WINDOW_MS = 5_000L
        private const val RESOLUTION_GRACE_MS = 1_000L
    }
}
