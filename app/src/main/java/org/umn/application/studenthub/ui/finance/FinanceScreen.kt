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
    var store by remember { mutableStateOf<FinanceStore?>(null) }
    var budgets by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        loading = true
        withContext(Dispatchers.IO) { runCatching { financeStore(context).let { it to it.budgets() } } }
            .onSuccess { (loaded, values) -> store = loaded; budgets = values; failed = false }
            .onFailure { failed = true }
        loading = false
    }
    var version by remember { mutableIntStateOf(0) }
    var month by rememberSaveable { mutableStateOf("2026-10") }
    var monthsOpen by remember { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var budgetOpen by rememberSaveable { mutableStateOf(false) }
    var allTransactions by rememberSaveable { mutableStateOf(false) }
    val transactions = remember(version, month, store) { store?.transactions.orEmpty().filter { it.date.startsWith(month) }.sortedByDescending { it.date } }
    // The inaccessible summary and transaction slots are not reconstructed. Only known
    // category totals are demo data; the balance card below describes user-entered data.
    val summary = remember(version, month, store) { store?.summary(month) ?: MonthlyFinance(0, 0) }
    val spent = budgets.mapValues { (category, _) ->
        (if (month == "2026-10") demoSpent[category] ?: 0 else 0) + transactions.filter { !it.income && it.category == category }.sumOf { it.amount }
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
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box {
            Row(Modifier.fillMaxWidth().clickable { monthsOpen = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy", financeLocale)).replaceFirstChar { it.titlecase(financeLocale) }, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
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
                Text("Saldo transaksi lokal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(rupiah(summary.balance), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("finance-balance"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) { Text("Pemasukan", style = MaterialTheme.typography.bodySmall); Text(rupiah(summary.income), style = MaterialTheme.typography.titleMedium) }
                    Column(Modifier.weight(1f)) { Text("Pengeluaran", style = MaterialTheme.typography.bodySmall); Text(rupiah(summary.expense), style = MaterialTheme.typography.titleMedium) }
                }
                Text("Hanya transaksi yang kamu catat; tidak termasuk contoh anggaran.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Anggaran bulanan", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { budgetOpen = true }) { Text("Atur") }
            }
            Text("${rupiah(spent.values.sum())} terpakai dari ${rupiah(budgets.values.sum())}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                budgets.forEach { (category, budget) ->
                    val amount = spent.getValue(category)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(category, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            Text("${rupiah(amount)} / ${rupiah(budget)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(progress = { (amount.toFloat() / budget).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {})
                    }
                }
            }
        }
        if (month == "2026-10") Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Notifications, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Kos Rp800.000 • Bayar sebelum 10 Okt", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Transaksi terbaru", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { allTransactions = !allTransactions }) { Text(if (allTransactions) "Ringkas" else "Semua") }
            }
            if (transactions.isEmpty()) Text("Belum ada transaksi lokal bulan ini.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (if (allTransactions) transactions else transactions.take(3)).forEach { transaction ->
                ListItem(headlineContent = { Text(transaction.title) }, supportingContent = { Text("${transaction.category} • ${LocalDate.parse(transaction.date).format(DateTimeFormatter.ofPattern("d MMM", financeLocale))}") }, trailingContent = { Text((if (transaction.income) "+" else "−") + rupiah(transaction.amount), style = MaterialTheme.typography.labelMedium) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            }
        }

        Button(onClick = { adding = true }, enabled = store != null) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Catat transaksi") }
        Text("Contoh anggaran Oktober dari desain • Data lokal, tanpa koneksi bank.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Catat transaksi") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().testTag("transaction-title"), label = { Text("Nama transaksi") }, singleLine = true)
            OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth().testTag("transaction-amount"), label = { Text("Nominal rupiah") }, supportingText = { Text("Angka bulat tanpa titik atau koma") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!income, { income = false }, { Text("Keluar") }); FilterChip(income, { income = true }, { Text("Masuk") })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                defaultBudgets.keys.forEach { option -> FilterChip(category == option, { category = option }, { Text(option) }) }
            }
            OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), label = { Text("Tanggal (YYYY-MM-DD)") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !saving, onClick = {
        saving = true
        scope.launch { runCatching { onSave(title, amount, category, income, date) }.onFailure { error = it.message ?: "Transaksi gagal disimpan." }; saving = false }
    }) { Text("Simpan transaksi") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun BudgetDialog(budgets: Map<String, Long>, onDismiss: () -> Unit, onSave: suspend (Map<String, Long>) -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val values = remember { mutableStateMapOf<String, String>().apply { budgets.forEach { (key, value) -> put(key, value.toString()) } } }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Atur anggaran bulanan") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            budgets.keys.forEach { key -> OutlinedTextField(values[key].orEmpty(), { values[key] = it }, label = { Text(key) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !saving, onClick = {
        saving = true
        scope.launch { runCatching {
            val parsed = values.mapValues { (_, value) -> value.toLongOrNull().also { require(it != null && it in 1..1_000_000_000_000L) { "Anggaran harus berupa rupiah bulat positif." } }!! }
            onSave(parsed)
        }.onFailure { error = it.message }; saving = false }
    }) { Text("Simpan anggaran") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}
