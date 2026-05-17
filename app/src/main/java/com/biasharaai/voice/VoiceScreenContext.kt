package com.biasharaai.voice

/**
 * Pass [currentScreen] to [VoiceRouter.classify] using these constants so data-entry screens
 * short-circuit to [VoiceIntent.DataEntry].
 */
object VoiceScreenContext {
    const val ADD_EDIT_PRODUCT = "AddEditProduct"
    const val KIOSK_CATALOGUE = "KioskCatalogue"
    const val AGENT_FEED = "AgentFeed"
    const val CHAT = "Chat"
    const val POS = "POS"
    const val INVENTORY = "InventoryList"
    const val INSIGHTS = "Insights"
}
