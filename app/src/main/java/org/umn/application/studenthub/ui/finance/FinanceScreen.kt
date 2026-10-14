package org.umn.application.studenthub.ui.finance

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.umn.application.studenthub.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private val financeLocale = Locale.forLanguageTag("id-ID")
private fun rupiah(value: Long) = "Rp" + NumberFormat.getIntegerInstance(financeLocale).format(value)
private val defaultBudgets = linkedMapOf("Makan" to 600000L, "Transportasi" to 200000L, "Kuliah" to 250000L, "Kos" to 800000L, "Lainnya" to 150000L)
private val demoSpent = mapOf("Makan" to 320000L, "Transportasi" to 100000L, "Kuliah" to 150000L, "Kos" to 0L, "Lainnya" to 80000L)

internal fun financeStore(context: Context): FinanceStore {
    return FinanceStore(org.umn.application.studenthub.data.local.StudentHubDatabase.get(context))
}

@Composable
fun FinanceScreen() {
    val context = LocalContext.current
    val initialStore = remember { runCatching { financeStore(context) }.getOrNull() }
    val initialBudgets = remember { runCatching { initialStore?.budgets() ?: emptyMap() }.getOrDefault(emptyMap()) }
    var store by remember { mutableStateOf<FinanceStore?>(initialStore) }
    var budgets by remember { mutableStateOf<Map<String, Long>>(initialBudgets) }
    var loading by remember { mutableStateOf(initialStore == null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        if (store == null) {
            loading = true
            withContext(Dispatchers.IO) { runCatching { financeStore(context).let { it to it.budgets() } } }
                .onSuccess { (loaded, values) -> store = loaded; budgets = values; failed = false }
                .onFailure { failed = true }
            loading = false
        }
    }
    var version by remember { mutableIntStateOf(0) }
    var month by rememberSaveable { mutableStateOf("2026-10") }
    var monthsOpen by remember { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var budgetOpen by rememberSaveable { mutableStateOf(false) }
    var allTransactions by rememberSaveable { mutableStateOf(false) }
    var filterType by rememberSaveable { mutableStateOf("Semua") }
    var selectedCategoryFilter by rememberSaveable { mutableStateOf("Semua") }
    var inspectingTransaction by remember { mutableStateOf<StudentTransaction?>(null) }
    val scope = rememberCoroutineScope()

    val rawTransactions = remember(version, month, store) { store?.transactions.orEmpty().filter { it.date.startsWith(month) }.sortedByDescending { it.date } }
    val transactions = remember(rawTransactions, filterType, selectedCategoryFilter) {
        rawTransactions.filter { tx ->
            val matchesType = when (filterType) {
                "Pemasukan" -> tx.income
                "Pengeluaran" -> !tx.income
                else -> true
            }
            val matchesCategory = if (selectedCategoryFilter == "Semua") true else tx.category == selectedCategoryFilter
            matchesType && matchesCategory
        }
    }

    val summary = remember(version, month, store) { store?.summary(month) ?: MonthlyFinance(0, 0) }
    val spent = budgets.mapValues { (category, _) ->
        rawTransactions.filter { !it.income && it.category == category }.sumOf { it.amount }
    }
    if (loading || failed) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) { CircularProgressIndicator(); Text("Memuat keuangan…") }
            else { Text("Keuangan tidak dapat dibaca. Data lama tidak diubah.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { attempt++ }) { Text("Coba lagi") } }
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(org.umn.application.studenthub.ui.localization.lString("Keuangan Mahasiswa", "Student Finances"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(org.umn.application.studenthub.ui.localization.lString("Kelola uang saku, anggaran, dan pengeluaran harianmu.", "Manage your allowance, budgets, and daily expenses."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            Row(Modifier.fillMaxWidth().clickable { monthsOpen = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy", financeLocale)).replaceFirstChar { it.titlecase(financeLocale) }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ExpandMore, "Pilih bulan", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(monthsOpen, { monthsOpen = false }) {
                val selected = YearMonth.parse(month)
                listOf(selected.minusMonths(1), selected, selected.plusMonths(1), YearMonth.now()).distinct().forEach { option ->
                    DropdownMenuItem(text = { Text(option.format(DateTimeFormatter.ofPattern("MMMM yyyy", financeLocale))) }, onClick = { month = option.toString(); monthsOpen = false })
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(org.umn.application.studenthub.ui.localization.lString("Saldo transaksi lokal", "Local Transaction Balance"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(rupiah(summary.balance), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("finance-balance"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) { Text(org.umn.application.studenthub.ui.localization.lString("Pemasukan", "Income"), style = MaterialTheme.typography.bodySmall); Text(rupiah(summary.income), style = MaterialTheme.typography.titleMedium) }
                    Column(Modifier.weight(1f)) { Text(org.umn.application.studenthub.ui.localization.lString("Pengeluaran", "Expense"), style = MaterialTheme.typography.bodySmall); Text(rupiah(summary.expense), style = MaterialTheme.typography.titleMedium) }
                }
                Text(org.umn.application.studenthub.ui.localization.lString("Tersimpan secara lokal di SQLite; data transaksi diperbarui otomatis.", "Stored locally in SQLite; transaction data updates automatically."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(org.umn.application.studenthub.ui.localization.lString("Anggaran bulanan", "Monthly budget"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { budgetOpen = true }) { Text(org.umn.application.studenthub.ui.localization.lString("Atur", "Set")) }
            }
            Text(org.umn.application.studenthub.ui.localization.lString("${rupiah(spent.values.sum())} terpakai dari ${rupiah(budgets.values.sum())}", "${rupiah(spent.values.sum())} used of ${rupiah(budgets.values.sum())}"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                budgets.forEach { (category, budget) ->
                    val amount = spent.getValue(category)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(category, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            Text("${rupiah(amount)} / ${rupiah(budget)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(progress = { if (budget > 0) (amount.toFloat() / budget).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(4.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {})
                    }
                }
            }
        }
        if (month == "2026-10") Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Notifications, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(org.umn.application.studenthub.ui.localization.lString("Pengingat: Kos Rp800.000 • Bayar sebelum 10 Okt", "Reminder: Rent Rp800,000 • Pay before Oct 10"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(org.umn.application.studenthub.ui.localization.lString("Transaksi terbaru", "Recent transactions"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { allTransactions = !allTransactions }) {
                    Text(if (allTransactions) org.umn.application.studenthub.ui.localization.lString("Ringkas", "Collapse") else org.umn.application.studenthub.ui.localization.lString("Semua (${transactions.size})", "All (${transactions.size})"))
                }
            }
            // Filter chips
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Semua" to org.umn.application.studenthub.ui.localization.lString("Semua", "All"),
                    "Pengeluaran" to org.umn.application.studenthub.ui.localization.lString("Pengeluaran", "Expense"),
                    "Pemasukan" to org.umn.application.studenthub.ui.localization.lString("Pemasukan", "Income")
                ).forEach { (typeKey, label) ->
                    FilterChip(selected = filterType == typeKey, onClick = { filterType = typeKey }, label = { Text(label) })
                }
            }
            if (transactions.isEmpty()) Text(org.umn.application.studenthub.ui.localization.lString("Belum ada transaksi pada filter ini.", "No transactions found for this filter."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (if (allTransactions) transactions else transactions.take(4)).forEach { transaction ->
                ListItem(
                    headlineContent = { Text(transaction.title) },
                    supportingContent = { Text("${transaction.category} • ${LocalDate.parse(transaction.date).format(DateTimeFormatter.ofPattern("d MMM yyyy", financeLocale))}") },
                    trailingContent = {
                        Text(
                            (if (transaction.income) "+" else "−") + rupiah(transaction.amount),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (transaction.income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable { inspectingTransaction = transaction },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            }
        }

        Button(onClick = { adding = true }, enabled = store != null, modifier = Modifier.fillMaxWidth().testTag("add-transaction-btn")) {
            Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(org.umn.application.studenthub.ui.localization.lString("Catat transaksi", "Record transaction"))
        }
    }
    if (adding) TransactionDialog(month, onDismiss = { adding = false }) { title, amount, category, income, date ->
        withContext(Dispatchers.IO) { requireNotNull(store).add(title, amount, category, income, date) }
        version++; adding = false
    }
    if (budgetOpen) BudgetDialog(budgets, onDismiss = { budgetOpen = false }) { values ->
        withContext(Dispatchers.IO) { requireNotNull(store).saveBudgets(values) }
        budgets = withContext(Dispatchers.IO) { requireNotNull(store).budgets() }
        version++; budgetOpen = false
    }
    inspectingTransaction?.let { tx ->
        AlertDialog(
            onDismissRequest = { inspectingTransaction = null },
            title = { Text(tx.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(org.umn.application.studenthub.ui.localization.lString("Kategori: ${tx.category}", "Category: ${tx.category}"))
                    Text(
                        if (tx.income) org.umn.application.studenthub.ui.localization.lString("Tipe: Pemasukan (+)", "Type: Income (+)")
                        else org.umn.application.studenthub.ui.localization.lString("Tipe: Pengeluaran (−)", "Type: Expense (−)")
                    )
                    Text(org.umn.application.studenthub.ui.localization.lString("Nominal: ${rupiah(tx.amount)}", "Amount: ${rupiah(tx.amount)}"))
                    Text(org.umn.application.studenthub.ui.localization.lString("Tanggal: ${tx.date}", "Date: ${tx.date}"))
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingTransaction = null }) { Text(org.umn.application.studenthub.ui.localization.lString("Tutup", "Close")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { store?.delete(tx.id) }
                        version++
                        inspectingTransaction = null
                    }
                }) { Text(org.umn.application.studenthub.ui.localization.lString("Hapus", "Delete"), color = MaterialTheme.colorScheme.error) }
            }
        )
    }
}

@Composable
private fun TransactionDialog(month: String, onDismiss: () -> Unit, onSave: suspend (String, String, String, Boolean, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Makan") }
    var income by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(if (YearMonth.now().toString() == month) LocalDate.now().toString() else "$month-01") }
    var error by remember { mutableStateOf<String?>(null) }

    val titleValid = title.isNotBlank() && title.length <= 200
    val amountNum = amount.toLongOrNull()
    val amountValid = amountNum != null && amountNum in 1..1_000_000_000_000L
    val dateValid = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(date) && runCatching { LocalDate.parse(date) }.isSuccess

    AlertDialog(onDismissRequest = onDismiss, title = { Text(org.umn.application.studenthub.ui.localization.lString("Catat transaksi", "Record transaction")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().testTag("transaction-title"),
                label = { Text(org.umn.application.studenthub.ui.localization.lString("Nama transaksi", "Transaction name")) },
                isError = title.isNotEmpty() && !titleValid,
                supportingText = {
                    if (title.isNotEmpty() && !titleValid) Text(org.umn.application.studenthub.ui.localization.lString("Nama transaksi wajib diisi (maks. 200 karakter)", "Transaction name is required (max 200 characters)"))
                },
                singleLine = true
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                modifier = Modifier.fillMaxWidth().testTag("transaction-amount"),
                label = { Text(org.umn.application.studenthub.ui.localization.lString("Nominal rupiah", "Amount (IDR)")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = amount.isNotEmpty() && !amountValid,
                supportingText = {
                    if (amount.isNotEmpty() && !amountValid) Text(org.umn.application.studenthub.ui.localization.lString("Masukkan angka bulat positif tanpa titik atau koma", "Enter positive whole number without dots or commas"))
                    else Text(org.umn.application.studenthub.ui.localization.lString("Angka bulat tanpa titik atau koma", "Whole numbers without dots or commas"))
                },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!income, { income = false }, { Text(org.umn.application.studenthub.ui.localization.lString("Keluar", "Expense")) })
                FilterChip(income, { income = true }, { Text(org.umn.application.studenthub.ui.localization.lString("Masuk", "Income")) })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                defaultBudgets.keys.forEach { option ->
                    val optLabel = when (option) {
                        "Makan" -> org.umn.application.studenthub.ui.localization.lString("Makan", "Food")
                        "Transportasi" -> org.umn.application.studenthub.ui.localization.lString("Transportasi", "Transport")
                        "Kuliah" -> org.umn.application.studenthub.ui.localization.lString("Kuliah", "Tuition")
                        "Kos" -> org.umn.application.studenthub.ui.localization.lString("Kos", "Rent")
                        else -> org.umn.application.studenthub.ui.localization.lString("Lainnya", "Others")
                    }
                    FilterChip(category == option, { category = option }, { Text(optLabel) })
                }
            }
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(org.umn.application.studenthub.ui.localization.lString("Tanggal (YYYY-MM-DD)", "Date (YYYY-MM-DD)")) },
                isError = date.isNotEmpty() && !dateValid,
                supportingText = {
                    if (date.isNotEmpty() && !dateValid) Text(org.umn.application.studenthub.ui.localization.lString("Format tanggal harus YYYY-MM-DD (contoh: 2026-10-05)", "Date format must be YYYY-MM-DD (e.g. 2026-10-05)"))
                },
                singleLine = true
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !saving, onClick = {
        saving = true
        scope.launch { runCatching { onSave(title, amount, category, income, date) }.onFailure { error = it.message ?: "Transaksi gagal disimpan." }; saving = false }
    }) { Text(org.umn.application.studenthub.ui.localization.lString("Simpan transaksi", "Save transaction")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(org.umn.application.studenthub.ui.localization.lString("Batal", "Cancel")) } })
}

@Composable
private fun BudgetDialog(budgets: Map<String, Long>, onDismiss: () -> Unit, onSave: suspend (Map<String, Long>) -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val values = remember { mutableStateMapOf<String, String>().apply { budgets.forEach { (key, value) -> put(key, value.toString()) } } }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(org.umn.application.studenthub.ui.localization.lString("Atur anggaran bulanan", "Set monthly budget")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            budgets.keys.forEach { key ->
                val keyLabel = when (key) {
                    "Makan" -> org.umn.application.studenthub.ui.localization.lString("Makan", "Food")
                    "Transportasi" -> org.umn.application.studenthub.ui.localization.lString("Transportasi", "Transport")
                    "Kuliah" -> org.umn.application.studenthub.ui.localization.lString("Kuliah", "Tuition")
                    "Kos" -> org.umn.application.studenthub.ui.localization.lString("Kos", "Rent")
                    else -> org.umn.application.studenthub.ui.localization.lString("Lainnya", "Others")
                }
                OutlinedTextField(values[key].orEmpty(), { values[key] = it }, label = { Text(keyLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !saving, onClick = {
        saving = true
        scope.launch { runCatching {
            val parsed = values.mapValues { (_, value) -> value.toLongOrNull().also { require(it != null && it in 1..1_000_000_000_000L) { "Anggaran harus berupa rupiah bulat positif." } }!! }
            onSave(parsed)
        }.onFailure { error = it.message }; saving = false }
    }) { Text(org.umn.application.studenthub.ui.localization.lString("Simpan anggaran", "Save budget")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(org.umn.application.studenthub.ui.localization.lString("Batal", "Cancel")) } })
}
