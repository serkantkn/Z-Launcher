package com.serkantkn.zeta

import com.android.billingclient.api.Purchase
import com.serkantkn.zeta.data.billing.ProStore
import com.serkantkn.zeta.data.billing.ProStore.Standing
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where an account stands with Pro, from what Google Play says it holds. */
class ProStoreTest {

    private val pro = listOf(ProStore.PRODUCT_ID)

    @Test
    fun proPaidForIsPro() {
        assertEquals(Standing.OWNED, ProStore.proStanding(listOf(pro to Purchase.PurchaseState.PURCHASED)))
    }

    @Test
    fun proStillBeingPaidForIsPending() {
        assertEquals(Standing.PENDING, ProStore.proStanding(listOf(pro to Purchase.PurchaseState.PENDING)))
    }

    @Test
    fun nothingBoughtOrSomethingElseBoughtIsNotPro() {
        assertEquals(Standing.NONE, ProStore.proStanding(emptyList()))
        assertEquals(Standing.NONE, ProStore.proStanding(listOf(listOf("other") to Purchase.PurchaseState.PURCHASED)))
    }

    @Test
    fun aPaidPurchaseOutweighsAPendingOne() {
        val both = listOf(pro to Purchase.PurchaseState.PENDING, pro to Purchase.PurchaseState.PURCHASED)
        assertEquals(Standing.OWNED, ProStore.proStanding(both))
    }
}
