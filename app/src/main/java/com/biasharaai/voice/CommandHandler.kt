package com.biasharaai.voice

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps classified [VoiceIntent.Command] values to [VoiceNavigationTarget] for the UI layer.
 */
@Singleton
class CommandHandler @Inject constructor() {

    fun resolveNavigationTarget(command: VoiceIntent.Command): VoiceNavigationTarget =
        when (command) {
            is VoiceIntent.Command.GoHome -> VoiceNavigationTarget.AgentFeed
            is VoiceIntent.Command.OpenInventory -> VoiceNavigationTarget.InventoryList
            is VoiceIntent.Command.ReadLastAlert -> VoiceNavigationTarget.AgentFeed
            is VoiceIntent.Command.OpenPOS ->
                VoiceNavigationTarget.Pos
            is VoiceIntent.Command.RecordSale ->
                VoiceNavigationTarget.Pos
            is VoiceIntent.Command.Navigate ->
                VoiceNavigationTarget.fromLooseHint(command.destination)
        }
}
