package com.biasharaai.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.DebtDao
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.enterprise.EnterpriseCatalogRepository
import com.biasharaai.whatsapp.WhatsAppIntegration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A9 — Executes owner-approved [AgentAction] rows: the bridge from agent output to real app effects.
 *
 * **SEND_SMS** / **SEND_WHATSAPP** only open user-controlled composers; the app never sends messages autonomously.
 */
sealed class ExecutionResult {
    data object Success : ExecutionResult()
    data object RequiresNavigation : ExecutionResult()
    data object UnknownVerb : ExecutionResult()
    data class Error(val message: String?) : ExecutionResult()
}

@Singleton
class AgentActionExecutor @Inject constructor(
    private val productDao: ProductDao,
    private val debtDao: DebtDao,
    private val customerDao: CustomerDao,
    private val agentActionDao: AgentActionDao,
    private val enterpriseCatalogRepository: EnterpriseCatalogRepository,
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()

    suspend fun execute(action: AgentAction): ExecutionResult {
        retainFutureDaoRefs()

        val result = withContext(Dispatchers.IO) {
            when (action.actionVerb) {
                "SEND_SMS" -> executeSendSms(action)
                "SEND_WHATSAPP" -> executeSendWhatsApp(action)
                "UPDATE_PRICE" -> executeUpdatePrice(action, requireSuggestedPrice = true)
                "REVIEW_PRICE" -> executeUpdatePrice(action, requireSuggestedPrice = false)
                "OPEN_SCREEN" -> ExecutionResult.RequiresNavigation
                "SHOW_REVIEW" -> ExecutionResult.Success
                "REVIEW_STOCK",
                "REVIEW_TRANSACTION",
                "REVIEW_CASH_FLOW",
                "REVIEW_LEDGER",
                "REVIEW_OPPORTUNITIES",
                "REVIEW_VOUCHERS",
                "EXPLORE_SERVICES",
                null,
                "",
                -> ExecutionResult.Success
                else -> ExecutionResult.UnknownVerb
            }
        }
        if (result is ExecutionResult.Success) {
            withContext(Dispatchers.IO) {
                agentActionDao.updateStatus(action.id, "EXECUTED")
            }
        }
        return result
    }

    private suspend fun executeSendWhatsApp(action: AgentAction): ExecutionResult {
        val map = parsePayloadMap(action.actionPayload) ?: return ExecutionResult.Error("Missing payload")
        val phone = (map["phone"] as? String)?.trim().orEmpty()
        val message = (map["draftMessage"] as? String)?.trim()
            ?: (map["message"] as? String)?.trim().orEmpty()
        if (message.isEmpty()) return ExecutionResult.Error("Invalid WhatsApp payload")
        return try {
            withContext(Dispatchers.Main) {
                if (!WhatsAppIntegration.sendText(context, message, phone.ifBlank { null })) {
                    error("No WhatsApp handler available")
                }
            }
            ExecutionResult.Success
        } catch (e: Exception) {
            ExecutionResult.Error(e.localizedMessage)
        }
    }

    /** Keeps [debtDao] / [customerDao] wired for upcoming obligation & customer execution paths. */
    private fun retainFutureDaoRefs() {
        debtDao.toString()
        customerDao.toString()
    }

    private suspend fun executeSendSms(action: AgentAction): ExecutionResult {
        val map = parsePayloadMap(action.actionPayload) ?: return ExecutionResult.Error("Missing payload")
        val phone = (map["phone"] as? String)?.trim().orEmpty()
        val message = (map["draftMessage"] as? String)?.trim()
            ?: (map["message"] as? String)?.trim().orEmpty()
        if (phone.isEmpty() || message.isEmpty()) {
            return ExecutionResult.Error("Invalid SMS payload")
        }
        return try {
            withContext(Dispatchers.Main) {
                val uri = Uri.parse("smsto:${Uri.encode(phone)}")
                val intent = Intent(Intent.ACTION_SENDTO, uri)
                    .putExtra("sms_body", message)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            ExecutionResult.Success
        } catch (e: Exception) {
            ExecutionResult.Error(e.localizedMessage)
        }
    }

    private suspend fun executeUpdatePrice(action: AgentAction, requireSuggestedPrice: Boolean): ExecutionResult {
        val map = parsePayloadMap(action.actionPayload)
        if (map == null) {
            return if (requireSuggestedPrice) {
                ExecutionResult.Error("Missing payload")
            } else {
                ExecutionResult.Success
            }
        }
        val newPrice = (map["suggestedPrice"] as? Number)?.toDouble()
        if (newPrice == null) {
            return if (requireSuggestedPrice) {
                ExecutionResult.Error("Missing suggestedPrice")
            } else {
                ExecutionResult.Success
            }
        }
        if (newPrice < 0) {
            return ExecutionResult.Error("Invalid price")
        }
        val productId = (map["productId"] as? Number)?.toLong()
            ?: action.relatedEntityId
            ?: return ExecutionResult.Error("Missing productId")
        val product = productDao.getProductByIdOnce(productId)
            ?: return ExecutionResult.Error("Unknown product")
        val updated = enterpriseCatalogRepository.prepareProductForLocalSave(
            existing = product,
            draft = product.copy(price = newPrice),
        )
        productDao.updateProduct(updated)
        enterpriseCatalogRepository.onProductSaved(updated, "AGENT_PRICE_UPDATE")
        return ExecutionResult.Success
    }

    private fun parsePayloadMap(json: String?): Map<String, Any>? {
        if (json.isNullOrBlank()) return null
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            gson.fromJson<Map<String, Any>>(json, type)
        } catch (_: Exception) {
            null
        }
    }
}
