package com.biasharaai.desktop

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.room.withTransaction
import com.biasharaai.BuildConfig
import com.biasharaai.data.local.db.AppDatabase
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.BusinessProfile
import com.biasharaai.data.local.db.BusinessProfileDao
import com.biasharaai.data.local.db.Customer
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.Debt
import com.biasharaai.data.local.db.DebtDao
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerRepository
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.SaleLineItem
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.ServiceItem
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.data.local.db.ServicePriceMode
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.data.local.db.TransactionNoteTypes
import com.biasharaai.data.local.db.TransactionType
import com.biasharaai.media.ProductPhotoStore
import com.biasharaai.money.RegionalDefaults
import com.biasharaai.service.ServiceTokenCodec
import com.biasharaai.sync.RequestSigner
import com.biasharaai.sync.SyncProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

private val DESKTOP_BRIDGE_JSON = "application/json; charset=utf-8".toMediaType()

data class DesktopBridgeSession(
    val baseUrl: String,
    val sessionKey: String,
    val deviceName: String,
    val pairedAtMillis: Long,
    val protocolVersion: String = SyncProtocol.CURRENT_VERSION,
)

data class DesktopBridgeActionResult(
    val success: Boolean,
    val message: String,
)

data class DesktopDiscoveryResult(
    val baseUrl: String,
    val token: String,
    val businessName: String,
    val host: String,
    val port: Int,
)

data class DesktopCatalogSyncResult(
    val sent: Int,
    val failed: Int,
    val skippedImages: Int,
    val message: String,
)

data class DesktopReconciliationResult(
    val productsCreated: Int,
    val productsUpdated: Int,
    val servicesCreated: Int,
    val servicesUpdated: Int,
    val servicesUploaded: Int,
    val salesImported: Int,
    val stockChangesMerged: Int,
    val settingsApplied: Boolean,
    val mobileTransactionsUploaded: Int,
    val message: String,
) {
    val hasChanges: Boolean
        get() = productsCreated > 0 ||
            productsUpdated > 0 ||
            servicesCreated > 0 ||
            servicesUpdated > 0 ||
            servicesUploaded > 0 ||
            salesImported > 0 ||
            stockChangesMerged > 0 ||
            settingsApplied ||
            mobileTransactionsUploaded > 0
}

@Singleton
class DesktopBridgeClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val appSettingsDao: AppSettingsDao,
    private val businessProfileDao: BusinessProfileDao,
    private val productDao: ProductDao,
    private val serviceItemDao: ServiceItemDao,
    private val transactionDao: TransactionDao,
    private val saleLineItemDao: SaleLineItemDao,
    private val customerDao: CustomerDao,
    private val debtDao: DebtDao,
    private val ledgerRepository: LedgerRepository,
    private val productPhotoStore: ProductPhotoStore,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    private val scanHttp = http.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val discoveryHttp = http.newBuilder()
        .connectTimeout(250, TimeUnit.MILLISECONDS)
        .readTimeout(500, TimeUnit.MILLISECONDS)
        .writeTimeout(500, TimeUnit.MILLISECONDS)
        .callTimeout(800, TimeUnit.MILLISECONDS)
        .build()

    fun currentSession(): DesktopBridgeSession? {
        val baseUrl = prefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val sessionKey = prefs.getString(KEY_SESSION_KEY, null)?.takeIf { it.isNotBlank() } ?: return null
        return DesktopBridgeSession(
            baseUrl = baseUrl,
            sessionKey = sessionKey,
            deviceName = prefs.getString(KEY_DEVICE_NAME, null).orEmpty().ifBlank { defaultDeviceName() },
            pairedAtMillis = prefs.getLong(KEY_PAIRED_AT, 0L),
            protocolVersion = prefs.getString(KEY_PROTOCOL_VERSION, SyncProtocol.CURRENT_VERSION)
                .orEmpty()
                .ifBlank { SyncProtocol.CURRENT_VERSION },
        )
    }

    fun disconnect() {
        prefs.edit().clear().apply()
    }

    suspend fun discoverDesktop(timeoutMillis: Long = 6_000L): DesktopDiscoveryResult? =
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + timeoutMillis.coerceAtLeast(1_000L)
            runCatching { listenForDiscoveryBroadcast(deadline) }.getOrNull()
                ?: probeLocalDesktopBridge(deadline)
        }

    private fun listenForDiscoveryBroadcast(deadline: Long): DesktopDiscoveryResult? {
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), DISCOVERY_PORT))
            socket.soTimeout = DISCOVERY_RECEIVE_TIMEOUT_MS
            val buffer = ByteArray(DISCOVERY_BUFFER_BYTES)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                val raw = runCatching {
                    socket.receive(packet)
                    String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                }.getOrNull() ?: continue
                parseDiscovery(raw)?.let { return it }
            }
            return null
        }
    }

    private suspend fun probeLocalDesktopBridge(deadline: Long): DesktopDiscoveryResult? = coroutineScope {
        val hosts = localSubnetCandidates()
        for (batch in hosts.chunked(DISCOVERY_PROBE_PARALLELISM)) {
            if (System.currentTimeMillis() >= deadline) return@coroutineScope null
            val found = batch
                .map { host -> async(Dispatchers.IO) { probeDiscoveryHost(host) } }
                .awaitAll()
                .firstOrNull()
            if (found != null) return@coroutineScope found
        }
        null
    }

    private fun probeDiscoveryHost(host: String): DesktopDiscoveryResult? {
        val baseUrl = runCatching { normalizeBaseUrl("http://$host:8865") }.getOrNull()
            ?: return null
        val request = Request.Builder()
            .url("$baseUrl/api/phone/discovery")
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", userAgent())
            .build()
        return runCatching {
            discoveryHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                parseDiscoveryResponse(response.body?.string().orEmpty(), host)
            }
        }.getOrNull()
    }

    private fun localSubnetCandidates(): List<String> {
        val out = LinkedHashSet<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val item = interfaces.nextElement()
                if (!item.isUp || item.isLoopback || item.isVirtual) continue
                item.interfaceAddresses.forEach { interfaceAddress ->
                    val address = interfaceAddress.address as? Inet4Address ?: return@forEach
                    val host = address.hostAddress ?: return@forEach
                    if (!isAllowedDesktopHost(host) || host.startsWith("127.")) return@forEach
                    appendSubnetHosts(out, host)
                }
            }
        }
        return out.toList()
    }

    private fun appendSubnetHosts(out: LinkedHashSet<String>, ownHost: String) {
        val parts = ownHost.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        val ownOctet = parts[3]
        val octets = (1..254)
            .filter { it != ownOctet }
            .sortedWith(
                compareBy<Int> {
                    when (it) {
                        1 -> 0
                        254 -> 1
                        else -> 2
                    }
                }.thenBy { Math.abs(it - ownOctet) },
            )
        octets.forEach { octet -> out.add("$prefix.$octet") }
    }

    private fun parseDiscoveryResponse(raw: String, probedHost: String): DesktopDiscoveryResult? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val token = root.optString("token").trim()
        if (token.isBlank()) return null
        val payload = root.optString("payload").trim()
        parsePairingPayload(payload)?.let { fields ->
            return DesktopDiscoveryResult(
                baseUrl = fields.baseUrl,
                token = fields.token,
                businessName = root.optString("businessName").trim(),
                host = Uri.parse(fields.baseUrl).host.orEmpty(),
                port = Uri.parse(fields.baseUrl).port.takeIf { it > 0 } ?: 8865,
            )
        }
        val baseUrl = runCatching {
            normalizeBaseUrl(root.optString("localUrl").trim().ifBlank { "http://$probedHost:8865" })
        }.getOrNull() ?: return null
        return DesktopDiscoveryResult(
            baseUrl = baseUrl,
            token = token,
            businessName = root.optString("businessName").trim(),
            host = Uri.parse(baseUrl).host.orEmpty().ifBlank { probedHost },
            port = Uri.parse(baseUrl).port.takeIf { it > 0 } ?: 8865,
        )
    }

    suspend fun pair(baseUrlInput: String, tokenInput: String): DesktopBridgeSession =
        withContext(Dispatchers.IO) {
            val fields = resolvePairingFields(baseUrlInput, tokenInput)
            val deviceName = defaultDeviceName()
            val payload = JSONObject()
                .put("token", fields.token)
                .put("deviceName", deviceName)
                .put("supportedProtocolVersions", JSONArray(SyncProtocol.SUPPORTED_VERSIONS.toList()))
                .toString()
                .toRequestBody(DESKTOP_BRIDGE_JSON)
            val request = Request.Builder()
                .url("${fields.baseUrl}/api/phone/pair")
                .post(payload)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent())
                .build()
            val text = execute(request)
            val response = JSONObject(text)
            val sessionKey = response.optString("sessionKey").trim()
            require(sessionKey.isNotBlank()) { "Desktop did not return a session key." }
            val protocolVersion = response.optString("protocolVersion")
                .trim()
                .ifBlank { SyncProtocol.CURRENT_VERSION }
            require(SyncProtocol.SUPPORTED_VERSIONS.contains(protocolVersion)) {
                "Desktop sync protocol $protocolVersion is not supported by this mobile app."
            }
            DesktopBridgeSession(
                baseUrl = fields.baseUrl,
                sessionKey = sessionKey,
                deviceName = deviceName,
                pairedAtMillis = System.currentTimeMillis(),
                protocolVersion = protocolVersion,
            ).also { session ->
                prefs.edit()
                    .putString(KEY_BASE_URL, session.baseUrl)
                    .putString(KEY_SESSION_KEY, session.sessionKey)
                    .putString(KEY_DEVICE_NAME, session.deviceName)
                    .putLong(KEY_PAIRED_AT, session.pairedAtMillis)
                    .putString(KEY_PROTOCOL_VERSION, session.protocolVersion)
                    .apply()
            }
        }

    suspend fun sendScan(rawValue: String): DesktopBridgeActionResult =
        withContext(Dispatchers.IO) {
            val session = currentSession()
                ?: return@withContext DesktopBridgeActionResult(false, "Pair with the desktop first.")
            val clean = rawValue.trim()
            if (clean.isBlank()) {
                return@withContext DesktopBridgeActionResult(false, "Scan value is empty.")
            }
            runCatching {
                val payload = JSONObject()
                    .put("sessionKey", session.sessionKey)
                    .put("deviceName", session.deviceName)
                    .put("rawValue", clean)
                    .toString()
                    .toRequestBody(DESKTOP_BRIDGE_JSON)
                execute(authorizedRequest(session, "/api/phone/scan", payload), scanHttp)
                DesktopBridgeActionResult(true, "Sent scan to desktop: $clean")
            }.getOrElse { throwable ->
                DesktopBridgeActionResult(false, throwable.cleanMessage("Scan could not reach desktop."))
            }
        }

    suspend fun syncCatalog(): DesktopCatalogSyncResult =
        withContext(Dispatchers.IO) {
            val session = currentSession()
                ?: return@withContext DesktopCatalogSyncResult(
                    sent = 0,
                    failed = 0,
                    skippedImages = 0,
                    message = "Pair with the desktop first.",
                )
            val uploadedTransactions = uploadRecentMobileTransactions(session)
            val pushed = pushProductCatalog(session, includeImages = true)
            val pulled = pullDesktopChanges(session, includeImages = true)
            DesktopCatalogSyncResult(
                sent = pushed.sent,
                failed = pushed.failed,
                skippedImages = pushed.skippedImages,
                message = pushed.messageWith(uploadedTransactions, pulled),
            )
        }

    suspend fun reconcile(): DesktopReconciliationResult =
        withContext(Dispatchers.IO) {
            val session = currentSession()
                ?: return@withContext DesktopReconciliationResult(
                    productsCreated = 0,
                    productsUpdated = 0,
                    servicesCreated = 0,
                    servicesUpdated = 0,
                    servicesUploaded = 0,
                    salesImported = 0,
                    stockChangesMerged = 0,
                    settingsApplied = false,
                    mobileTransactionsUploaded = 0,
                    message = "Pair with the desktop first.",
                )
            val uploadedTransactions = uploadRecentMobileTransactions(session)
            val shouldPushProducts = shouldAutoPushProductCatalog()
            val pushed = if (shouldPushProducts) {
                pushProductCatalog(session, includeImages = false).also { result ->
                    if (result.failed == 0) markAutoProductCatalogPushed()
                }
            } else {
                ProductPushResult(sent = 0, failed = 0, skippedImages = 0)
            }
            val pulled = pullDesktopChanges(session, includeImages = false)
            pulled.copy(
                mobileTransactionsUploaded = uploadedTransactions,
                message = pushed.messageWith(uploadedTransactions, pulled),
            )
        }

    private suspend fun pushProductCatalog(
        session: DesktopBridgeSession,
        includeImages: Boolean,
    ): ProductPushResult {
        val products = productDao.getProductsList()
        var sent = 0
        var failed = 0
        var skippedImages = 0
        products.forEach { product ->
            runCatching {
                val image = if (includeImages) readProductImage(product) else null
                if (image?.skipped == true) skippedImages += 1
                val json = product.toDesktopJson(session, image)
                execute(authorizedRequest(session, "/api/phone/product-sync", json.toString().toRequestBody(DESKTOP_BRIDGE_JSON)))
                sent += 1
            }.onFailure {
                failed += 1
            }
        }
        return ProductPushResult(sent, failed, skippedImages)
    }

    private suspend fun uploadRecentMobileTransactions(session: DesktopBridgeSession): Int {
        val remembered = prefs.getStringSet(KEY_UPLOADED_TX_IDS, emptySet()).orEmpty().toMutableSet()
        val uploadedNow = mutableSetOf<String>()
        val transactions = transactionDao.getTransactionsList()
            .asSequence()
            .filter { it.type == TransactionType.INCOME }
            .filter { !it.receiptNumber.orEmpty().startsWith("DESK-", ignoreCase = true) }
            .filter { !it.saleGroupId.orEmpty().startsWith("DESKTOP-", ignoreCase = true) }
            .filter { it.id > 0L && it.id.toString() !in remembered }
            .take(MAX_TRANSACTION_UPLOADS_PER_RECONCILE)
            .toList()
        transactions.forEach { transaction ->
            runCatching {
                val json = transaction.toDesktopTransactionJson(session)
                val response = execute(authorizedRequest(session, "/api/phone/transaction-sync", json.toString().toRequestBody(DESKTOP_BRIDGE_JSON)))
                val acknowledgement = runCatching { JSONObject(response) }.getOrNull()
                val stockApplied = acknowledgement?.let {
                    it.optBoolean("stockApplied", !it.optBoolean("duplicate", false))
                } ?: false
                if (stockApplied) {
                    acknowledgeUploadedStockMovements(json)
                }
                uploadedNow += transaction.id.toString()
            }
        }
        if (uploadedNow.isNotEmpty()) {
            prefs.edit().putStringSet(KEY_UPLOADED_TX_IDS, remembered + uploadedNow).apply()
        }
        return uploadedNow.size
    }

    private suspend fun pullDesktopChanges(
        session: DesktopBridgeSession,
        includeImages: Boolean,
    ): DesktopReconciliationResult {
        val mobileSettings = mobileSettingsForSync()
        val stockChanges = stockChangesForSync(session)
        val services = mobileServicesForSync()
        val payload = JSONObject()
            .put("sessionKey", session.sessionKey)
            .put("deviceName", session.deviceName)
            .put("includeImages", includeImages)
            .put("settings", mobileSettings.json)
            .put("settingsFingerprint", mobileSettings.fingerprint)
            .put("lastSettingsFingerprint", prefs.getString(KEY_LAST_SETTINGS_FINGERPRINT, "").orEmpty())
            .put("stockChanges", stockChanges)
            .put("services", services)
            .toString()
            .toRequestBody(DESKTOP_BRIDGE_JSON)
        val response = execute(authorizedRequest(session, "/api/phone/reconcile", payload))
        val root = JSONObject(response)
        val settingsApplied = applyDesktopSettings(
            root.optJSONObject("settings"),
            root.optString("settingsFingerprint").trim(),
        )
        val productResult = applyDesktopProducts(root.optJSONArray("products") ?: JSONArray())
        val serviceResult = applyDesktopServices(root.optJSONArray("services") ?: JSONArray())
        val salesImported = applyDesktopTransactions(root.optJSONArray("transactions") ?: JSONArray())
        return DesktopReconciliationResult(
            productsCreated = productResult.created,
            productsUpdated = productResult.updated,
            servicesCreated = serviceResult.created,
            servicesUpdated = serviceResult.updated,
            servicesUploaded = root.optInt("serviceChangesApplied", 0).coerceAtLeast(0),
            salesImported = salesImported,
            stockChangesMerged = root.optInt("stockChangesApplied", 0).coerceAtLeast(0),
            settingsApplied = settingsApplied,
            mobileTransactionsUploaded = 0,
            message = "",
        )
    }

    private fun ProductPushResult.messageWith(
        uploadedTransactions: Int,
        pulled: DesktopReconciliationResult,
    ): String =
        buildString {
            if (sent > 0 || failed > 0 || skippedImages > 0) {
                append("Synced ")
                append(sent)
                append(if (sent == 1) " product" else " products")
                append(" to desktop.")
            } else {
                append("Checked desktop.")
            }
            if (failed > 0) append(" $failed failed.")
            if (skippedImages > 0) append(" $skippedImages large or external images skipped.")
            if (uploadedTransactions > 0) {
                append(" Uploaded ")
                append(uploadedTransactions)
                append(if (uploadedTransactions == 1) " mobile receipt." else " mobile receipts.")
            }
            if (pulled.settingsApplied) {
                append(" Synced business settings.")
            }
            if (pulled.stockChangesMerged > 0) {
                append(" Merged ")
                append(pulled.stockChangesMerged)
                append(if (pulled.stockChangesMerged == 1) " stock change." else " stock changes.")
            }
            if (pulled.servicesUploaded > 0) {
                append(" Synced ")
                append(pulled.servicesUploaded)
                append(if (pulled.servicesUploaded == 1) " mobile service to desktop." else " mobile services to desktop.")
            }
            val desktopServiceChanges = pulled.servicesCreated + pulled.servicesUpdated
            if (desktopServiceChanges > 0) {
                append(" Applied ")
                append(desktopServiceChanges)
                append(if (desktopServiceChanges == 1) " desktop service." else " desktop services.")
            }
            val desktopChanges = pulled.productsCreated + pulled.productsUpdated + pulled.salesImported + pulled.stockChangesMerged
            if (desktopChanges > 0) {
                append(" Applied ")
                append(desktopChanges)
                append(if (desktopChanges == 1) " desktop change." else " desktop changes.")
            } else if (desktopServiceChanges == 0 && pulled.servicesUploaded == 0 && failed == 0 && !pulled.settingsApplied) {
                append(" Both devices are current.")
            }
        }

    private suspend fun mobileServicesForSync(): JSONArray {
        val services = JSONArray()
        serviceItemDao.getAllOnce().forEach { service ->
            services.put(
                JSONObject()
                    .put("mobileServiceId", service.id.toString())
                    .put("desktopServiceId", prefs.getString(serviceRemoteIdKey(service.id), "").orEmpty())
                    .put("name", service.name)
                    .put("description", service.description.orEmpty())
                    .put("category", service.category.orEmpty())
                    .put("priceCents", service.basePrice.toCents())
                    .put("priceMode", service.priceMode)
                    .put("durationMinutes", service.durationMinutes)
                    .put("warrantyDays", service.warrantyDays)
                    .put("visibleInKiosk", service.visibleInKiosk)
                    .put("updatedAt", service.updatedAt),
            )
        }
        return services
    }

    private suspend fun stockChangesForSync(session: DesktopBridgeSession): JSONArray {
        val changes = JSONArray()
        productDao.getProductsList().forEach { product ->
            val baseline = stockBaseline(product.id) ?: return@forEach
            if (baseline == product.stockQuantity) return@forEach
            val mutationId = sha256(
                listOf(
                    session.deviceName,
                    product.id.toString(),
                    baseline.toString(),
                    product.stockQuantity.toString(),
                ).joinToString("\n"),
            )
            changes.put(
                JSONObject()
                    .put("mobileProductId", product.id.toString())
                    .put("name", product.name)
                    .put("barcode", product.barcodeValue.orEmpty())
                    .put("stockBaseKnown", true)
                    .put("stockBase", baseline)
                    .put("stock", product.stockQuantity)
                    .put("mutationId", mutationId),
            )
        }
        return changes
    }

    private fun acknowledgeUploadedStockMovements(transaction: JSONObject) {
        val quantities = mutableMapOf<Long, Int>()
        val lines = transaction.optJSONArray("lines") ?: return
        for (index in 0 until lines.length()) {
            val line = lines.optJSONObject(index) ?: continue
            if (!line.optString("kind").equals("PRODUCT", ignoreCase = true)) continue
            val productId = line.optString("mobileProductId").toLongOrNull() ?: continue
            val quantity = line.optInt("quantity", 0).coerceAtLeast(0)
            if (quantity > 0) quantities[productId] = quantities.getOrDefault(productId, 0) + quantity
        }
        if (quantities.isEmpty()) return
        val editor = prefs.edit()
        quantities.forEach { (productId, quantity) ->
            val baseline = stockBaseline(productId) ?: return@forEach
            editor.putInt(stockBaselineKey(productId), (baseline - quantity).coerceAtLeast(0))
        }
        editor.apply()
    }

    private fun stockBaseline(productId: Long): Int? {
        val key = stockBaselineKey(productId)
        return if (prefs.contains(key)) prefs.getInt(key, 0).coerceAtLeast(0) else null
    }

    private fun stockBaselineKey(productId: Long): String = "$KEY_STOCK_BASELINE_PREFIX$productId"

    private fun shouldAutoPushProductCatalog(now: Long = System.currentTimeMillis()): Boolean {
        val last = prefs.getLong(KEY_LAST_AUTO_PRODUCT_PUSH_AT, 0L)
        return now - last >= AUTO_PRODUCT_PUSH_INTERVAL_MS
    }

    private fun markAutoProductCatalogPushed(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_AUTO_PRODUCT_PUSH_AT, now).apply()
    }

    private suspend fun mobileSettingsForSync(): SyncedSettingsPayload {
        val appSettings = appSettingsDao.getSettingsSync() ?: AppSettings()
        val profile = businessProfileDao.get()
        val businessName = cleanBusinessName(profile?.businessName)
            .ifBlank { cleanBusinessName(appSettings.businessName) }
        val ownerName = profile?.ownerName?.trim().orEmpty()
        val receiptFooter = cleanReceiptFooter(appSettings.receiptFooter)
        val configured = businessName.isNotBlank() ||
            ownerName.isNotBlank() ||
            receiptFooter.isNotBlank() ||
            appSettings.taxRate != 0.0
        val rawCurrency = appSettings.currencyCode.trim().uppercase(Locale.ROOT)
        val currency = if (configured || rawCurrency != RegionalDefaults.CURRENCY_CODE) {
            normalizeCurrencyCode(rawCurrency)
        } else {
            ""
        }
        val json = JSONObject()
            .put("businessName", businessName)
            .put("ownerName", ownerName)
            .put("currency", currency)
            .put("currencySymbol", if (currency.isBlank()) "" else currencySymbolFor(currency, appSettings.currencySymbol))
            .put("taxPercent", appSettings.taxRate)
            .put("receiptFooter", receiptFooter)
            .put("whatsappPhoneNumberId", "")
            .put("whatsappCatalogId", "")
            .put("whatsappDefaultCountryCode", "")
            .put("whatsappGraphVersion", "")
        return SyncedSettingsPayload(
            json = json,
            fingerprint = syncedSettingsFingerprint(json),
        )
    }

    private suspend fun applyDesktopSettings(settings: JSONObject?, fingerprintFromDesktop: String): Boolean {
        if (settings == null) return false
        val normalized = normalizedSettingsJson(settings)
        val fingerprint = fingerprintFromDesktop.ifBlank { syncedSettingsFingerprint(normalized) }
        val before = mobileSettingsForSync()
        val changed = fingerprint != before.fingerprint
        database.withTransaction {
            if (changed) {
                val currentApp = appSettingsDao.getSettingsSync() ?: AppSettings()
                val currency = normalizeCurrencyCode(normalized.optString("currency"))
                val nextApp = currentApp.copy(
                    businessName = normalized.optString("businessName").trim(),
                    currencyCode = currency,
                    currencySymbol = if (currency.isBlank()) "" else currencySymbolFor(currency, normalized.optString("currencySymbol")),
                    taxRate = normalized.optDouble("taxPercent", 0.0),
                    receiptFooter = normalized.optString("receiptFooter").trim(),
                )
                if (nextApp != currentApp) {
                    appSettingsDao.updateSettings(nextApp)
                }
                val currentProfile = businessProfileDao.get() ?: BusinessProfile()
                val nextProfile = currentProfile.copy(
                    businessName = normalized.optString("businessName").trim(),
                    ownerName = normalized.optString("ownerName").trim(),
                    lastUpdatedAt = System.currentTimeMillis(),
                )
                if (nextProfile != currentProfile) {
                    businessProfileDao.upsert(nextProfile)
                }
            }
        }
        prefs.edit().putString(KEY_LAST_SETTINGS_FINGERPRINT, fingerprint).apply()
        return changed
    }

    private fun normalizedSettingsJson(input: JSONObject): JSONObject {
        val currency = normalizeCurrencyCode(input.optString("currency"))
        return JSONObject()
            .put("businessName", cleanBusinessName(input.optString("businessName")))
            .put("ownerName", input.optString("ownerName").trim())
            .put("currency", currency)
            .put("currencySymbol", if (currency.isBlank()) "" else currencySymbolFor(currency, input.optString("currencySymbol")))
            .put("taxPercent", input.optDouble("taxPercent", 0.0))
            .put("receiptFooter", cleanReceiptFooter(input.optString("receiptFooter")))
            .put("whatsappPhoneNumberId", input.optString("whatsappPhoneNumberId").trim())
            .put("whatsappCatalogId", input.optString("whatsappCatalogId").trim())
            .put("whatsappDefaultCountryCode", input.optString("whatsappDefaultCountryCode").trim())
            .put("whatsappGraphVersion", input.optString("whatsappGraphVersion").trim())
    }

    private fun syncedSettingsFingerprint(settings: JSONObject): String =
        sha256(
            listOf(
                cleanBusinessName(settings.optString("businessName")),
                settings.optString("ownerName").trim(),
                normalizeCurrencyCode(settings.optString("currency")),
                String.format(Locale.US, "%.4f", settings.optDouble("taxPercent", 0.0)),
                cleanReceiptFooter(settings.optString("receiptFooter")),
                settings.optString("whatsappPhoneNumberId").trim(),
                settings.optString("whatsappCatalogId").trim(),
                settings.optString("whatsappDefaultCountryCode").trim(),
                settings.optString("whatsappGraphVersion").trim(),
            ).joinToString("\n"),
        )

    private fun cleanBusinessName(value: String?): String {
        val clean = value?.trim().orEmpty()
        return if (
            clean.isBlank() ||
            clean.equals("My Business", ignoreCase = true) ||
            clean.equals("Biashara AI Pro", ignoreCase = true)
        ) {
            ""
        } else {
            clean
        }
    }

    private fun cleanReceiptFooter(value: String?): String {
        val clean = value?.trim().orEmpty()
        return if (
            clean.isBlank() ||
            clean.equals("Thank you!", ignoreCase = true) ||
            clean.equals("Thank you for your business.", ignoreCase = true)
        ) {
            ""
        } else {
            clean
        }
    }

    private fun normalizeCurrencyCode(value: String?): String =
        value?.trim().orEmpty()
            .uppercase(Locale.ROOT)
            .replace("-", " ")
            .replace("_", " ")
            .replace(Regex("\\s+"), " ")
            .let { normalized ->
                when {
                    normalized.isBlank() -> ""
                    normalized == "FCFA" ||
                        normalized == "CFA" ||
                        normalized == "CFA FRANC" ||
                        normalized == "FRANC CFA" ||
                        normalized == "FRANCE CFA" ||
                        normalized.contains("CENTRAL AFRICAN CFA") ||
                        normalized.contains("BEAC") -> "XAF"
                    normalized.contains("WEST AFRICAN CFA") ||
                        normalized.contains("BCEAO") ||
                        normalized == "F CFA" -> "XOF"
                    normalized.length >= 3 -> normalized.take(3)
                    else -> normalized
                }
            }

    private fun currencySymbolFor(code: String, preferred: String? = null): String {
        val fallback = preferred?.trim().orEmpty()
        return when (code.uppercase(Locale.ROOT)) {
            "XAF", "XOF" -> "FCFA"
            "KES" -> "KSh"
            "TZS" -> "TSh"
            "UGX" -> "USh"
            "RWF" -> "RF"
            else -> fallback.ifBlank { code }
        }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private suspend fun Transaction.toDesktopTransactionJson(session: DesktopBridgeSession): JSONObject {
        val lineItems = saleLineItemDao.getLineItemsForTransactionOnce(id)
        val customer = customerId?.let { customerDao.getCustomerById(it) }
        val lines = JSONArray()
        lineItems.forEach { line ->
            val product = productDao.getProductByIdOnce(line.productId)
            lines.put(
                JSONObject()
                    .put("kind", "PRODUCT")
                    .put("mobileProductId", line.productId.toString())
                    .put("name", line.productName)
                    .put("barcode", product?.barcodeValue.orEmpty())
                    .put("category", product?.category.orEmpty())
                    .put("quantity", line.quantity)
                    .put("unitCents", line.unitPrice.toCents())
                    .put("lineTotalCents", line.lineTotal.toCents()),
            )
        }
        val paidNow = when {
            amountPaid > 0.0 -> amountPaid
            balanceDue > 0.0 -> (amount - balanceDue).coerceAtLeast(0.0)
            paymentMethod == "CREDIT" -> 0.0
            else -> amount
        }
        val subtotal = (productSubtotal + serviceSubtotal)
            .takeIf { it > 0.0 }
            ?: (amount - taxAmount).coerceAtLeast(0.0)
        return JSONObject()
            .put("sessionKey", session.sessionKey)
            .put("deviceName", session.deviceName)
            .put("mobileTransactionId", id.toString())
            .put("receiptNumber", receiptNumber.orEmpty())
            .put("createdAtMillis", date)
            .put("type", type.name)
            .put("customerName", customer?.name.orEmpty())
            .put("customerPhone", customer?.phone.orEmpty())
            .put("description", description)
            .put("paymentMethod", paymentMethod)
            .put("subtotalCents", subtotal.toCents())
            .put("productSubtotalCents", productSubtotal.toCents())
            .put("serviceSubtotalCents", serviceSubtotal.toCents())
            .put("taxCents", taxAmount.toCents())
            .put("totalCents", amount.toCents())
            .put("paidCents", paidNow.toCents())
            .put("balanceCents", balanceDue.toCents())
            .put("lines", lines)
    }

    private suspend fun applyDesktopProducts(products: JSONArray): ProductApplyResult {
        var created = 0
        var updated = 0
        val syncedStock = mutableMapOf<Long, Int>()
        database.withTransaction {
            for (index in 0 until products.length()) {
                val item = products.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isBlank()) continue
                val barcode = item.optString("barcode").trim()
                val category = item.optString("category").trim().ifBlank { null }
                val price = item.optLong("priceCents", 0L).centsToDouble()
                val cost = item.optLong("costCents", 0L).centsToDouble()
                val stock = item.optInt("stock", 0).coerceAtLeast(0)
                val description = item.optString("description").trim().ifBlank { null }
                val existing = barcode.takeIf { it.isNotBlank() }?.let { productDao.getProductByBarcodeOnce(it) }
                    ?: productDao.findProductByExactName(name)
                val imagePath = desktopImagePath(item, existing?.imageUrl)
                if (existing == null) {
                    val productId = productDao.insertProduct(
                        Product(
                            name = name,
                            description = description ?: "Synced from desktop",
                            price = price,
                            cost = cost,
                            stockQuantity = stock,
                            category = category,
                            barcodeValue = barcode.ifBlank { null },
                            imageUrl = imagePath,
                            enterpriseSyncStatus = Product.SYNC_SYNCED,
                        ),
                    )
                    syncedStock[productId] = stock
                    created += 1
                } else {
                    val next = existing.copy(
                        name = name,
                        description = description ?: existing.description,
                        price = price,
                        cost = cost,
                        stockQuantity = stock,
                        category = category,
                        barcodeValue = barcode.ifBlank { existing.barcodeValue },
                        imageUrl = imagePath ?: existing.imageUrl,
                        enterpriseSyncStatus = Product.SYNC_SYNCED,
                    )
                    if (next != existing) {
                        productDao.updateProduct(next)
                        updated += 1
                    }
                    syncedStock[existing.id] = stock
                }
            }
        }
        if (syncedStock.isNotEmpty()) {
            val editor = prefs.edit()
            syncedStock.forEach { (productId, stock) -> editor.putInt(stockBaselineKey(productId), stock) }
            editor.apply()
        }
        return ProductApplyResult(created, updated)
    }

    private suspend fun applyDesktopServices(services: JSONArray): ServiceApplyResult {
        var created = 0
        var updated = 0
        val mappings = mutableListOf<ServiceIdMapping>()
        val current = serviceItemDao.getAllOnce()
        val servicesById = current.associateBy { it.id }.toMutableMap()
        val servicesByName = current.associateBy { it.name.trim().lowercase(Locale.ROOT) }.toMutableMap()
        database.withTransaction {
            for (index in 0 until services.length()) {
                val item = services.optJSONObject(index) ?: continue
                val desktopServiceId = item.optString("id").trim()
                val name = item.optString("name").trim()
                if (desktopServiceId.isBlank() || name.isBlank()) continue
                val mappedLocalId = prefs.getLong(serviceLocalIdKey(desktopServiceId), 0L)
                val existing = servicesById[mappedLocalId]
                    ?: servicesByName[name.lowercase(Locale.ROOT)]
                val incomingUpdatedAt = item.optLong("updatedAt", System.currentTimeMillis()).coerceAtLeast(0L)
                val description = item.optString("description").trim().takeIf { it.isNotBlank() }
                    ?: existing?.description
                val category = item.optString("category").trim().takeIf { it.isNotBlank() }
                    ?: existing?.category
                val price = item.optLong("priceCents", 0L).centsToDouble()
                val priceMode = ServicePriceMode.parse(item.optString("priceMode")).name
                val durationMinutes = item.optInt("durationMinutes", 0).coerceAtLeast(0)
                val warrantyDays = item.optInt("warrantyDays", 0).coerceAtLeast(0)
                val visibleInKiosk = if (item.has("visibleInKiosk")) {
                    item.optBoolean("visibleInKiosk", true)
                } else {
                    existing?.visibleInKiosk ?: true
                }
                val localId = if (existing == null) {
                    val insertedId = serviceItemDao.insert(
                        ServiceItem(
                            name = name,
                            description = description,
                            basePrice = price,
                            priceMode = priceMode,
                            durationMinutes = durationMinutes,
                            category = category,
                            catalogueToken = ServiceTokenCodec.catalogueToken(0L),
                            warrantyDays = warrantyDays,
                            visibleInKiosk = visibleInKiosk,
                            createdAt = incomingUpdatedAt,
                            updatedAt = incomingUpdatedAt,
                            enterpriseSyncStatus = ServiceItem.SYNC_SYNCED,
                        ),
                    )
                    val inserted = serviceItemDao.getById(insertedId) ?: continue
                    val saved = inserted.copy(catalogueToken = ServiceTokenCodec.catalogueToken(insertedId))
                    serviceItemDao.update(saved)
                    servicesById[insertedId] = saved
                    servicesByName[saved.name.lowercase(Locale.ROOT)] = saved
                    created += 1
                    insertedId
                } else {
                    if (incomingUpdatedAt >= existing.updatedAt) {
                        val next = existing.copy(
                            name = name,
                            description = description,
                            basePrice = price,
                            priceMode = priceMode,
                            durationMinutes = durationMinutes,
                            category = category,
                            warrantyDays = warrantyDays,
                            visibleInKiosk = visibleInKiosk,
                            updatedAt = incomingUpdatedAt,
                            enterpriseSyncStatus = ServiceItem.SYNC_SYNCED,
                        )
                        if (next != existing) {
                            serviceItemDao.update(next)
                            servicesByName.remove(existing.name.lowercase(Locale.ROOT))
                            servicesByName[next.name.lowercase(Locale.ROOT)] = next
                            servicesById[next.id] = next
                            updated += 1
                        }
                    }
                    existing.id
                }
                mappings += ServiceIdMapping(localId, desktopServiceId)
            }
        }
        if (mappings.isNotEmpty()) {
            val editor = prefs.edit()
            mappings.forEach { mapping ->
                editor.putString(serviceRemoteIdKey(mapping.localId), mapping.desktopServiceId)
                editor.putLong(serviceLocalIdKey(mapping.desktopServiceId), mapping.localId)
            }
            editor.apply()
        }
        return ServiceApplyResult(created, updated)
    }

    private fun serviceRemoteIdKey(localId: Long): String = "$KEY_SERVICE_REMOTE_ID_PREFIX$localId"

    private fun serviceLocalIdKey(desktopServiceId: String): String =
        "$KEY_SERVICE_LOCAL_ID_PREFIX${sha256(desktopServiceId).take(24)}"

    private suspend fun applyDesktopTransactions(transactions: JSONArray): Int {
        var imported = 0
        database.withTransaction {
            for (index in 0 until transactions.length()) {
                val item = transactions.optJSONObject(index) ?: continue
                val desktopId = item.optString("id").trim()
                if (desktopId.isBlank() || desktopId.startsWith("MOB-", ignoreCase = true)) continue
                val receiptNumber = "DESK-$desktopId"
                if (transactionDao.getTransactionByReceiptNumber(receiptNumber) != null) continue

                val createdAt = item.optLong("createdAtMillis", System.currentTimeMillis())
                val customerId = getOrCreateDesktopCustomerId(
                    name = item.optString("customerName").trim(),
                    phone = item.optString("customerPhone").trim(),
                    now = createdAt,
                )
                val pendingLines = pendingProductLines(item.optJSONArray("lines") ?: JSONArray())
                val productSubtotal = pendingLines.sumOf { it.lineTotal }
                val serviceSubtotal = item.optLong("subtotalCents", 0L).centsToDouble()
                    .minus(productSubtotal)
                    .coerceAtLeast(0.0)
                val total = item.optLong("totalCents", 0L).centsToDouble()
                val paid = item.optLong("paidCents", item.optLong("totalCents", 0L)).centsToDouble()
                val balanceDue = item.optLong("balanceCents", 0L).centsToDouble()
                val tx = Transaction(
                    type = TransactionType.INCOME,
                    amount = total,
                    description = "Desktop sale: ${item.optString("description").ifBlank { desktopId }}",
                    date = createdAt,
                    paymentMethod = item.optString("paymentMethod").normalizePaymentMethod(),
                    receiptNumber = receiptNumber,
                    saleGroupId = "DESKTOP-$desktopId",
                    taxRate = 0.0,
                    taxAmount = item.optLong("taxCents", 0L).centsToDouble(),
                    customerId = customerId,
                    noteType = if (balanceDue > EPSILON) {
                        TransactionNoteTypes.PARTIAL_CREDIT
                    } else {
                        TransactionNoteTypes.STANDARD
                    },
                    productSubtotal = productSubtotal,
                    serviceSubtotal = serviceSubtotal,
                    amountPaid = paid,
                    balanceDue = balanceDue,
                )
                val txId = transactionDao.insertTransaction(tx)
                val lineItems = pendingLines.map { line ->
                    SaleLineItem(
                        transactionId = txId,
                        productId = line.productId,
                        productName = line.productName,
                        unitPrice = line.unitPrice,
                        quantity = line.quantity,
                        lineTotal = line.lineTotal,
                    )
                }
                if (lineItems.isNotEmpty()) {
                    saleLineItemDao.insertLineItems(lineItems)
                }
                val committed = tx.copy(id = txId)
                val committedLines = if (lineItems.isEmpty()) {
                    emptyList()
                } else {
                    saleLineItemDao.getLineItemsForTransactionOnce(txId)
                }
                if (paid > EPSILON) {
                    runCatching {
                        if (committedLines.isNotEmpty()) {
                            ledgerRepository.recordProductSale(committed, committedLines, customerId, cashAmount = paid)
                        } else {
                            ledgerRepository.recordManualEntry(
                                LedgerDirection.MONEY_IN,
                                paid,
                                committed.description,
                                "Imported from desktop reconciliation.",
                                occurredAt = createdAt,
                            )
                        }
                    }
                }
                if (balanceDue > EPSILON && customerId != null) {
                    val debt = Debt(
                        customerId = customerId,
                        amount = balanceDue,
                        description = "Desktop balance: $receiptNumber",
                        createdAt = createdAt,
                        sourceTransactionId = txId,
                    )
                    val debtId = debtDao.insertDebt(debt)
                    val customerName = customerDao.getCustomerById(customerId)?.name ?: "Customer"
                    runCatching { ledgerRepository.recordCreditExtended(debt.copy(id = debtId), customerName) }
                }
                if (customerId != null) {
                    customerDao.updateLastVisit(customerId, createdAt)
                }
                imported += 1
            }
        }
        return imported
    }

    private fun desktopImagePath(item: JSONObject, existingPath: String?): String? {
        if (!existingPath.isNullOrBlank()) return existingPath
        val raw = item.optString("imageBase64").trim()
        if (raw.isBlank()) return null
        val payload = raw.substringAfter(',', raw)
        val bytes = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull() ?: return null
        if (bytes.size > MAX_DESKTOP_IMAGE_BYTES) return null
        return productPhotoStore.saveScaledJpegBytes(bytes)
    }

    private suspend fun pendingProductLines(lines: JSONArray): List<PendingProductLine> {
        val result = mutableListOf<PendingProductLine>()
        for (index in 0 until lines.length()) {
            val item = lines.optJSONObject(index) ?: continue
            if (!item.optString("kind").equals("PRODUCT", ignoreCase = true)) continue
            val name = item.optString("name").trim().ifBlank { "Desktop product" }
            val barcode = item.optString("barcode").trim()
            val category = item.optString("category").trim().ifBlank { null }
            val unitPrice = item.optLong("unitCents", 0L).centsToDouble()
            val quantity = item.optInt("quantity", 1).coerceAtLeast(1)
            val lineTotal = item.optLong("lineTotalCents", (unitPrice * quantity).toCents()).centsToDouble()
            val product = barcode.takeIf { it.isNotBlank() }?.let { productDao.getProductByBarcodeOnce(it) }
                ?: productDao.findProductByExactName(name)
                ?: Product(
                    name = name,
                    description = "Created from desktop sale",
                    price = unitPrice,
                    cost = 0.0,
                    stockQuantity = 0,
                    category = category,
                    barcodeValue = barcode.ifBlank { null },
                    enterpriseSyncStatus = Product.SYNC_SYNCED,
                ).let { created ->
                    val id = productDao.insertProduct(created)
                    created.copy(id = id)
                }
            result += PendingProductLine(
                productId = product.id,
                productName = name,
                unitPrice = unitPrice,
                quantity = quantity,
                lineTotal = lineTotal,
            )
        }
        return result
    }

    private suspend fun getOrCreateDesktopCustomerId(
        name: String,
        phone: String,
        now: Long,
    ): Long? {
        if (name.isBlank() && phone.isBlank()) return null
        phone.takeIf { it.isNotBlank() }?.let { value ->
            customerDao.findByPhone(value)?.let { return it.id }
        }
        name.takeIf { it.isNotBlank() }?.let { value ->
            customerDao.findByExactName(value)?.let { return it.id }
        }
        return customerDao.insertCustomer(
            Customer(
                name = name.ifBlank { phone },
                phone = phone.takeIf { it.isNotBlank() },
                createdAt = now,
                lastVisit = now,
            ),
        )
    }

    private fun String.normalizePaymentMethod(): String =
        when (lowercase(Locale.ROOT).replace(" ", "_").replace("-", "_")) {
            "mobile_money", "mpesa", "m_pesa", "mtn", "airtel_money", "orange_money" -> "MOBILE_MONEY"
            "credit" -> "CREDIT"
            "split" -> "SPLIT"
            "voucher" -> "VOUCHER"
            else -> "CASH"
        }

    private fun Product.toDesktopJson(
        session: DesktopBridgeSession,
        image: EncodedImage?,
    ): JSONObject =
        JSONObject()
            .put("sessionKey", session.sessionKey)
            .put("deviceName", session.deviceName)
            .put("mobileProductId", id.toString())
            .put("name", name)
            .put("description", description.orEmpty())
            .put("sku", "")
            .put("barcode", barcodeValue.orEmpty())
            .put("category", category.orEmpty())
            .put("stock", stockQuantity)
            .put("priceCents", price.toCents())
            .put("costCents", cost.toCents())
            .put("imageFileName", image?.fileName.orEmpty())
            .put("imageBase64", image?.base64.orEmpty())
            .put("whatsappRetailerId", barcodeValue.orEmpty().ifBlank { "mobile-$id" })

    private fun authorizedRequest(
        session: DesktopBridgeSession,
        path: String,
        body: okhttp3.RequestBody,
    ): Request {
        val requestId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val bodyBytes = Buffer().use { buffer ->
            body.writeTo(buffer)
            buffer.readByteArray()
        }
        val signature = RequestSigner.sign(
            "POST",
            path,
            session.protocolVersion,
            requestId,
            timestamp,
            nonce,
            bodyBytes,
            session.sessionKey,
        )
        return Request.Builder()
            .url("${session.baseUrl}$path")
            .post(body)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent())
            .header(SyncProtocol.HEADER_SESSION, session.sessionKey)
            .header(SyncProtocol.HEADER_PROTOCOL, session.protocolVersion)
            .header(SyncProtocol.HEADER_REQUEST_ID, requestId)
            .header(SyncProtocol.HEADER_TIMESTAMP, timestamp)
            .header(SyncProtocol.HEADER_NONCE, nonce)
            .header(SyncProtocol.HEADER_SIGNATURE, signature)
            .build()
    }

    private fun execute(request: Request, client: OkHttpClient = http): String {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(text).optString("error") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: text.take(240)
                error("HTTP ${response.code}: ${detail.ifBlank { "Desktop bridge rejected the request." }}")
            }
            return text
        }
    }

    private fun readProductImage(product: Product): EncodedImage? {
        val path = product.imageUrl?.trim().orEmpty()
        if (path.isBlank() || !productPhotoStore.isAppStoredAbsolutePath(path)) return null
        val file = File(path)
        if (!file.isFile || !file.canRead()) return null
        if (file.length() > MAX_IMAGE_BYTES) {
            return EncodedImage(fileName = file.name, base64 = "", skipped = true)
        }
        val extension = file.extension.lowercase(Locale.ROOT).ifBlank { "jpg" }
        val safeName = "mobile_product_${product.id}.$extension"
        return EncodedImage(
            fileName = safeName,
            base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP),
            skipped = false,
        )
    }

    private fun resolvePairingFields(baseUrlInput: String, tokenInput: String): PairingFields {
        parsePairingPayload(baseUrlInput)?.let { return it }
        parsePairingPayload(tokenInput)?.let { return it }
        val baseUrl = normalizeBaseUrl(baseUrlInput)
        val token = tokenInput.trim()
        require(token.isNotBlank()) { "Enter the pairing code shown on desktop." }
        return PairingFields(baseUrl, token)
    }

    private fun parsePairingPayload(raw: String): PairingFields? {
        val text = raw.trim()
        if (!text.startsWith("biashara-desktop://", ignoreCase = true)) return null
        val uri = Uri.parse(text)
        val host = uri.getQueryParameter("host")?.trim().orEmpty()
        val port = uri.getQueryParameter("port")?.trim().orEmpty().ifBlank { "8865" }
        val token = uri.getQueryParameter("token")?.trim().orEmpty()
        require(host.isNotBlank()) { "Desktop pairing QR is missing the host." }
        require(token.isNotBlank()) { "Desktop pairing QR is missing the code." }
        return PairingFields(normalizeBaseUrl("http://$host:$port"), token)
    }

    private fun parseDiscovery(raw: String): DesktopDiscoveryResult? {
        val text = raw.trim()
        if (!text.startsWith(DISCOVERY_PREFIX, ignoreCase = true)) return null
        val root = runCatching { JSONObject(text.substring(DISCOVERY_PREFIX.length).trim()) }.getOrNull()
            ?: return null
        val host = root.optString("host").trim()
        val port = root.optInt("port", 8865).coerceIn(1, 65_535)
        val token = root.optString("token").trim()
        if (host.isBlank() || token.isBlank()) return null
        val baseUrl = runCatching { normalizeBaseUrl("http://$host:$port") }.getOrNull()
            ?: return null
        return DesktopDiscoveryResult(
            baseUrl = baseUrl,
            token = token,
            businessName = root.optString("businessName").trim(),
            host = host,
            port = port,
        )
    }

    private fun normalizeBaseUrl(raw: String): String {
        var value = raw.trim()
        require(value.isNotBlank()) { "Enter the desktop bridge URL." }
        if (!value.contains("://")) value = "http://$value"
        val uri = Uri.parse(value)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") {
            "Desktop bridge URL must start with http:// or https://."
        }
        val host = uri.host?.trim().orEmpty()
        require(isAllowedDesktopHost(host)) {
            "Use a local desktop address such as 127.0.0.1, 192.168.x.x, or 10.x.x.x."
        }
        val port = uri.port
        val defaultPort = if (scheme == "https") 443 else 80
        val normalizedPort = if (port > 0 && port != defaultPort) ":$port" else ""
        return "$scheme://$host$normalizedPort"
    }

    private fun isAllowedDesktopHost(host: String): Boolean {
        val clean = host.trim().trim('[', ']').lowercase(Locale.ROOT)
        if (clean == "localhost" || clean == "::1" || clean == "10.0.2.2") return true
        if (clean.startsWith("127.")) return true
        val parts = clean.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return when {
            parts[0] == 10 -> true
            parts[0] == 192 && parts[1] == 168 -> true
            parts[0] == 172 && parts[1] in 16..31 -> true
            parts[0] == 169 && parts[1] == 254 -> true
            else -> false
        }
    }

    private fun defaultDeviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Biashara mobile" }

    private fun userAgent(): String = "BiasharaAI-Android/${BuildConfig.VERSION_NAME}"

    private fun Double.toCents(): Long = (this * 100.0).roundToLong().coerceAtLeast(0L)

    private fun Long.centsToDouble(): Double = this.coerceAtLeast(0L) / 100.0

    private fun Throwable.cleanMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback

    private data class ProductPushResult(
        val sent: Int,
        val failed: Int,
        val skippedImages: Int,
    )

    private data class ProductApplyResult(
        val created: Int,
        val updated: Int,
    )

    private data class ServiceApplyResult(
        val created: Int,
        val updated: Int,
    )

    private data class ServiceIdMapping(
        val localId: Long,
        val desktopServiceId: String,
    )

    private data class PendingProductLine(
        val productId: Long,
        val productName: String,
        val unitPrice: Double,
        val quantity: Int,
        val lineTotal: Double,
    )

    private data class SyncedSettingsPayload(
        val json: JSONObject,
        val fingerprint: String,
    )

    private data class PairingFields(
        val baseUrl: String,
        val token: String,
    )

    private data class EncodedImage(
        val fileName: String,
        val base64: String,
        val skipped: Boolean,
    )

    companion object {
        const val DEFAULT_TEST_URL = "http://127.0.0.1:8865"
        private const val PREFS_NAME = "desktop_bridge"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_SESSION_KEY = "session_key"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_PAIRED_AT = "paired_at"
        private const val KEY_PROTOCOL_VERSION = "protocol_version"
        private const val KEY_UPLOADED_TX_IDS = "uploaded_tx_ids"
        private const val KEY_LAST_AUTO_PRODUCT_PUSH_AT = "last_auto_product_push_at"
        private const val KEY_LAST_SETTINGS_FINGERPRINT = "last_settings_fingerprint"
        private const val KEY_STOCK_BASELINE_PREFIX = "stock_baseline_"
        private const val KEY_SERVICE_REMOTE_ID_PREFIX = "service_remote_id_"
        private const val KEY_SERVICE_LOCAL_ID_PREFIX = "service_local_id_"
        private const val DISCOVERY_PORT = 8864
        private const val DISCOVERY_PREFIX = "BIASHARA_DESKTOP_V1 "
        private const val DISCOVERY_RECEIVE_TIMEOUT_MS = 1_000
        private const val DISCOVERY_BUFFER_BYTES = 4096
        private const val DISCOVERY_PROBE_PARALLELISM = 32
        private const val AUTO_PRODUCT_PUSH_INTERVAL_MS = 5L * 60L * 1000L
        private const val MAX_IMAGE_BYTES = 7L * 1024L * 1024L
        private const val MAX_DESKTOP_IMAGE_BYTES = 2L * 1024L * 1024L
        private const val MAX_TRANSACTION_UPLOADS_PER_RECONCILE = 40
        private const val EPSILON = 0.01
    }
}
