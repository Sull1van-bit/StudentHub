package org.umn.application.studenthub.ui.scanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.umn.application.studenthub.ui.localization.lString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanNoteScreen(
    initialCourseId: String = "IF570",
    onNavigateBack: () -> Unit,
    onSaveToNote: (title: String, course: String, content: String) -> Unit
) {
    var isCaptured by rememberSaveable { mutableStateOf(false) }
    var flashOn by rememberSaveable { mutableStateOf(false) }
    var isFrontCamera by rememberSaveable { mutableStateOf(false) }
    var selectedFilter by rememberSaveable { mutableStateOf("B&W Dokumen") }
    var selectedCourse by rememberSaveable { mutableStateOf(if (initialCourseId.isNotBlank()) initialCourseId else "IF570") }
    val defaultTitle = lString("Catatan Fisik • Pertemuan Kuliah", "Physical Notes • Lecture")
    var customTitle by rememberSaveable { mutableStateOf(defaultTitle) }

    // Mock OCR text variants
    val extractedText = remember(selectedCourse) {
        when (selectedCourse) {
            "IF570" -> """Ringkasan Kuliah IF570: Pemrograman Mobile
Tanggal: 14 Oktober 2026
Topik: Navigasi dan State Management Jetpack Compose

Poin Penting:
1. NavHost & NavController adalah inti navigasi deklaratif Compose.
2. Argument passing menggunakan navArgument(key) { type = NavType.StringType }.
3. State hoisting: pisahkan event dan state untuk komponen reusable.
4. ViewModel mempertahankan state saat konfigurasi layar berubah.

Catatan praktikum: Pastikan back stack dibersihkan saat kembali ke halaman utama."""

            "IF550" -> """Ringkasan Kuliah IF550: Rekayasa Perangkat Lunak
Topik: Pola Arsitektur MVC vs MVVM

1. Model: Menyimpan data & logika bisnis murni.
2. View: Hanya bertugas merender UI dan mendengarkan perubahan data.
3. ViewModel: Bertindak sebagai penghubung dan state-holder.
Tenggat tugas diagram arsitektur: Rabu, 7 Okt."""

            "IF510" -> """Ringkasan Kuliah IF510: Basis Data Lanjut
Topik: Indeks B-Tree & Optimasi Query

- B-Tree index mempercepat query pencarian range dan equality.
- Hindari indeks berlebihan pada kolom yang sering mengalami UPDATE/INSERT.
- Evaluasi query execution plan dengan EXPLAIN ANALYZE."""

            else -> """Catatan Perkuliahan Mahasiswa
Mata Kuliah: $selectedCourse
Hasil pindaian dokumen fisik:
• Seluruh catatan tertata rapi dan siap dipelajari kembali.
• Disimpan ke dalam catatan lokal StudentHub."""
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scan-line")
    val scanOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan-line-anim"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (!isCaptured) lString("Pindai Catatan Kuliah", "Scan Class Notes") else lString("Hasil Pindaian Catatan", "Scanned Note Result")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, lString("Kembali", "Back"))
                    }
                },
                actions = {
                    if (!isCaptured) {
                        IconButton(onClick = { flashOn = !flashOn }) {
                            Icon(
                                if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                "Flash",
                                tint = if (flashOn) Color(0xFFF1C40F) else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = { isFrontCamera = !isFrontCamera }) {
                            Icon(Icons.Default.FlipCameraAndroid, lString("Ganti Kamera", "Switch Camera"))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (!isCaptured) {
            // CAMERA VIEWFINDER VIEW
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF0F0F12)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Helper Banner
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.DocumentScanner, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(18.dp))
                        Text(
                            lString("Arahkan kamera ke buku catatan atau modul", "Point camera at notebook or handout"),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }

                // Viewfinder Frame with Reticles
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(380.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1E26))
                        .border(2.dp, Color(0xFF4ADE80).copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Simulated Notebook Paper Texture
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        repeat(10) { index ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (index % 3 == 0) 0.6f else 0.95f)
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )
                        }
                    }

                    // Scanning line animation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.TopCenter)
                            .offset(y = (scanOffset * 370).dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color(0xFF4ADE80),
                                        Color(0xFF22C55E),
                                        Color(0xFF4ADE80),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Target Corner Indicators
                    Text(
                        text = lString("Dokumen terdeteksi (A4 / Buku)", "Document detected (A4 / Notebook)"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4ADE80),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                // Bottom Controls: Shutter & Gallery
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, start = 32.dp, end = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gallery mockup
                    IconButton(
                        onClick = { isCaptured = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, lString("Galeri", "Gallery"), tint = Color.White)
                    }

                    // Main Shutter Button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { isCaptured = true }
                            .testTag("scan-shutter-btn")
                    )

                    // Flash feedback
                    IconButton(
                        onClick = { flashOn = !flashOn },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            "Flash",
                            tint = if (flashOn) Color(0xFFFBBF24) else Color.White
                        )
                    }
                }
            }
        } else {
            // CAPTURED PREVIEW & OCR EXTRACTION VIEW
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Success Badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                lString("Pindaian Berhasil", "Scan Successful"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                lString("Teks catatan fisik berhasil diekstraksi secara otomatis (OCR)", "Physical note text extracted automatically (OCR)"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Filter Enhancements
                Text(lString("Pilih Tampilan Dokumen:", "Select Document Appearance:"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "B&W Dokumen" to lString("B&W Dokumen", "B&W Document"),
                        "Asli (Warna)" to lString("Asli (Warna)", "Original (Color)"),
                        "Kontras Tinggi" to lString("Kontras Tinggi", "High Contrast")
                    ).forEach { (filterKey, filterLabel) ->
                        FilterChip(
                            selected = selectedFilter == filterKey,
                            onClick = { selectedFilter = filterKey },
                            label = { Text(filterLabel) }
                        )
                    }
                }

                // Target Mata Kuliah
                Text(lString("Pilih Mata Kuliah untuk Catatan Ini:", "Select Course for This Note:"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("IF570", "IF550", "IF510", "IF530").forEach { cId ->
                        FilterChip(
                            selected = selectedCourse == cId,
                            onClick = { selectedCourse = cId },
                            label = { Text(cId) }
                        )
                    }
                }

                // Judul Catatan
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text(lString("Judul Catatan", "Note Title")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // OCR Text Result Preview Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                lString("Teks Hasil OCR", "OCR Extracted Text"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                lString("Ekstraksi Otomatis", "Automatic Extraction"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                        Text(
                            text = extractedText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Action Buttons
                val fallbackScannedTitle = lString("Catatan Pindaian • $selectedCourse", "Scanned Note • $selectedCourse")
                Button(
                    onClick = {
                        onSaveToNote(
                            customTitle.ifBlank { fallbackScannedTitle },
                            selectedCourse,
                            extractedText
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save-scanned-note-btn")
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(lString("Simpan ke Catatan Kuliah", "Save to Class Notes"))
                }

                OutlinedButton(
                    onClick = { isCaptured = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(lString("Foto Ulang Catatan", "Retake Photo"))
                }
            }
        }
    }
}
