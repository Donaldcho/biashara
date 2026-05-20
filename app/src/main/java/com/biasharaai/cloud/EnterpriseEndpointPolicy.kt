package com.biasharaai.cloud

import java.net.URI
import java.util.Locale

object EnterpriseEndpointPolicy {
    fun isAllowed(url: String, deploymentModeName: String? = null): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme == "https") return true
        if (scheme != "http") return false
        return EnterpriseDeploymentMode.parse(deploymentModeName) == EnterpriseDeploymentMode.ON_PREMISE &&
            isPrivateOnPremHost(uri.host)
    }

    fun errorMessage(deploymentModeName: String? = null): String =
        if (EnterpriseDeploymentMode.parse(deploymentModeName) == EnterpriseDeploymentMode.ON_PREMISE) {
            "Use https://, or http:// with localhost/private LAN hosts for on-premise Enterprise testing."
        } else {
            "Only https:// endpoints are allowed."
        }

    private fun isPrivateOnPremHost(host: String?): Boolean {
        val normalized = host?.trim()?.lowercase(Locale.ROOT)?.removeSurrounding("[", "]")
            ?: return false
        if (normalized == "localhost" || normalized == "::1" || normalized.endsWith(".local")) return true
        val parts = normalized.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 127)
    }
}
