package com.biasharaai.cloud

import java.net.URI
import java.util.Locale

object CloudAiGatewayPolicy {
    fun isAllowed(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        val host = uri.host?.trim()
        if (host.isNullOrBlank()) return false
        if (scheme == "https") return true
        if (scheme != "http") return false
        return isPrivateHost(host)
    }

    fun requireAllowed(url: String) {
        require(isAllowed(url)) {
            "Use https:// for cloud gateways, or http:// only for localhost/private LAN gateways."
        }
    }

    private fun isPrivateHost(host: String?): Boolean {
        val normalized = host?.trim()?.lowercase(Locale.ROOT)?.removeSurrounding("[", "]")
            ?: return false
        if (normalized == "localhost" || normalized == "::1" || normalized.endsWith(".local")) return true
        val parts = normalized.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168) ||
            parts[0] == 127
    }
}
