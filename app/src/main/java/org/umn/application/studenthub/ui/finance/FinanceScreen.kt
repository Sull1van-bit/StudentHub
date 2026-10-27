package org.umn.application.studenthub.ui.finance

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.umn.application.studenthub.data.local.ReminderItemRow
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.ui.localization.lString

private val financeLocale = Locale.forLanguageTag("id-ID")
private fun rupiah(value: Long) = "Rp" + NumberFormat.getIntegerInstance(financeLocale).format(value)
private val budgetCategories = listOf("Makan", "Transportasi", "Kuliah", "Kos", "Lainnya")

internal fun financeStore(context: Context): FinanceStore {
    return FinanceStore(StudentHubDatabase.get(context))
}

@Composable
fun FinanceScreen() {
    val context = LocalContext.current
    val database = remember(context) { runCatching { StudentHubDatabase.get(context) }.getOrNull() }
    val initialStore = remember { runCatching { financeStore(context) }.getOrNull() }
    val initialBudgets = remember { runCatching { initialStore?.budgets() ?: emptyMap() }.getOrDefault(emptyMap()) }
    var store by remember { mutableStateOf<FinanceStore?>(initialStore) }
    var budgets by remember { mutableStateOf<Map<String, Long>>(initialBudgets) }
    var financeReminders by remember {
        mutableStateOf(
            runCatching { database?.reminders()?.filter { it.category == "Keuangan" }.orEmpty() }.getOrDefault(emptyList())
        )
    }
    var loading by remember { mutableStateOf(initialStore == null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt, version) {
        if (store == null) {
            loading = true
            withContext(Dispatchers.IO) {
                runCatching {
                    val s = financeStore(context)
                    val b = s.budgets()
                    val r = StudentHubDatabase.get(context).reminders().filter { it.category == "Keuangan" }
                    Triple(s, b, r)
                }
            }.onSuccess { (loaded, values, rems) ->
                store = loaded
                budgets = values
                financeReminders = rems
                failed = false
            }.onFailure {
                failed = true
            }
            loading = false
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    StudentHubDatabase.get(context).reminders().filter { it.category == "Keuangan" }
                }
            }.onSuccess { rems ->
                financeReminders = rems
            }
        }
    }

    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var monthsOpen by remember { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var budgetOpen by rememberSaveable { mutableStateOf(false) }
    var addingReminder by rememberSaveable { mutableStateOf(false) }
    var allTransactions by rememberSaveable { mutableStateOf(false) }
    var filterType by rememberSaveable { mutableStateOf("Semua") }
    var selectedCategoryFilter by rememberSaveable { mutableStateOf("Semua") }
    var inspectingTransaction by remember { mutableStateOf<StudentTransaction?>(null) }
    val scope = rememberCoroutineScope()

    val rawTransactions = remember(version, month, store) {
        store?.transactions.orEmpty().filter { it.date.startsWith(month) }.sortedByDescending { it.date }
    }
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
            if (loading) {
                CircularProgressIndicator()
                Text(lString("Memuat data keuangan…", "Loading financial data…"))
            } else {
                Text(lString("Gagal memuat data keuangan.", "Failed to load financial data."), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { attempt++ }) { Text(lString("Coba lagi", "Retry")) }
            }
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(lString("Keuangan Mahasiswa", "Student Finances"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(lString("Kelola uang saku, anggaran, dan pengeluaran harianmu.", "Manage your allowance, budgets, and daily expenses."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            Row(Modifier.fillMaxWidth().clickable { monthsOpen = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy", financeLocale)).replaceFirstChar { it.titlecase(financeLocale) }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ExpandMore, "Pilih bulan", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(monthsOpen, { monthsOpen = false }) {
                val selected = YearMonth.parse(month)
                val txMonths = store?.transactions.orEmpty().mapNotNull {
                    runCatching { YearMonth.parse(it.date.substring(0, 7)) }.getOrNull()
                }
                (listOf(selected.minusMonths(1), selected, selected.plusMonths(1), YearMonth.now()) + txMonths)
                    .distinct()
                    .sortedDescending()
                    .forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.format(DateTimeFormatter.ofPattern("MMMM yyyy", financeLocale))) },
                            onClick = { month = option.toString(); monthsOpen = false }
                        )
                    }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(lString("Total Saldo", "Current Balance"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(rupiah(summary.balance), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("finance-balance"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(lString("Pemasukan", "Income"), style = MaterialTheme.typography.bodySmall)
                        Text(rupiah(summary.income), style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(lString("Pengeluaran", "Expense"), style = MaterialTheme.typography.bodySmall)
                        Text(rupiah(summary.expense), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lString("Anggaran bulanan", "Monthly budget"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { budgetOpen = true }) { Text(lString("Atur", "Set")) }
            }
            Text(
                lString(
                    "${rupiah(spent.values.sum())} terpakai dari ${rupiah(budgets.values.sum())}",
                    "${rupiah(spent.values.sum())} used of ${rupiah(budgets.values.sum())}"
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                budgets.forEach { (category, budget) ->
                    val amount = spent.getValue(category)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(category, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            Text("${rupiah(amount)} / ${rupiah(budget)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { if (budget > 0) (amount.toFloat() / budget).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            drawStopIndicator = {}
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lString("Pengingat Keuangan", "Finance Reminders"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { addingReminder = true }, modifier = Modifier.testTag("add-finance-reminder-btn")) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(lString("Tambah Pengingat", "Add Reminder"))
                }
            }
            if (financeReminders.isEmpty()) {
                Text(
                    lString("Belum ada pengingat keuangan (mis. bayar kos / UKT).", "No finance reminders yet (e.g. rent / tuition)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                financeReminders.forEach { reminder ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            null,
                            Modifier.size(20.dp),
                            tint = if (reminder.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(Modifier.weight(1f)) {
                            Text(reminder.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            val sub = listOfNotNull(
                                reminder.time.takeIf { it.isNotBlank() },
                                reminder.detail.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                            if (sub.isNotBlank()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = reminder.enabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    StudentHubDatabase.get(context).toggleReminder(reminder.id, enabled)
                                    financeReminders = StudentHubDatabase.get(context).reminders().filter { it.category == "Keuangan" }
                                    version++
                                }
                            }
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    StudentHubDatabase.get(context).deleteReminder(reminder.id)
                                    financeReminders = StudentHubDatabase.get(context).reminders().filter { it.category == "Keuangan" }
                                    version++
                                }
                            }
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = lString("Hapus pengingat", "Delete reminder"), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lString("Transaksi terbaru", "Recent transactions"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { allTransactions = !allTransactions }) {
                    Text(if (allTransactions) lString("Ringkas", "Collapse") else lString("Semua (${transactions.size})", "All (${transactions.size})"))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Semua" to lString("Semua", "All"),
                    "Pengeluaran" to lString("Pengeluaran", "Expense"),
                    "Pemasukan" to lString("Pemasukan", "Income")
                ).forEach { (typeKey, label) ->
                    FilterChip(selected = filterType == typeKey, onClick = { filterType = typeKey }, label = { Text(label) })
                }
            }
            if (transactions.isEmpty()) Text(lString("Belum ada transaksi pada filter ini.", "No transactions found for this filter."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(lString("Catat transaksi", "Record transaction"))
        }
    }
    if (adding) TransactionDialog(month, onDismiss = { adding = false }) { title, amount, category, income, date ->
        requireNotNull(store).add(title, amount, category, income, date)
        version++; adding = false
    }
    if (budgetOpen) BudgetDialog(budgets, onDismiss = { budgetOpen = false }) { values ->
        requireNotNull(store).saveBudgets(values)
        budgets = requireNotNull(store).budgets()
        version++; budgetOpen = false
    }
    if (addingReminder) FinanceReminderDialog(
        onDismiss = { addingReminder = false },
        onSave = { title, time, detail ->
            StudentHubDatabase.get(context).addReminder(
                title = title.trim(),
                detail = detail.trim(),
                time = time.trim(),
                category = "Keuangan",
                enabled = true
            )
            financeReminders = StudentHubDatabase.get(context).reminders().filter { it.category == "Keuangan" }
            version++
            addingReminder = false
        }
    )
    inspectingTransaction?.let { tx ->
        AlertDialog(
            onDismissRequest = { inspectingTransaction = null },
            title = { Text(tx.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(lString("Kategori: ${tx.category}", "Category: ${tx.category}"))
                    Text(
                        if (tx.income) lString("Tipe: Pemasukan (+)", "Type: Income (+)")
                        else lString("Tipe: Pengeluaran (−)", "Type: Expense (−)")
                    )
                    Text(lString("Nominal: ${rupiah(tx.amount)}", "Amount: ${rupiah(tx.amount)}"))
                    Text(lString("Tanggal: ${tx.date}", "Date: ${tx.date}"))
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingTransaction = null }) { Text(lString("Tutup", "Close")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { store?.delete(tx.id) }
                        version++
                        inspectingTransaction = null
                    }
                }) { Text(lString("Hapus", "Delete"), color = MaterialTheme.colorScheme.error) }
            }
        )
    }
}

@Composable
private fun FinanceReminderDialog(
    onDismiss: () -> Unit,
    onSave: suspend (String, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("") }
    var detail by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lString("Tambah Pengingat Keuangan", "Add Finance Reminder")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(lString("Judul pengingat", "Reminder title")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("finance-reminder-title")
                )
                org.umn.application.studenthub.ui.components.TimePickerField(
                    value = time,
                    onValueChange = { time = it },
                    label = lString("Waktu (HH.mm)", "Time (HH.mm)"),
                    placeholder = "09.00",
                    testTag = "finance-reminder-time"
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text(lString("Keterangan / Tanggal jatuh tempo", "Detail / Due date")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("finance-reminder-detail")
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && title.isNotBlank(),
                onClick = {
                    saving = true
                    scope.launch {
                        onSave(title, time.ifBlank { "09.00" }, detail)
                        saving = false
                    }
                }
            ) {
                Text(lString("Simpan", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(lString("Batal", "Cancel"))
            }
        }
    )
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

    AlertDialog(onDismissRequest = onDismiss, title = { Text(lString("Catat transaksi", "Record transaction")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().testTag("transaction-title"),
                label = { Text(lString("Nama transaksi", "Transaction name")) },
                isError = title.isNotEmpty() && !titleValid,
                supportingText = {
                    if (title.isNotEmpty() && !titleValid) Text(lString("Nama transaksi wajib diisi (maks. 200 karakter)", "Transaction name is required (max 200 characters)"))
                },
                singleLine = true
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = org.umn.application.studenthub.ui.components.cleanRupiahInput(it) },
                modifier = Modifier.fillMaxWidth().testTag("transaction-amount"),
                label = { Text(lString("Nominal rupiah", "Amount (IDR)")) },
                prefix = { Text("Rp ") },
                visualTransformation = remember { org.umn.application.studenthub.ui.components.RupiahVisualTransformation() },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = amount.isNotEmpty() && !amountValid,
                supportingText = if (amount.isNotEmpty() && !amountValid) {
                    { Text(lString("Masukkan nominal antara Rp 1 dan Rp 1.000.000.000.000", "Enter amount between Rp 1 and Rp 1,000,000,000,000")) }
                } else null,
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!income, { income = false }, { Text(lString("Keluar", "Expense")) })
                FilterChip(income, { income = true }, { Text(lString("Masuk", "Income")) })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                budgetCategories.forEach { option ->
                    val optLabel = when (option) {
                        "Makan" -> lString("Makan", "Food")
                        "Transportasi" -> lString("Transportasi", "Transport")
                        "Kuliah" -> lString("Kuliah", "Tuition")
                        "Kos" -> lString("Kos", "Rent")
                        else -> lString("Lainnya", "Others")
                    }
                    FilterChip(category == option, { category = option }, { Text(optLabel) })
                }
            }
            org.umn.application.studenthub.ui.components.DatePickerField(
                value = date,
                onValueChange = { date = it },
                label = lString("Tanggal (YYYY-MM-DD)", "Date (YYYY-MM-DD)"),
                isError = date.isNotEmpty() && !dateValid,
                supportingText = {
                    if (date.isNotEmpty() && !dateValid) Text(lString("Format tanggal harus YYYY-MM-DD (contoh: 2026-10-05)", "Date format must be YYYY-MM-DD (e.g. 2026-10-05)"))
                }
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !saving, onClick = {
        saving = true
        scope.launch { runCatching { onSave(title, amount, category, income, date) }.onFailure { error = it.message ?: "Transaksi gagal disimpan." }; saving = false }
    }) { Text(lString("Simpan transaksi", "Save transaction")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(lString("Batal", "Cancel")) } })
}

@Composable
private fun BudgetDialog(budgets: Map<String, Long>, onDismiss: () -> Unit, onSave: suspend (Map<String, Long>) -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val values = remember {
        mutableStateMapOf<String, String>().apply {
            budgetCategories.forEach { key ->
                val current = budgets[key] ?: 0L
                put(key, if (current > 0L) current.toString() else "")
            }
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    val rupiahTransformation = remember { org.umn.application.studenthub.ui.components.RupiahVisualTransformation() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(lString("Atur anggaran bulanan", "Set monthly budget")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            budgetCategories.forEach { key ->
                val keyLabel = when (key) {
                    "Makan" -> lString("Makan", "Food")
                    "Transportasi" -> lString("Transportasi", "Transport")
                    "Kuliah" -> lString("Kuliah", "Tuition")
                    "Kos" -> lString("Kos", "Rent")
                    else -> lString("Lainnya", "Others")
                }
                OutlinedTextField(
                    value = values[key].orEmpty(),
                    onValueChange = { values[key] = org.umn.application.studenthub.ui.components.cleanRupiahInput(it) },
                    modifier = Modifier.fillMaxWidth().testTag("budget-$key"),
                    label = { Text(keyLabel) },
                    prefix = { Text("Rp ") },
                    visualTransformation = rupiahTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !saving, onClick = {
        saving = true
        scope.launch { runCatching {
            val parsed = buildMap<String, Long> {
                values.forEach { (key, value) ->
                    val trimmed = value.replace(".", "").replace(",", "").trim()
                    if (trimmed.isNotEmpty() && trimmed != "0") {
                        val amount = trimmed.toLongOrNull()
                        require(amount != null && amount in 1..1_000_000_000_000L) { "Anggaran harus berupa rupiah bulat positif." }
                        put(key, amount)
                    }
                }
            }
            onSave(parsed)
        }.onFailure { error = it.message }; saving = false }
    }) { Text(lString("Simpan anggaran", "Save budget")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(lString("Batal", "Cancel")) } })
}
