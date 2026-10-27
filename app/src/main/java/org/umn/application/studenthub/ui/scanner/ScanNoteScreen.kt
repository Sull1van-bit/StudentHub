package org.umn.application.studenthub.ui.scanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.ui.localization.lString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanNoteScreen(
    initialCourseId: String = "",
    onNavigateBack: () -> Unit,
    onSaveToNote: (title: String, course: String, content: String) -> Unit
) {
    val context = LocalContext.current
    val dbCourses = remember {
        runCatching { StudentHubDatabase.get(context).courses() }.getOrDefault(emptyList())
    }
    var isCaptured by rememberSaveable { mutableStateOf(false) }
    var flashOn by rememberSaveable { mutableStateOf(false) }
    var isFrontCamera by rememberSaveable { mutableStateOf(false) }
    var selectedFilter by rememberSaveable { mutableStateOf("B&W Dokumen") }
    var selectedCourse by rememberSaveable {
        mutableStateOf(
            initialCourseId.takeIf { it.isNotBlank() }
                ?: dbCourses.firstOrNull()?.code
                ?: ""
        )
    }
    var customTitle by rememberSaveable { mutableStateOf("") }
    var scannedBody by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }

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
                                tint = if (flashOn) MaterialTheme.colorScheme.primary else LocalContentColor.current
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF0F0F12)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(380.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1E26))
                        .border(2.dp, Color(0xFF4ADE80).copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
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

                    Text(
                        text = lString("Dokumen siap dipindai", "Document ready to scan"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4ADE80),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, start = 32.dp, end = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isCaptured = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, lString("Galeri", "Gallery"), tint = Color.White)
                    }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                                lString("Pindaian Siap Disimpan", "Scan Ready to Save"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                lString("Isi judul, mata kuliah, dan teks hasil pindaian sebelum menyimpan.", "Fill in the title, course, and scanned text before saving."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

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

                OutlinedTextField(
                    value = selectedCourse,
                    onValueChange = { selectedCourse = it; error = false },
                    label = { Text(lString("Kode Mata Kuliah / Kategori", "Course Code / Category")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (dbCourses.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        dbCourses.forEach { c ->
                            FilterChip(
                                selected = selectedCourse == c.code,
                                onClick = { selectedCourse = c.code; error = false },
                                label = { Text("${c.code} • ${c.title}") }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it; error = false },
                    label = { Text(lString("Judul Catatan", "Note Title")) },
                    isError = error && customTitle.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = scannedBody,
                    onValueChange = { scannedBody = it },
                    label = { Text(lString("Isi Catatan Hasil Pindaian", "Scanned Note Content")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    minLines = 5
                )

                if (error) {
                    Text(
                        lString("Judul catatan wajib diisi.", "Note title is required."),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        if (customTitle.isBlank()) {
                            error = true
                        } else {
                            val courseToSave = selectedCourse.trim().ifBlank { "Umum" }
                            onSaveToNote(
                                customTitle.trim(),
                                courseToSave,
                                scannedBody
                            )
                        }
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
