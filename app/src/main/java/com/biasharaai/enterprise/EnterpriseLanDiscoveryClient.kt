package com.biasharaai.enterprise

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredEnterpriseService(
    val endpointUrl: String,
    val host: String,
    val port: Int,
)

@Singleton
class EnterpriseLanDiscoveryClient @Inject constructor() {
    suspend fun discover(timeoutMillis: Long = 3_000L): Result<DiscoveredEnterpriseService> =
        withContext(Dispatchers.IO) {
            runCatching {
                DatagramSocket(null).use { socket ->
                    socket.reuseAddress = true
                    socket.broadcast = true
                    socket.soTimeout = RECEIVE_TIMEOUT_MS
                    socket.bind(InetSocketAddress(0))
                    val probe = DISCOVERY_PROBE.toByteArray(Charsets.UTF_8)
                    discoveryTargets().forEach { address ->
                        socket.send(
                            DatagramPacket(
                                probe,
                                probe.size,
                                InetAddress.getByName(address),
                                DISCOVERY_PORT,
                            ),
                        )
                    }
                    val deadline = System.currentTimeMillis() + timeoutMillis
                    val buffer = ByteArray(2048)
                    while (System.currentTimeMillis() < deadline) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                        } catch (_: SocketTimeoutException) {
                            continue
                        }
                        val service = parseResponse(packet) ?: continue
                        return@runCatching service
                    }
                    error("No Enterprise sync service found on this LAN.")
                }
            }
        }

    private fun parseResponse(packet: DatagramPacket): DiscoveredEnterpriseService? {
        val text = packet.data.decodeToString(0, packet.length)
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (json.optString("service") != SERVICE_NAME) return null
        val port = json.optInt("httpPort", DEFAULT_HTTP_PORT).takeIf { it in 1..65535 }
            ?: DEFAULT_HTTP_PORT
        val path = json.optString("path", DEFAULT_PATH).ifBlank { DEFAULT_PATH }
        val host = json.optString("host").ifBlank { packet.address.hostAddress.orEmpty() }
        if (host.isBlank()) return null
        return DiscoveredEnterpriseService(
            endpointUrl = "http://$host:$port$path",
            host = host,
            port = port,
        )
    }

    private fun discoveryTargets(): List<String> {
        val targets = linkedSetOf("255.255.255.255")
        runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses.asSequence() }
                .mapNotNull { it.broadcast?.hostAddress }
                .forEach { targets += it }
        }
        return targets.toList()
    }

    private companion object {
        const val SERVICE_NAME = "biashara-enterprise-sync"
        const val DISCOVERY_PROBE = "BIASHARA_ENTERPRISE_DISCOVERY_V1"
        const val DISCOVERY_PORT = 8099
        const val DEFAULT_HTTP_PORT = 8088
        const val DEFAULT_PATH = "/v1/enterprise/sync"
        const val RECEIVE_TIMEOUT_MS = 500
    }
}
