package org.umn.application.studenthub.ui.finance

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.umn.application.studenthub.data.local.StudentHubDatabase

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FinanceScreenTest {
    @get:Rule val compose = createComposeRule()
    @Before fun clearFinance() {
        StudentHubDatabase.resetForTests()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase("studenthub.db")
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("studenthub_finance", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun rejectsZeroAmountWithoutSaving() {
        compose.setContent { MaterialTheme { FinanceScreen() } }
        compose.onNodeWithText("Catat transaksi").performScrollTo().performClick()
        compose.onNodeWithTag("transaction-title").performTextInput("Makan uji")
        compose.onNodeWithTag("transaction-amount").performTextInput("0")
        compose.onNodeWithText("Simpan transaksi").performClick()
        compose.onNodeWithText("Nominal harus 1–1.000.000.000.000 rupiah.").assertExists()
        compose.runOnIdle { org.junit.Assert.assertTrue(financeStore(ApplicationProvider.getApplicationContext()).transactions.isEmpty()) }
    }
    @Test fun budgetChangesPersist() {
        compose.setContent { MaterialTheme { FinanceScreen() } }
        compose.onNodeWithText("Atur").performScrollTo().performClick()
        compose.onNodeWithText("600000").performTextReplacement("700000")
        compose.onNodeWithText("Simpan anggaran").performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(700000L, financeStore(ApplicationProvider.getApplicationContext()).budgets().getValue("Makan"))
        }
    }
    @Test fun transactionFormSavesAndUpdatesSummary() {
        compose.setContent { MaterialTheme { FinanceScreen() } }
        compose.onNodeWithText("Catat transaksi").performScrollTo().performClick()
        compose.onNodeWithTag("transaction-title").performTextInput("Makan uji")
        compose.onNodeWithTag("transaction-amount").performTextInput("10000")
        compose.onNodeWithText("Simpan transaksi").performClick()
        compose.runOnIdle {
            val entries = financeStore(ApplicationProvider.getApplicationContext()).transactions
            org.junit.Assert.assertEquals(10000L, entries.single().amount)
        }
    }
}
