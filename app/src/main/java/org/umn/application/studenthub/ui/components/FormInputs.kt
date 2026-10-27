package org.umn.application.studenthub.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import org.umn.application.studenthub.data.local.CurriculumCourse
import org.umn.application.studenthub.data.local.UmnCurriculum
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.umn.application.studenthub.ui.localization.lString
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun cleanRupiahInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    return digits.trimStart('0').ifEmpty { "0" }
}

fun formatRupiahDigits(digits: String): String {
    val clean = digits.filter { it.isDigit() }
    if (clean.isEmpty()) return ""
    val sb = StringBuilder()
    val len = clean.length
    for (i in 0 until len) {
        sb.append(clean[i])
        val remaining = len - 1 - i
        if (remaining > 0 && remaining % 3 == 0) {
            sb.append('.')
        }
    }
    return sb.toString()
}

class RupiahVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty() || !raw.all { it.isDigit() }) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val formatted = formatRupiahDigits(raw)
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val clamped = offset.coerceAtMost(raw.length)
                var digitsSeen = 0
                for (i in formatted.indices) {
                    if (formatted[i].isDigit()) {
                        digitsSeen++
                        if (digitsSeen == clamped) {
                            return i + 1
                        }
                    }
                }
                return formatted.length
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                val clamped = offset.coerceAtMost(formatted.length)
                var digitsCount = 0
                for (i in 0 until clamped) {
                    if (formatted[i].isDigit()) {
                        digitsCount++
                    }
                }
                return digitsCount.coerceAtMost(raw.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

fun defaultEndTimeFor(startTime: String): String {
    val clean = startTime.trim().replace(':', '.')
    val parts = clean.split('.')
    if (parts.size == 2) {
        val h = parts[0].toIntOrNull() ?: return ""
        val m = parts[1].toIntOrNull() ?: return ""
        if (h in 0..23 && m in 0..59) {
            val endTotal = (h * 60 + m + 100) % (24 * 60)
            return String.format("%02d.%02d", endTotal / 60, endTotal % 60)
        }
    }
    return ""
}

fun combineTimeRange(startTime: String, endTime: String): String {
    val start = startTime.trim()
    val end = endTime.trim()
    if (start.isBlank()) return ""
    val resolvedEnd = if (end.isNotBlank()) end else defaultEndTimeFor(start)
    return if (resolvedEnd.isNotBlank()) "$start–$resolvedEnd" else start
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "09.00",
    isError: Boolean = false,
    testTag: String? = null
) {
    var showPicker by remember { mutableStateOf(false) }

    val parsedParts = remember(value) {
        val clean = value.trim().replace(':', '.')
        val p = clean.split('.')
        val h = p.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val m = p.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        h to m
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            isError = isError,
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { showPicker = true }) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = lString("Pilih jam", "Pick time"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .let { if (testTag != null) it.testTag(testTag) else it }
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showPicker = true }
        )
    }

    if (showPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = parsedParts.first,
            initialMinute = parsedParts.second,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = pickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val formatted = String.format("%02d.%02d", pickerState.hour, pickerState.minute)
                        onValueChange(formatted)
                        showPicker = false
                    }
                ) {
                    Text(lString("Pilih", "Select"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(lString("Batal", "Cancel"))
                }
            }
        )
    }
}

@Composable
fun TimeRangePickerRow(
    startTime: String,
    endTime: String,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    startLabel: String = lString("Jam Mulai", "Start Time"),
    endLabel: String = lString("Jam Selesai", "End Time"),
    isError: Boolean = false,
    startTestTag: String? = null,
    endTestTag: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimePickerField(
            value = startTime,
            onValueChange = { newStart ->
                onStartTimeChange(newStart)
                if (endTime.isBlank()) {
                    val suggested = defaultEndTimeFor(newStart)
                    if (suggested.isNotBlank()) {
                        onEndTimeChange(suggested)
                    }
                }
            },
            label = startLabel,
            placeholder = "07.00",
            isError = isError,
            testTag = startTestTag,
            modifier = Modifier.weight(1f)
        )
        TimePickerField(
            value = endTime,
            onValueChange = onEndTimeChange,
            label = endLabel,
            placeholder = if (startTime.isNotBlank()) defaultEndTimeFor(startTime).ifBlank { "08.40" } else "08.40",
            isError = isError,
            testTag = endTestTag,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = LocalDate.now().toString(),
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    testTag: String? = null
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        isError = isError,
        supportingText = supportingText,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = lString("Pilih tanggal", "Pick date"),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it }
    )

    if (showPicker) {
        val initialMillis = remember(value) {
            runCatching {
                LocalDate.parse(value.trim())
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull() ?: System.currentTimeMillis()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            onValueChange(picked.toString())
                        }
                        showPicker = false
                    }
                ) {
                    Text(lString("Pilih", "Select"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(lString("Batal", "Cancel"))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun CurriculumCourseDropdown(
    currentSemester: Int,
    selectedTitle: String,
    onCourseSelected: (CurriculumCourse?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val compCourses = remember(currentSemester) { UmnCurriculum.forSemester(currentSemester) }
    val electives = remember { UmnCurriculum.electives() }
    val otherCourses = remember(currentSemester) { UmnCurriculum.otherSemesters(currentSemester) }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedTitle.ifBlank { "" },
            onValueChange = {},
            readOnly = true,
            label = { Text(lString("Pilih Mata Kuliah Kurikulum UMN", "Select Course from UMN Curriculum")) },
            placeholder = { Text(lString("Sentuh untuk memilih mata kuliah...", "Tap to select course...")) },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = !expanded }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 320.dp)
        ) {
            if (compCourses.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        lString("Mata Kuliah Wajib (Semester $currentSemester)", "Compulsory Courses (Semester $currentSemester)"),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                compCourses.forEach { c ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("${c.code} · ${c.title}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("${c.sks} SKS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            onCourseSelected(c)
                            expanded = false
                        }
                    )
                }
            }

            if (electives.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        lString("Mata Kuliah Pilihan (Elective)", "Elective Courses"),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                electives.forEach { c ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("${c.code} · ${c.title}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("${c.sks} SKS • Pilihan (Elective)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            onCourseSelected(c)
                            expanded = false
                        }
                    )
                }
            }

            if (otherCourses.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        lString("Mata Kuliah Semester Lain (Mengulang)", "Other Semesters (Repeat Course)"),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                otherCourses.forEach { c ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("${c.code} · ${c.title}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("Semester ${c.semester} • ${c.sks} SKS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            onCourseSelected(c)
                            expanded = false
                        }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = {
                    Text(
                        lString("Input Manual (Ketik Sendiri)", "Manual Input (Custom)"),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    onCourseSelected(null)
                    expanded = false
                }
            )
        }
    }
}

