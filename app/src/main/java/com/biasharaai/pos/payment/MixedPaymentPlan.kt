package com.biasharaai.pos.payment

/** How a mixed (product + service) cart is paid at checkout. */
enum class MixedPaymentPlan {
    /** Scenario 1 — full amount now (cash / mobile / split). */
    PAY_ALL,

    /** Scenario 2 — product lines paid now; service lines on customer credit. */
    CREDIT_SERVICES,

    /** Scenario 2 — service lines paid now; product lines on customer credit. */
    CREDIT_PRODUCTS,

    /** Scenario 3 — partial deposit now; balance due on collection. */
    DEPOSIT,
}
