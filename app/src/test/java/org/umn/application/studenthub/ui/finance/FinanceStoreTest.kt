package org.umn.application.studenthub.ui.finance

import org.junit.Assert.*
import org.junit.Test

class FinanceStoreTest {
    @Test fun invalidTransactionsNeverChangeStoredData() {
        var writes = 0
        val store = FinanceStore({ null }, { writes++ })
        listOf("0", "-1", "1.2", "abc", "999999999999999999999", "1,000", "  ").forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) { store.add("Makan", amount, "Makan", false, "2026-10-05") }
        }
        assertThrows(IllegalArgumentException::class.java) { store.add(" ", "1000", "Makan", false, "2026-10-05") }
        assertThrows(IllegalArgumentException::class.java) { store.add("Makan", "1000", "Makan", false, "2026-02-30") }
        assertEquals(0, writes)
    }
    @Test fun monthsAreIsolatedAndIncomeIncreasesBalance() {
        var disk: String? = null
        val store = FinanceStore({ disk }, { disk = it })
        store.add("Uang saku", "2500000", "Lainnya", true, "2026-10-01")
        store.add("Makan", "25000", "Makan", false, "2026-11-01")
        assertEquals(2500000L, store.summary("2026-10").balance)
        assertEquals(-25000L, store.summary("2026-11").balance)
    }

    @Test fun addingTransactionPersistsAndRecalculatesBalance() {
        var disk: String? = null
        val store = FinanceStore({ disk }, { disk = it })
        val before = store.summary("2026-10").balance
        store.add("Makan siang", "25000", "Makan", false, "2026-10-05")
        val reloaded = FinanceStore({ disk }, { disk = it })
        assertEquals(before - 25000, reloaded.summary("2026-10").balance)
        assertEquals("Makan siang", reloaded.transactions.last().title)
    }
}
