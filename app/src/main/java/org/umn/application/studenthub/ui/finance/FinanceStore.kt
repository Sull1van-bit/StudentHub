package org.umn.application.studenthub.ui.finance

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.data.local.TransactionRow

data class StudentTransaction(val id: String, val title: String, val amount: Long, val category: String, val income: Boolean, val date: String)
data class MonthlyFinance(val income: Long, val expense: Long) { val balance: Long get() = income - expense }

class FinanceStore(private val read: () -> String?, private val write: (String) -> Unit) {
    private var database: StudentHubDatabase? = null
    constructor(database: StudentHubDatabase) : this({ null }, {}) {
        this.database = database
        transactions = database.transactions().map { StudentTransaction(it.id, it.title, it.amount, it.category, it.income, it.date) }
    }

    fun budgets(): Map<String, Long> = requireNotNull(database).budgets()
    fun saveBudgets(values: Map<String, Long>) = requireNotNull(database).saveBudgets(values)
    var transactions: List<StudentTransaction> = read()?.let { raw ->
        if (raw.isEmpty()) emptyList() else raw.lines().map { line ->
            val f = line.split('\t').map { URLDecoder.decode(it, "UTF-8") }
            require(f.size == 6)
            StudentTransaction(f[0], f[1], f[2].toLong(), f[3], f[4].toBooleanStrict(), f[5])
        }
    } ?: emptyList()
        private set

    fun summary(month: String): MonthlyFinance {
        val entries = transactions.filter { it.date.startsWith(month) }
        return MonthlyFinance(entries.filter { it.income }.sumOf { it.amount }, entries.filterNot { it.income }.sumOf { it.amount })
    }

    fun add(title: String, amount: String, category: String, income: Boolean, date: String) {
        require(title.isNotBlank() && title.length <= 200) { "Nama transaksi wajib diisi (maks. 200 karakter)." }
        require(amount.matches(Regex("[0-9]+"))) { "Masukkan nominal rupiah bulat tanpa titik atau koma." }
        val rupiah = amount.toLongOrNull()
        require(rupiah != null && rupiah in 1..1_000_000_000_000L) { "Nominal harus 1–1.000.000.000.000 rupiah." }
        require(category in listOf("Makan", "Transportasi", "Kuliah", "Kos", "Lainnya")) { "Kategori tidak valid." }
        require(runCatching { java.time.LocalDate.parse(date) }.isSuccess) { "Tanggal tidak valid." }
        require(transactions.size < 10000) { "Batas transaksi lokal tercapai." }
        val transaction = StudentTransaction(UUID.randomUUID().toString(), title.trim(), rupiah, category, income, date)
        val updated = transactions + transaction
        if (database != null) database!!.addTransaction(TransactionRow(transaction.id, transaction.title, transaction.amount, transaction.category, transaction.income, transaction.date))
        else write(updated.joinToString("\n") { entry ->
            listOf(entry.id, entry.title, entry.amount.toString(), entry.category, entry.income.toString(), entry.date)
                .joinToString("\t") { URLEncoder.encode(it, "UTF-8") }
        })
        transactions = updated
    }

    fun delete(id: String) {
        val updated = transactions.filterNot { it.id == id }
        if (database != null) database!!.deleteTransaction(id)
        else write(updated.joinToString("\n") { entry ->
            listOf(entry.id, entry.title, entry.amount.toString(), entry.category, entry.income.toString(), entry.date)
                .joinToString("\t") { URLEncoder.encode(it, "UTF-8") }
        })
        transactions = updated
    }
}
