package io.nekohasekai.sagernet.bg

import io.nekohasekai.sagernet.PROXY_ASSISTANT_PORT
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections

class HotspotProxyRelay(
    private val vpnService: VpnService
) {
    private var scope: CoroutineScope? = null
    private var monitorJob: Job? = null
    @Volatile
    private var running = false

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var listenerAddress: String? = null

    private val clients = Collections.synchronizedSet(mutableSetOf<Socket>())

    private var lastActive = false
    private var lastAddress: String? = null
    private var lastMessage: String? = null

    @Synchronized
    fun start() {
        if (monitorJob?.isActive == true) return

        running = true
        val relayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = relayScope
        publish(false, null, "Menunggu jaringan hotspot...")

        monitorJob = relayScope.launch {
            while (isActive && running) {
                try {
                    updateListener()
                } catch (e: Exception) {
                    if (running) {
                        closeListener()
                        closeClients()
                        publish(
                            false,
                            null,
                            "Relay gagal: ${e.message ?: "kesalahan jaringan"}"
                        )
                    }
                }

                delay(2000)
            }
        }
    }

    @Synchronized
    fun stop() {
        running = false
        monitorJob?.cancel()
        monitorJob = null

        scope?.cancel()
        scope = null

        closeListener()
        closeClients()

        publish(false, null, "Proxy Assistant berhenti")
    }

    @Synchronized
    private fun updateListener() {
        if (!running) return
        val candidates = findHotspotAddresses()

        if (candidates.size != 1) {
            closeListener()
            closeClients()

            val message = if (candidates.isEmpty()) {
                "Hotspot belum terdeteksi. VPN tetap berjalan."
            } else {
                "Interface jaringan tidak dapat dipastikan. Relay dinonaktifkan."
            }

            publish(false, null, message)
            return
        }

        val address = candidates.first()

        if (listenerAddress == address && serverSocket?.isClosed == false) {
            return
        }

        closeListener()
        closeClients()

        val server = ServerSocket()

        try {
            server.reuseAddress = true
            server.bind(
                InetSocketAddress(
                    InetAddress.getByName(address),
                    PROXY_ASSISTANT_PORT
                )
            )

            serverSocket = server
            listenerAddress = address

            publish(
                true,
                address,
                "Proxy relay aktif pada $address:$PROXY_ASSISTANT_PORT"
            )

            scope?.launch {
                acceptClients(server)
            }
        } catch (e: Exception) {
            try {
                server.close()
            } catch (_: Exception) {
            }

            throw e
        }
    }

    private fun findHotspotAddresses(): Set<String> {
        val result = mutableSetOf<String>()

        val interfaces = NetworkInterface.getNetworkInterfaces()
            ?.let { Collections.list(it) }
            ?: emptyList()

        for (networkInterface in interfaces) {
            try {
                if (!networkInterface.isUp ||
                    networkInterface.isLoopback ||
                    networkInterface.isVirtual
                ) {
                    continue
                }

                for (entry in networkInterface.interfaceAddresses) {
                    val address = entry.address

                    if (address is Inet4Address &&
                        address.isSiteLocalAddress &&
                        !address.isLoopbackAddress &&
                        entry.broadcast != null
                    ) {
                        result.add(address.hostAddress ?: continue)
                    }
                }
            } catch (_: Exception) {
            }
        }

        return result
    }

    private suspend fun acceptClients(server: ServerSocket) {
        while (running && scope?.isActive == true && !server.isClosed) {
            try {
                val client = server.accept()
                clients.add(client)

                scope?.launch {
                    handleClient(client)
                } ?: run {
                    closeSocket(client)
                }
            } catch (_: SocketException) {
                break
            } catch (_: IOException) {
                if (server.isClosed) break
            }
        }
    }

    private suspend fun handleClient(client: Socket) {
        var upstream: Socket? = null

        try {
            val remote = Socket()
            upstream = remote
            clients.add(remote)

            if (!vpnService.protect(remote)) {
                throw IOException("Gagal melindungi socket relay dari VPN")
            }

            remote.connect(
                InetSocketAddress("127.0.0.1", DataStore.mixedPort),
                5000
            )

            coroutineScope {
                val upload = launch {
                    client.getInputStream().copyTo(remote.getOutputStream())
                    remote.shutdownOutput()
                }

                val download = launch {
                    remote.getInputStream().copyTo(client.getOutputStream())
                    client.shutdownOutput()
                }

                joinAll(upload, download)
            }
        } catch (e: Exception) {
            Logs.w("Proxy Assistant client relay failed", e)
        } finally {
            closeSocket(client)
            upstream?.let { closeSocket(it) }
            clients.remove(client)
            upstream?.let { clients.remove(it) }
        }
    }

    private fun closeListener() {
        val server = serverSocket
        serverSocket = null
        listenerAddress = null

        try {
            server?.close()
        } catch (_: Exception) {
        }
    }

    private fun closeClients() {
        val currentClients = synchronized(clients) {
            clients.toList()
        }

        currentClients.forEach {
            closeSocket(it)
        }

        clients.clear()
    }

    private fun closeSocket(socket: Socket) {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun publish(
        active: Boolean,
        address: String?,
        message: String
    ) {
        if (lastActive == active &&
            lastAddress == address &&
            lastMessage == message
        ) {
            return
        }

        lastActive = active
        lastAddress = address
        lastMessage = message

        vpnService.data.binder.proxyAssistantChanged(
            active,
            address,
            PROXY_ASSISTANT_PORT,
            message
        )
    }
}
