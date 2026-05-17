package com.biasharaai.voice

/**
 * Stable navigation hints for voice-driven commands. UI maps these to `NavController` destinations.
 */
sealed class VoiceNavigationTarget {
    data object AgentFeed : VoiceNavigationTarget()
    data object InventoryList : VoiceNavigationTarget()
    data object Pos : VoiceNavigationTarget()
    data object Chat : VoiceNavigationTarget()
    data object Insights : VoiceNavigationTarget()
    data object Ledger : VoiceNavigationTarget()
    data class Unknown(val rawHint: String) : VoiceNavigationTarget()

    companion object {
        fun fromLooseHint(h: String): VoiceNavigationTarget {
            val s = h.lowercase()
            return when {
                s.contains("invent") || s.contains("stock") || s.contains("oricha") ||
                    s.contains("orodha") || s.contains("bidhaa") -> InventoryList
                s.contains("pos") || s.contains("sell") || s.contains("sale") || s.contains("uza") -> Pos
                s.contains("chat") || s.contains("ongea") -> Chat
                s.contains("ledger") || s.contains("daftari") || s.contains("leja") -> Ledger
                s.contains("insight") || s.contains("cash flow") || s.contains("mauzo") -> Insights
                s.contains("home") || s.contains("today") || s.contains("feed") ||
                    s.contains("nyumbani") || s.contains("leo") -> AgentFeed
                else -> Unknown(h)
            }
        }
    }
}
