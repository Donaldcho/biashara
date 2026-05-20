package com.biasharaai.cloud

import android.content.Context
import com.biasharaai.BuildConfig
import com.biasharaai.data.local.db.Alert
import com.biasharaai.data.local.db.AiBusinessMemory
import com.biasharaai.data.local.db.AppDatabase
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.ChatMemoryDao
import com.biasharaai.data.local.db.Customer
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.Debt
import com.biasharaai.data.local.db.DebtDao
import com.biasharaai.data.local.db.EnterpriseAuditEvent
import com.biasharaai.data.local.db.EnterpriseAuditEventDao
import com.biasharaai.data.local.db.EnterpriseBranch
import com.biasharaai.data.local.db.EnterpriseBranchDao
import com.biasharaai.data.local.db.EnterpriseRegisteredDevice
import com.biasharaai.data.local.db.EnterpriseRegisteredDeviceDao
import com.biasharaai.data.local.db.EnterpriseStockMovement
import com.biasharaai.data.local.db.EnterpriseStockMovementDao
import com.biasharaai.data.local.db.EnterpriseSyncOutboxDao
import com.biasharaai.data.local.db.LossAlertDao
import com.biasharaai.data.local.db.PosSaleLineFact
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.ProductSaleDayRow
import com.biasharaai.data.local.db.SaleLineItem
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.data.local.db.AlertDao
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a single JSON document of shop data for user-initiated upload to a cloud analytics
 * endpoint. Excludes chat session message text by default (see [BusinessDataExportV1]).
 */
@Singleton
class BusinessAnalyticsJsonExporter @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appDatabase: AppDatabase,
    private val appSettingsDao: AppSettingsDao,
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    private val saleLineItemDao: SaleLineItemDao,
    private val customerDao: CustomerDao,
    private val debtDao: DebtDao,
    private val alertDao: AlertDao,
    private val chatMemoryDao: ChatMemoryDao,
    private val lossAlertDao: LossAlertDao,
    private val enterpriseRegisteredDeviceDao: EnterpriseRegisteredDeviceDao,
    private val enterpriseAuditEventDao: EnterpriseAuditEventDao,
    private val enterpriseBranchDao: EnterpriseBranchDao,
    private val enterpriseSyncOutboxDao: EnterpriseSyncOutboxDao,
    private val enterpriseStockMovementDao: EnterpriseStockMovementDao,
) {
    private val gson = Gson()

    suspend fun buildJson(): String = withContext(Dispatchers.IO) {
        coroutineScope {
            val settingsDef = async { appSettingsDao.getSettingsSync() }
            val productsDef = async { productDao.getProductsList() }
            val transactionsDef = async { transactionDao.getTransactionsList() }
            val linesDef = async { saleLineItemDao.getAllLineItems() }
            val customersDef = async { customerDao.getCustomersList() }
            val debtsDef = async { debtDao.getAllDebts() }
            val outstandingDef = async { debtDao.getTotalOutstandingOnce() }
            val alertsDef = async { alertDao.listRecentForExport() }
            val memoriesDef = async { chatMemoryDao.getAllMemoriesForExport() }
            val posFactsDef = async { saleLineItemDao.posSaleLineFactsSince(0L) }
            val saleByDayDef = async { lossAlertDao.getProductSaleQuantitiesByDay(0L) }
            val devicesDef = async { enterpriseRegisteredDeviceDao.listActive() }
            val auditEventsDef = async { enterpriseAuditEventDao.listRecent(1000) }
            val branchesDef = async { enterpriseBranchDao.listActive() }
            val pendingSyncDef = async { enterpriseSyncOutboxDao.count() }
            val stockMovementsDef = async { enterpriseStockMovementDao.listRecent(1000) }
            val export = BusinessDataExportV1(
                exportedAtEpochMs = System.currentTimeMillis(),
                appVersionName = BuildConfig.VERSION_NAME,
                appSettings = settingsDef.await(),
                totalOutstandingDebt = outstandingDef.await(),
                products = productsDef.await(),
                transactions = transactionsDef.await(),
                saleLineItems = linesDef.await(),
                customers = customersDef.await(),
                debts = debtsDef.await(),
                alerts = alertsDef.await(),
                aiBusinessMemories = memoriesDef.await(),
                posSaleLineFacts = posFactsDef.await(),
                productSaleQuantitiesByDayUtc = saleByDayDef.await(),
                enterpriseRegisteredDevices = devicesDef.await(),
                enterpriseAuditEvents = auditEventsDef.await(),
                enterpriseBranches = branchesDef.await(),
                pendingEnterpriseSyncItems = pendingSyncDef.await(),
                enterpriseStockMovements = stockMovementsDef.await(),
            )
            gson.toJson(export)
        }
    }

    /**
     * Flushes WAL into the main DB file, then copies [AppDatabase.NAME] into cache for upload.
     */
    suspend fun copyCheckpointedDatabaseToCache(): File =
        withContext(Dispatchers.IO) {
            appDatabase.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(FULL)", emptyArray())
                .close()
            val src = appContext.getDatabasePath(AppDatabase.NAME)
            check(src.isFile) { "Database file not found." }
            val out = File(appContext.cacheDir, "biashara_export_${System.currentTimeMillis()}.db")
            src.copyTo(out, overwrite = true)
            out
        }
}

data class BusinessDataExportV1(
    val schemaVersion: Int = 4,
    val exportKind: String = "biashara_business_snapshot",
    val exportedAtEpochMs: Long,
    val appVersionName: String,
    val appSettings: AppSettings?,
    val totalOutstandingDebt: Double,
    val products: List<Product>,
    val transactions: List<Transaction>,
    val saleLineItems: List<SaleLineItem>,
    val customers: List<Customer>,
    val debts: List<Debt>,
    val alerts: List<Alert>,
    val aiBusinessMemories: List<AiBusinessMemory>,
    val posSaleLineFacts: List<PosSaleLineFact>,
    val productSaleQuantitiesByDayUtc: List<ProductSaleDayRow>,
    val enterpriseRegisteredDevices: List<EnterpriseRegisteredDevice>,
    val enterpriseAuditEvents: List<EnterpriseAuditEvent>,
    val enterpriseBranches: List<EnterpriseBranch>,
    val pendingEnterpriseSyncItems: Int,
    val enterpriseStockMovements: List<EnterpriseStockMovement>,
)
