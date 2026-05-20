package com.biasharaai.enterprise

import android.util.Log
import com.biasharaai.data.local.db.EnterpriseStockMovement
import com.biasharaai.data.local.db.EnterpriseStockMovementDao
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.ServiceItem
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.productline.ProductLineManager
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnterpriseCatalogRepository @Inject constructor(
    private val productDao: ProductDao,
    private val serviceItemDao: ServiceItemDao,
    private val stockMovementDao: EnterpriseStockMovementDao,
    private val enterpriseAuditRepository: EnterpriseAuditRepository,
    private val productLineManager: ProductLineManager,
) {
    fun prepareProductForLocalSave(existing: Product?, draft: Product): Product {
        if (!productLineManager.isEnterprisePro()) return draft
        val now = System.currentTimeMillis()
        return draft.copy(
            enterpriseCatalogId = existing?.enterpriseCatalogId
                ?.takeIf { it.isNotBlank() }
                ?: draft.enterpriseCatalogId?.takeIf { it.isNotBlank() }
                ?: newCatalogId("prod"),
            enterpriseCatalogVersion = maxOf(
                existing?.enterpriseCatalogVersion ?: 0L,
                draft.enterpriseCatalogVersion,
            ) + 1L,
            enterpriseCatalogUpdatedAt = now,
            enterpriseSyncStatus = Product.SYNC_PENDING,
        )
    }

    fun prepareServiceForLocalSave(existing: ServiceItem?, draft: ServiceItem): ServiceItem {
        if (!productLineManager.isEnterprisePro()) return draft
        val now = System.currentTimeMillis()
        return draft.copy(
            enterpriseCatalogId = existing?.enterpriseCatalogId
                ?.takeIf { it.isNotBlank() }
                ?: draft.enterpriseCatalogId?.takeIf { it.isNotBlank() }
                ?: newCatalogId("svc"),
            enterpriseCatalogVersion = maxOf(
                existing?.enterpriseCatalogVersion ?: 0L,
                draft.enterpriseCatalogVersion,
            ) + 1L,
            enterpriseCatalogUpdatedAt = now,
            enterpriseSyncStatus = ServiceItem.SYNC_PENDING,
        )
    }

    suspend fun onProductSaved(product: Product, changeType: String): Product =
        runEnterprise(defaultValue = product, operation = "product catalog sync") {
            val normalized = ensureProductCatalogMetadata(product)
            enqueueProductUpsert(normalized, changeType)
            enterpriseAuditRepository.record(
                action = "CATALOG_PRODUCT_UPSERTED",
                entityType = "PRODUCT",
                entityId = normalized.enterpriseCatalogId ?: normalized.id.toString(),
                summary = "Product catalog saved: ${normalized.name}",
                metadata = "changeType=${changeType.normalizedChangeType()}; version=${normalized.enterpriseCatalogVersion}; syncStatus=${normalized.enterpriseSyncStatus}",
            )
            normalized
        }

    suspend fun onServiceSaved(service: ServiceItem, changeType: String): ServiceItem =
        runEnterprise(defaultValue = service, operation = "service catalog sync") {
            val normalized = ensureServiceCatalogMetadata(service)
            enqueueServiceUpsert(normalized, changeType)
            enterpriseAuditRepository.record(
                action = "CATALOG_SERVICE_UPSERTED",
                entityType = "SERVICE_ITEM",
                entityId = normalized.enterpriseCatalogId ?: normalized.id.toString(),
                summary = "Service catalog saved: ${normalized.name}",
                metadata = "changeType=${changeType.normalizedChangeType()}; version=${normalized.enterpriseCatalogVersion}; syncStatus=${normalized.enterpriseSyncStatus}",
            )
            normalized
        }

    suspend fun onProductDeleted(product: Product) {
        runEnterprise(defaultValue = Unit, operation = "product catalog delete") {
            val entityId = product.enterpriseCatalogId ?: product.id.toString()
            enterpriseAuditRepository.enqueueSyncPayload(
                payloadType = "CATALOG_PRODUCT_DELETED",
                payloadEntityId = entityId,
                payload = CatalogDeletePayload(
                    catalogType = "PRODUCT",
                    centralId = product.enterpriseCatalogId,
                    localId = product.id,
                    name = product.name,
                ),
            )
            enterpriseAuditRepository.record(
                action = "CATALOG_PRODUCT_DELETED",
                entityType = "PRODUCT",
                entityId = entityId,
                summary = "Product catalog deleted: ${product.name}",
                metadata = "centralId=${product.enterpriseCatalogId.orEmpty()}; localId=${product.id}",
            )
        }
    }

    suspend fun onServiceDeleted(service: ServiceItem) {
        runEnterprise(defaultValue = Unit, operation = "service catalog delete") {
            val entityId = service.enterpriseCatalogId ?: service.id.toString()
            enterpriseAuditRepository.enqueueSyncPayload(
                payloadType = "CATALOG_SERVICE_DELETED",
                payloadEntityId = entityId,
                payload = CatalogDeletePayload(
                    catalogType = "SERVICE",
                    centralId = service.enterpriseCatalogId,
                    localId = service.id,
                    name = service.name,
                ),
            )
            enterpriseAuditRepository.record(
                action = "CATALOG_SERVICE_DELETED",
                entityType = "SERVICE_ITEM",
                entityId = entityId,
                summary = "Service catalog deleted: ${service.name}",
                metadata = "centralId=${service.enterpriseCatalogId.orEmpty()}; localId=${service.id}",
            )
        }
    }

    suspend fun recordProductStockMovement(
        product: Product,
        quantityDelta: Int,
        movementType: String,
        sourceType: String? = null,
        sourceId: String? = null,
        note: String? = null,
    ) {
        if (quantityDelta == 0) return
        runEnterprise(defaultValue = Unit, operation = "stock movement sync") {
            val normalized = ensureProductCatalogMetadata(product)
            val movement = EnterpriseStockMovement(
                productId = normalized.id,
                enterpriseProductId = normalized.enterpriseCatalogId,
                movementType = movementType.normalizedChangeType(),
                quantityDelta = quantityDelta,
                stockAfter = product.stockQuantity,
                sourceType = sourceType?.normalizedChangeType(),
                sourceId = sourceId?.takeIf { it.isNotBlank() },
                note = note?.trim()?.take(MAX_NOTE_CHARS)?.takeIf { it.isNotBlank() },
            )
            val movementId = stockMovementDao.insert(movement)
            val saved = movement.copy(id = movementId)
            enterpriseAuditRepository.enqueueSyncPayload(
                payloadType = "STOCK_MOVEMENT_RECORDED",
                payloadEntityId = movementId.toString(),
                payload = StockMovementPayload.from(saved),
            )
            enterpriseAuditRepository.record(
                action = "STOCK_MOVEMENT_RECORDED",
                entityType = "PRODUCT",
                entityId = normalized.enterpriseCatalogId ?: normalized.id.toString(),
                summary = "Stock movement recorded: ${normalized.name}",
                metadata = "movementType=${saved.movementType}; quantityDelta=${saved.quantityDelta}; stockAfter=${saved.stockAfter}",
            )
        }
    }

    private suspend fun ensureProductCatalogMetadata(product: Product): Product {
        if (!product.enterpriseCatalogId.isNullOrBlank()) return product
        val latest = productDao.getProductByIdOnce(product.id) ?: product
        val prepared = prepareProductForLocalSave(
            existing = latest,
            draft = latest,
        )
        productDao.updateProduct(prepared)
        enqueueProductUpsert(prepared, "ENTERPRISE_METADATA_ASSIGNED")
        return prepared
    }

    private suspend fun ensureServiceCatalogMetadata(service: ServiceItem): ServiceItem {
        if (!service.enterpriseCatalogId.isNullOrBlank()) return service
        val latest = serviceItemDao.getById(service.id) ?: service
        val prepared = prepareServiceForLocalSave(existing = latest, draft = latest)
        serviceItemDao.update(prepared)
        enqueueServiceUpsert(prepared, "ENTERPRISE_METADATA_ASSIGNED")
        return prepared
    }

    private suspend fun enqueueProductUpsert(product: Product, changeType: String) {
        enterpriseAuditRepository.enqueueSyncPayload(
            payloadType = "CATALOG_PRODUCT_UPSERTED",
            payloadEntityId = product.enterpriseCatalogId ?: product.id.toString(),
            payload = ProductCatalogPayload.from(product, changeType),
        )
    }

    private suspend fun enqueueServiceUpsert(service: ServiceItem, changeType: String) {
        enterpriseAuditRepository.enqueueSyncPayload(
            payloadType = "CATALOG_SERVICE_UPSERTED",
            payloadEntityId = service.enterpriseCatalogId ?: service.id.toString(),
            payload = ServiceCatalogPayload.from(service, changeType),
        )
    }

    private suspend fun <T> runEnterprise(
        defaultValue: T,
        operation: String,
        block: suspend () -> T,
    ): T {
        if (!productLineManager.isEnterprisePro()) return defaultValue
        return runCatching { block() }
            .onFailure { Log.w(TAG, "Enterprise $operation failed", it) }
            .getOrDefault(defaultValue)
    }

    private fun newCatalogId(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    private fun String.normalizedChangeType(): String =
        trim().uppercase(Locale.ROOT).take(MAX_TYPE_CHARS)

    private companion object {
        const val TAG = "EnterpriseCatalog"
        const val MAX_TYPE_CHARS = 48
        const val MAX_NOTE_CHARS = 240
    }
}

private data class ProductCatalogPayload(
    val schemaVersion: Int = 1,
    val catalogType: String = "PRODUCT",
    val changeType: String,
    val centralId: String?,
    val localId: Long,
    val version: Long,
    val syncStatus: String,
    val updatedAtEpochMs: Long,
    val name: String,
    val description: String?,
    val price: Double,
    val cost: Double,
    val stockQuantity: Int,
    val category: String?,
    val barcodeValue: String?,
    val imageUrl: String?,
) {
    companion object {
        fun from(product: Product, changeType: String): ProductCatalogPayload =
            ProductCatalogPayload(
                changeType = changeType.trim().uppercase(Locale.ROOT),
                centralId = product.enterpriseCatalogId,
                localId = product.id,
                version = product.enterpriseCatalogVersion,
                syncStatus = product.enterpriseSyncStatus,
                updatedAtEpochMs = product.enterpriseCatalogUpdatedAt,
                name = product.name,
                description = product.description,
                price = product.price,
                cost = product.cost,
                stockQuantity = product.stockQuantity,
                category = product.category,
                barcodeValue = product.barcodeValue,
                imageUrl = product.imageUrl,
            )
    }
}

private data class ServiceCatalogPayload(
    val schemaVersion: Int = 1,
    val catalogType: String = "SERVICE",
    val changeType: String,
    val centralId: String?,
    val localId: Long,
    val version: Long,
    val syncStatus: String,
    val updatedAtEpochMs: Long,
    val name: String,
    val description: String?,
    val basePrice: Double,
    val priceMode: String,
    val durationMinutes: Int,
    val category: String?,
    val catalogueToken: String,
    val warrantyDays: Int,
    val visibleInKiosk: Boolean,
) {
    companion object {
        fun from(service: ServiceItem, changeType: String): ServiceCatalogPayload =
            ServiceCatalogPayload(
                changeType = changeType.trim().uppercase(Locale.ROOT),
                centralId = service.enterpriseCatalogId,
                localId = service.id,
                version = service.enterpriseCatalogVersion,
                syncStatus = service.enterpriseSyncStatus,
                updatedAtEpochMs = service.enterpriseCatalogUpdatedAt,
                name = service.name,
                description = service.description,
                basePrice = service.basePrice,
                priceMode = service.priceMode,
                durationMinutes = service.durationMinutes,
                category = service.category,
                catalogueToken = service.catalogueToken,
                warrantyDays = service.warrantyDays,
                visibleInKiosk = service.visibleInKiosk,
            )
    }
}

private data class CatalogDeletePayload(
    val schemaVersion: Int = 1,
    val catalogType: String,
    val centralId: String?,
    val localId: Long,
    val name: String,
    val deletedAtEpochMs: Long = System.currentTimeMillis(),
)

private data class StockMovementPayload(
    val schemaVersion: Int = 1,
    val movementId: Long,
    val localProductId: Long,
    val enterpriseProductId: String?,
    val movementType: String,
    val quantityDelta: Int,
    val stockAfter: Int?,
    val sourceType: String?,
    val sourceId: String?,
    val note: String?,
    val createdAtEpochMs: Long,
) {
    companion object {
        fun from(movement: EnterpriseStockMovement): StockMovementPayload =
            StockMovementPayload(
                movementId = movement.id,
                localProductId = movement.productId,
                enterpriseProductId = movement.enterpriseProductId,
                movementType = movement.movementType,
                quantityDelta = movement.quantityDelta,
                stockAfter = movement.stockAfter,
                sourceType = movement.sourceType,
                sourceId = movement.sourceId,
                note = movement.note,
                createdAtEpochMs = movement.createdAt,
            )
    }
}
