package com.biasharaai.pos.receipt

import com.biasharaai.data.local.db.SaleLineItem
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.ServiceDelivery
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.data.local.db.ServiceItem
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.data.local.db.ServiceVoucherDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PosReceiptAssemblerTest {

    private lateinit var saleLineItemDao: SaleLineItemDao
    private lateinit var serviceDeliveryDao: ServiceDeliveryDao
    private lateinit var serviceItemDao: ServiceItemDao
    private lateinit var serviceVoucherDao: ServiceVoucherDao
    private lateinit var assembler: PosReceiptAssembler

    @Before
    fun setUp() {
        saleLineItemDao = mockk()
        serviceDeliveryDao = mockk()
        serviceItemDao = mockk()
        serviceVoucherDao = mockk()
        assembler = PosReceiptAssembler(
            saleLineItemDao,
            serviceDeliveryDao,
            serviceItemDao,
            serviceVoucherDao,
        )
    }

    @Test
    fun assemble_includesProductsAndServices() = runTest {
        coEvery { saleLineItemDao.getLineItemsForTransactionOnce(1L) } returns listOf(
            SaleLineItem(
                id = 10L,
                transactionId = 1L,
                productId = 5L,
                productName = "Soap",
                unitPrice = 100.0,
                quantity = 2,
                lineTotal = 200.0,
            ),
        )
        coEvery { serviceDeliveryDao.getByTransactionOnce(1L) } returns listOf(
            ServiceDelivery(
                id = 1L,
                serviceItemId = 9L,
                transactionId = 1L,
                staffName = "Jane",
                chargedAmount = 500.0,
            ),
        )
        coEvery { serviceItemDao.getById(9L) } returns ServiceItem(
            id = 9L,
            name = "Haircut",
            basePrice = 500.0,
            catalogueToken = "BSVC-test",
        )
        coEvery { serviceVoucherDao.getVoucherIdsBySourceTransaction(1L) } returns emptyList()

        val receipt = assembler.assemble(1L)

        assertEquals(2, receipt.lines.size)
        assertTrue(receipt.lines.any { it.name == "Soap" && it.kind == PosReceiptLine.Kind.PRODUCT })
        assertTrue(
            receipt.lines.any {
                it.kind == PosReceiptLine.Kind.SERVICE &&
                    it.name.contains("Haircut") &&
                    it.name.contains("Jane")
            },
        )
    }
}
