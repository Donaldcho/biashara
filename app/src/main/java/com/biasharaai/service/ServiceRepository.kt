package com.biasharaai.service

import com.biasharaai.data.local.db.LedgerRepository
import com.biasharaai.data.local.db.ServiceDelivery
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.data.local.db.ServiceItem
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.data.local.db.ServicePriceMode
import com.biasharaai.data.local.db.ServiceVoucher
import com.biasharaai.data.local.db.ServiceVoucherDao
import com.biasharaai.enterprise.EnterpriseCatalogRepository
import com.biasharaai.productline.ProductLineManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceRepository @Inject constructor(
    private val serviceItemDao: ServiceItemDao,
    private val serviceVoucherDao: ServiceVoucherDao,
    private val serviceDeliveryDao: ServiceDeliveryDao,
    private val ledgerRepository: LedgerRepository,
    private val productLineManager: ProductLineManager,
    private val enterpriseCatalogRepository: EnterpriseCatalogRepository,
) {
    private fun requirePro() {
        check(productLineManager.isProEnabled()) { "Service features require Biashara AI Pro" }
    }

    fun observeServices(): Flow<List<ServiceItem>> = serviceItemDao.observeAll()

    suspend fun getService(id: Long): ServiceItem? {
        requirePro()
        return serviceItemDao.getById(id)
    }

    suspend fun getServiceByCatalogueToken(token: String): ServiceItem? {
        requirePro()
        return serviceItemDao.getByCatalogueToken(token)
    }

    suspend fun upsertService(
        id: Long,
        name: String,
        description: String?,
        basePrice: Double,
        priceMode: ServicePriceMode,
        durationMinutes: Int,
        category: String?,
        warrantyDays: Int,
    ): Long {
        requirePro()
        val now = System.currentTimeMillis()
        return if (id > 0L) {
            val existing = serviceItemDao.getById(id) ?: error("Service not found")
            val draft = existing.copy(
                name = name.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                basePrice = basePrice,
                priceMode = priceMode.name,
                durationMinutes = durationMinutes.coerceAtLeast(0),
                category = category?.trim()?.takeIf { it.isNotEmpty() },
                warrantyDays = warrantyDays.coerceAtLeast(0),
                updatedAt = now,
            )
            serviceItemDao.update(
                enterpriseCatalogRepository.prepareServiceForLocalSave(existing, draft),
            )
            id
        } else {
            val draft = ServiceItem(
                name = name.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                basePrice = basePrice,
                priceMode = priceMode.name,
                durationMinutes = durationMinutes.coerceAtLeast(0),
                category = category?.trim()?.takeIf { it.isNotEmpty() },
                catalogueToken = ServiceTokenCodec.catalogueToken(0),
                warrantyDays = warrantyDays.coerceAtLeast(0),
                createdAt = now,
                updatedAt = now,
            )
            val insertedId = serviceItemDao.insert(
                enterpriseCatalogRepository.prepareServiceForLocalSave(
                    existing = null,
                    draft = draft,
                ),
            )
            val token = ServiceTokenCodec.catalogueToken(insertedId)
            val row = serviceItemDao.getById(insertedId)!!
            serviceItemDao.update(row.copy(catalogueToken = token, updatedAt = now))
            insertedId
        }
    }

    suspend fun deleteService(id: Long) {
        requirePro()
        val service = serviceItemDao.getById(id)
        serviceItemDao.deleteById(id)
        if (service != null) {
            enterpriseCatalogRepository.onServiceDeleted(service)
        }
    }

    suspend fun recordDeliveriesForSale(
        services: List<ServiceCartLine>,
        transactionId: Long,
        customerId: Long?,
    ): List<ServiceDelivery> {
        requirePro()
        val now = System.currentTimeMillis()
        return services.flatMap { line ->
            val item = line.service
            val warrantyExpires = if (item.warrantyDays > 0) {
                now + TimeUnit.DAYS.toMillis(item.warrantyDays.toLong())
            } else {
                null
            }
            val unitCharge = line.effectivePrice
            List(line.quantity.coerceAtLeast(1)) {
                val deliveryId = serviceDeliveryDao.insert(
                    ServiceDelivery(
                        serviceItemId = item.id,
                        transactionId = transactionId,
                        customerId = customerId,
                        staffName = line.staffName?.trim()?.takeIf { it.isNotEmpty() },
                        deliveredAt = now,
                        warrantyExpiresAt = warrantyExpires,
                        chargedAmount = unitCharge,
                    ),
                )
                val saved = serviceDeliveryDao.getById(deliveryId)!!
                val token = ServiceTokenCodec.receiptToken(deliveryId)
                serviceDeliveryDao.update(saved.copy(receiptToken = token))
                serviceDeliveryDao.getById(deliveryId)!!
            }
        }
    }

    suspend fun sellVoucher(
        serviceItemId: Long,
        totalUses: Int,
        amountPaid: Double,
        customerId: Long?,
        expiresAt: Long?,
        sourceTransactionId: Long? = null,
    ): ServiceVoucher {
        requirePro()
        val item = serviceItemDao.getById(serviceItemId) ?: error("Service not found")
        val voucherId = UUID.randomUUID().toString()
        val voucher = ServiceVoucher(
            voucherId = voucherId,
            serviceItemId = serviceItemId,
            customerId = customerId,
            sourceTransactionId = sourceTransactionId,
            totalUses = totalUses.coerceAtLeast(1),
            remainingUses = totalUses.coerceAtLeast(1),
            amountPaid = amountPaid,
            expiresAt = expiresAt,
        )
        serviceVoucherDao.insert(voucher)
        ledgerRepository.recordVoucherSale(
            voucherId = voucherId,
            serviceName = item.name,
            totalUses = voucher.totalUses,
            amount = amountPaid,
            customerId = customerId,
        )
        return serviceVoucherDao.getByVoucherId(voucherId)!!
    }

    suspend fun redeemVoucher(voucherId: String, customerId: Long?): ServiceItem {
        requirePro()
        val voucher = serviceVoucherDao.getByVoucherId(voucherId)
            ?: error("Voucher not found")
        require(voucher.remainingUses > 0) { "Voucher has no uses left" }
        voucher.expiresAt?.let { expires ->
            require(expires > System.currentTimeMillis()) { "Voucher has expired" }
        }
        val item = serviceItemDao.getById(voucher.serviceItemId) ?: error("Service not found")
        val updated = voucher.copy(
            remainingUses = voucher.remainingUses - 1,
            lastRedeemedAt = System.currentTimeMillis(),
        )
        serviceVoucherDao.update(updated)
        ledgerRepository.recordVoucherRedemption(
            voucherId = voucherId,
            serviceName = item.name,
            remainingUses = updated.remainingUses,
            customerId = customerId ?: voucher.customerId,
        )
        return item
    }

    suspend fun verifyReceiptToken(token: String): ServiceDelivery? {
        requirePro()
        return serviceDeliveryDao.getByReceiptToken(token)
            ?: ServiceTokenCodec.parse(token)?.let { parsed ->
                if (parsed is ServiceTokenCodec.Parsed.Receipt) {
                    serviceDeliveryDao.getById(parsed.deliveryId)
                } else {
                    null
                }
            }
    }

    suspend fun itemCount(): Int = serviceItemDao.count()

    suspend fun voucherCount(): Int = serviceVoucherDao.count()

    suspend fun deliveryCount(): Int = serviceDeliveryDao.count()
}

/** In-memory POS service line (mirrors [com.biasharaai.pos.cart.CartItem] for products). */
data class ServiceCartLine(
    val service: ServiceItem,
    val quantity: Int = 1,
    val overridePrice: Double? = null,
    val staffName: String? = null,
) {
    val effectivePrice: Double get() = overridePrice ?: service.basePrice
    val lineTotal: Double get() = effectivePrice * quantity
}
