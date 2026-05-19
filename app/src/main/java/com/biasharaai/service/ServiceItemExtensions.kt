package com.biasharaai.service

import com.biasharaai.data.local.db.ServiceItem

/** Pro voucher defaults until optional per-service columns are added. */
val ServiceItem.isVoucherable: Boolean get() = true

val ServiceItem.voucherValidDays: Int get() = 90
