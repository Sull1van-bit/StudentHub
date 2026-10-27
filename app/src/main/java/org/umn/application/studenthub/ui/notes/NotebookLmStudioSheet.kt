package org.umn.application.studenthub.ui.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.umn.application.studenthub.data.local.AiChatMessageRow
import org.umn.application.studenthub.data.local.AiChatSessionRow
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.ui.localization.AppLanguage
import org.umn.application.studenthub.ui.localization.LocalAppLanguage
import org.umn.application.studenthub.ui.localization.lString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLmStudioSheet(
    noteId: String,
    noteTitle: String,
    noteCourse: String,
    noteBody: String,
    initialTab: Int = 0,
    initialSessionId: String? = null,
    onInsertToNote: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { StudentHubDatabase.get(context) }
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
    val effectiveNoteKey = noteId.ifBlank { "draft-${noteCourse.ifBlank { "umum" }}" }

    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 4)) }
    var pendingChatPrompt by rememberSaveable { mutableStateOf<String?>(null) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    var summary by remember(noteTitle, noteBody, isEn) {
        mutableStateOf(NoteAiAssistant.generateSummary(noteTitle, noteBody, isEn))
    }
    LaunchedEffect(noteTitle, noteBody, isEn) {
        if (noteBody.isNotBlank()) {
            summary = NoteAiAssistant.enrichSummaryRemote(noteTitle, noteBody, isEn, summary)
        }
    }
    val studyGuide = remember(noteTitle, noteBody, isEn) {
        NoteAiAssistant.generateStudyGuide(noteTitle, noteBody, isEn)
    }
    val quiz = remember(noteTitle, noteBody, isEn) {
        NoteAiAssistant.generateQuiz(noteTitle, noteBody, isEn)
    }
    val canvasData = remember(noteTitle, noteBody, isEn) {
        NoteAiAssistant.generateCanvasData(noteTitle, noteBody, isEn)
    }

    var savedProgress by remember(effectiveNoteKey) {
        mutableStateOf(runCatching { database.aiStudyProgress(effectiveNoteKey) }.getOrNull())
    }
    var progressVersion by remember { mutableIntStateOf(0) }

    fun updateStudyProgress(masteredCount: Int? = null, totalCount: Int? = null, canvasScore: Int? = null) {
        val current = savedProgress
        val nextMastered = masteredCount ?: current?.masteredCount ?: 0
        val nextTotal = totalCount ?: current?.totalCount ?: quiz.questions.size
        val nextCanvas = canvasScore ?: current?.canvasScore ?: 0
        runCatching {
            database.saveAiStudyProgress(effectiveNoteKey, nextMastered, nextTotal, nextCanvas)
            savedProgress = database.aiStudyProgress(effectiveNoteKey)
            progressVersion++
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lString("Studio Belajar Catatan", "Note Study Studio"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = (noteCourse.ifBlank { lString("Umum", "General") }) + " • " +
                            (noteTitle.ifBlank { lString("Catatan Tanpa Judul", "Untitled Note") }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = lString("Tutup", "Close"))
                }
            }

            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(lString("Rangkuman", "Summary")) },
                    icon = { Icon(Icons.Default.Summarize, null, Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(lString("Kanvas", "Canvas")) },
                    icon = { Icon(Icons.Default.AccountTree, null, Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(lString("Kuis & Panduan", "Quiz & Guide")) },
                    icon = { Icon(Icons.Default.Quiz, null, Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text(lString("Tanya Catatan", "Ask Note")) },
                    icon = { Icon(Icons.Default.Forum, null, Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text(lString("Asesmen & Jadwal", "Progress & Plan")) },
                    icon = { Icon(Icons.Default.Assessment, null, Modifier.size(18.dp)) }
                )
            }

            feedbackBanner?.let { banner ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = banner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { feedbackBanner = null }) {
                            Text(lString("Tutup", "Dismiss"))
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> NoteSummaryTabContent(
                        summary = summary,
                        isEn = isEn,
                        onInsertToNote = onInsertToNote?.let { insert ->
                            {
                                insert(NoteAiAssistant.formatSummaryForNote(summary, isEn))
                                feedbackBanner = if (isEn) "Summary appended to note." else "Rangkuman ditambahkan ke isi catatan."
                            }
                        },
                        onCopy = {
                            copyToClipboard(context, NoteAiAssistant.formatSummaryForNote(summary, isEn))
                            feedbackBanner = if (isEn) "Summary copied to clipboard." else "Rangkuman disalin ke papan klip."
                        }
                    )
                    1 -> InteractiveStudyCanvas(
                        canvasData = canvasData,
                        onInsertToNote = { snippet ->
                            if (onInsertToNote != null) {
                                onInsertToNote(snippet)
                                feedbackBanner = if (isEn) "Added to note body." else "Berhasil disisipkan ke catatan."
                            } else {
                                copyToClipboard(context, snippet)
                                feedbackBanner = if (isEn) "Copied to clipboard." else "Disalin ke papan klip."
                            }
                        },
                        onAskInChat = { prompt ->
                            pendingChatPrompt = prompt
                            selectedTab = 3
                        },
                        onCanvasScoreEarned = { score ->
                            updateStudyProgress(canvasScore = maxOf(savedProgress?.canvasScore ?: 0, score))
                        }
                    )
                    2 -> StudyGuideAndQuizTabContent(
                        studyGuide = studyGuide,
                        quiz = quiz,
                        initialMasteredCount = savedProgress?.masteredCount ?: 0,
                        isEn = isEn,
                        onMasteryChanged = { mastered, total ->
                            updateStudyProgress(masteredCount = mastered, totalCount = total)
                        },
                        onInsertGuide = onInsertToNote?.let { insert ->
                            {
                                insert(NoteAiAssistant.formatStudyGuideForNote(studyGuide, isEn))
                                feedbackBanner = if (isEn) "Study guide appended to note." else "Panduan belajar ditambahkan ke isi catatan."
                            }
                        }
                    )
                    3 -> PersistentNoteChatTabContent(
                        database = database,
                        noteId = effectiveNoteKey,
                        noteTitle = noteTitle,
                        noteCourse = noteCourse,
                        noteBody = noteBody,
                        initialSessionId = initialSessionId,
                        pendingPrompt = pendingChatPrompt,
                        onPendingPromptConsumed = { pendingChatPrompt = null },
                        isEn = isEn
                    )
                    4 -> StudentProgressAndScheduleSection(
                        database = database,
                        focusCourseCode = noteCourse,
                        focusNoteTitle = noteTitle,
                        refreshTrigger = progressVersion,
                        onFeedback = { feedbackBanner = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteSummaryTabContent(
    summary: NoteSummary,
    isEn: Boolean,
    onInsertToNote: (() -> Unit)?,
    onCopy: () -> Unit
) {
    if (summary.overview.isBlank() && summary.keyPoints.isEmpty()) {
        OutlinedCard(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = lString("Catatan masih kosong", "Note is still empty"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = lString(
                        "Tulis atau pindai materi kuliah pada catatan terlebih dahulu untuk membuat ringkasan dan daftar istilah.",
                        "Write or scan lecture material in the note first to generate a summary and key terms."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                lString("Ringkasan Materi", "Material Overview"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = summary.overview,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (summary.keyPoints.isNotEmpty()) {
        OutlinedCard(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    lString("Poin Kunci", "Key Points"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                summary.keyPoints.forEachIndexed { idx, pt ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${idx + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = pt,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (summary.glossaryTerms.isNotEmpty()) {
        OutlinedCard(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    lString("Istilah Penting", "Key Terms"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                summary.glossaryTerms.forEachIndexed { idx, (term, def) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = term,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = def,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (idx < summary.glossaryTerms.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onInsertToNote != null) {
            Button(
                onClick = onInsertToNote,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PostAdd, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(lString("Tambah ke Catatan", "Add to Note"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        OutlinedButton(
            onClick = onCopy,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(lString("Salin Rangkuman", "Copy Summary"), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StudyGuideAndQuizTabContent(
    studyGuide: StudyGuide,
    quiz: StudyQuiz,
    initialMasteredCount: Int,
    isEn: Boolean,
    onMasteryChanged: (Int, Int) -> Unit,
    onInsertGuide: (() -> Unit)?
) {
    val questions = quiz.questions
    if (questions.isEmpty() && studyGuide.sections.isEmpty()) {
        OutlinedCard(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = lString("Belum ada kartu latihan", "No flashcards yet"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = lString(
                        "Kartu latihan dan panduan belajar akan disusun dari kalimat serta definisi yang kamu tulis di catatan.",
                        "Flashcards and study guides are built from the sentences and definitions you write in your note."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var currentCardIndex by rememberSaveable { mutableIntStateOf(0) }
    var answerRevealed by rememberSaveable { mutableStateOf(false) }
    var masteredSet by rememberSaveable {
        mutableStateOf(
            questions.take(initialMasteredCount.coerceAtMost(questions.size)).map { it.id }
        )
    }

    val currentQuestion = questions.getOrNull(currentCardIndex.coerceIn(0, (questions.size - 1).coerceAtLeast(0)))

    if (currentQuestion != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Quiz, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(
                            lString("Kartu Latihan", "Flashcards"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "${(currentCardIndex + 1).coerceAtMost(questions.size)} / ${questions.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LinearProgressIndicator(
                    progress = {
                        if (questions.isNotEmpty()) masteredSet.size.toFloat() / questions.size else 0f
                    },
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
                Text(
                    lString(
                        "Dikuasai: ${masteredSet.size} dari ${questions.size} kartu",
                        "Mastered: ${masteredSet.size} of ${questions.size} cards"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = lString("Pertanyaan", "Question"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = currentQuestion.question,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (answerRevealed) {
                            HorizontalDivider()
                            Text(
                                text = lString("Jawaban dari catatan", "Answer from note"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = currentQuestion.answer,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = currentQuestion.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isMastered = currentQuestion.id in masteredSet
                                Button(
                                    onClick = {
                                        val nextSet = if (isMastered) masteredSet else (masteredSet + currentQuestion.id)
                                        masteredSet = nextSet
                                        onMasteryChanged(nextSet.size, questions.size)
                                        answerRevealed = false
                                        if (currentCardIndex < questions.lastIndex) currentCardIndex++
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(lString("Sudah Paham", "Got It"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val nextSet = masteredSet - currentQuestion.id
                                        masteredSet = nextSet
                                        onMasteryChanged(nextSet.size, questions.size)
                                        answerRevealed = false
                                        if (currentCardIndex < questions.lastIndex) currentCardIndex++ else currentCardIndex = 0
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(lString("Ulangi Lagi", "Review Again"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { answerRevealed = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Visibility, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(lString("Lihat Jawaban", "Show Answer"))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            if (currentCardIndex > 0) {
                                currentCardIndex--
                                answerRevealed = false
                            }
                        },
                        enabled = currentCardIndex > 0,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(lString("Sebelumnya", "Previous"))
                    }
                    TextButton(
                        onClick = {
                            if (currentCardIndex < questions.lastIndex) {
                                currentCardIndex++
                                answerRevealed = false
                            }
                        },
                        enabled = currentCardIndex < questions.lastIndex,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(lString("Berikutnya", "Next"))
                    }
                }
            }
        }
    }

    if (studyGuide.sections.isNotEmpty()) {
        OutlinedCard(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        lString("Panduan Belajar", "Study Guide"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = studyGuide.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                studyGuide.sections.forEach { section ->
                    Text(
                        text = section.heading,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    section.details.forEach { detail ->
                        Text("• $detail", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (studyGuide.examFocusPoints.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        lString("Poin Latihan Ujian", "Exam Practice Points"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    studyGuide.examFocusPoints.forEach { pt ->
                        Text("• $pt", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (onInsertGuide != null) {
                    OutlinedButton(
                        onClick = onInsertGuide,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PostAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(lString("Tambah Panduan ke Catatan", "Add Guide to Note"))
                    }
                }
            }
        }
    }
}

@Composable
private fun PersistentNoteChatTabContent(
    database: StudentHubDatabase,
    noteId: String,
    noteTitle: String,
    noteCourse: String,
    noteBody: String,
    initialSessionId: String?,
    pendingPrompt: String?,
    onPendingPromptConsumed: () -> Unit,
    isEn: Boolean
) {
    val scope = rememberCoroutineScope()
    var sessions by remember(noteId) {
        mutableStateOf(runCatching { database.aiChatSessions(noteId) }.getOrDefault(emptyList()))
    }
    var activeSessionId by rememberSaveable(noteId, initialSessionId) {
        mutableStateOf(initialSessionId ?: sessions.firstOrNull()?.id.orEmpty())
    }

    LaunchedEffect(noteId, sessions.size) {
        if (activeSessionId.isNotBlank() && sessions.none { it.id == activeSessionId }) {
            activeSessionId = sessions.firstOrNull()?.id.orEmpty()
        } else if (activeSessionId.isBlank() && sessions.isNotEmpty()) {
            activeSessionId = sessions.first().id
        }
    }

    var messages by remember(activeSessionId) {
        mutableStateOf(
            if (activeSessionId.isNotBlank()) {
                runCatching { database.aiChatMessages(activeSessionId) }.getOrDefault(emptyList())
            } else emptyList()
        )
    }
    var inputQuery by rememberSaveable { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    fun sendQuery(q: String) {
        val trimmed = q.trim()
        if (trimmed.isBlank() || isThinking) return
        inputQuery = ""
        isThinking = true
        scope.launch {
            runCatching {
                val targetSessionId = if (activeSessionId.isNotBlank() && sessions.any { it.id == activeSessionId }) {
                    activeSessionId
                } else {
                    val defaultTitle = if (noteTitle.isNotBlank()) {
                        noteTitle.take(40)
                    } else {
                        trimmed.take(36)
                    }
                    val created = database.createAiChatSession(noteId, noteCourse, defaultTitle)
                    sessions = database.aiChatSessions(noteId)
                    activeSessionId = created.id
                    created.id
                }
                database.addAiChatMessage(targetSessionId, "USER", trimmed)
                messages = database.aiChatMessages(targetSessionId)
                val (replyText, quote) = NoteAiAssistant.answerQuestion(
                    query = trimmed,
                    noteTitle = noteTitle,
                    noteBody = noteBody,
                    history = messages,
                    isEn = isEn
                )
                database.addAiChatMessage(targetSessionId, "AI", replyText, quote)
                messages = database.aiChatMessages(targetSessionId)
                sessions = database.aiChatSessions(noteId)
            }
            isThinking = false
        }
    }

    LaunchedEffect(pendingPrompt) {
        if (!pendingPrompt.isNullOrBlank()) {
            val promptToRun = pendingPrompt
            onPendingPromptConsumed()
            sendQuery(promptToRun)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (sessions.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    lString("Riwayat Diskusi", "Discussion History"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            activeSessionId = ""
                            messages = emptyList()
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(lString("Topik Baru", "New Topic"))
                    }
                    if (activeSessionId.isNotBlank()) {
                        IconButton(
                            onClick = {
                                runCatching {
                                    database.deleteAiChatSession(activeSessionId)
                                    sessions = database.aiChatSessions(noteId)
                                    activeSessionId = sessions.firstOrNull()?.id.orEmpty()
                                    messages = if (activeSessionId.isNotBlank()) {
                                        database.aiChatMessages(activeSessionId)
                                    } else emptyList()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = lString("Hapus Sesi", "Delete Session"),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sessions.forEach { session ->
                    val timeLabel = SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("id-ID")).format(Date(session.updatedAt))
                    FilterChip(
                        selected = session.id == activeSessionId,
                        onClick = {
                            activeSessionId = session.id
                            messages = runCatching { database.aiChatMessages(session.id) }.getOrDefault(emptyList())
                        },
                        label = {
                            Text(
                                "${session.title} ($timeLabel)",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }

        if (noteBody.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NoteAiAssistant.getSuggestedPrompts(isEn).forEach { prompt ->
                    SuggestionChip(
                        onClick = { sendQuery(prompt) },
                        label = { Text(prompt) }
                    )
                }
            }
        }

        if (messages.isEmpty() && !isThinking) {
            OutlinedCard(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (noteBody.isBlank()) {
                            lString("Catatan belum memiliki isi", "Note has no content yet")
                        } else {
                            lString("Tanyakan isi catatan ini", "Ask about this note")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (noteBody.isBlank()) {
                            lString(
                                "Tulis materi kuliah pada catatan terlebih dahulu agar pertanyaanmu bisa dijawab berdasarkan isi catatan.",
                                "Write lecture material in the note first so your questions can be answered from its content."
                            )
                        } else {
                            lString(
                                "Pilih salah satu pertanyaan di atas atau ketik pertanyaanmu di bawah. Percakapan baru akan disimpan setelah kamu mengirim pesan.",
                                "Pick a question above or type your own below. A session is saved once you send a message."
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                messages.forEach { msg ->
                    val isUser = msg.sender == "USER"
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                    ) {
                        Surface(
                            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isUser) lString("Kamu", "You") else lString("Jawaban", "Answer"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (!isUser && msg.quoteSnippet.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = lString("Kutipan catatan: ", "From note: ") + "\"${msg.quoteSnippet.take(120)}\"",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isThinking) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            lString("Memeriksa isi catatan...", "Checking note content..."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(lString("Tanyakan isi catatan ini...", "Ask about this note...")) },
                singleLine = true
            )
            FilledIconButton(
                onClick = { sendQuery(inputQuery) },
                enabled = inputQuery.isNotBlank() && !isThinking
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = lString("Kirim", "Send"))
            }
        }
    }
}

@Composable
fun StudentProgressAndScheduleSection(
    database: StudentHubDatabase,
    focusCourseCode: String = "",
    focusNoteTitle: String = "",
    refreshTrigger: Int = 0,
    onFeedback: (String) -> Unit = {}
) {
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
    var localVersion by remember { mutableIntStateOf(0) }
    var addedSlotIds by rememberSaveable { mutableStateOf(listOf<String>()) }

    val assessment = remember(refreshTrigger, localVersion, isEn) {
        NoteAiAssistant.assessStudentProgress(database, isEn)
    }
    val recommendedSlots = remember(focusCourseCode, focusNoteTitle, refreshTrigger, localVersion, isEn) {
        NoteAiAssistant.recommendStudySchedule(database, focusCourseCode, focusNoteTitle, isEn)
    }

    if (!assessment.hasData) {
        OutlinedCard(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = lString("Belum ada data belajar", "No study data yet"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = assessment.summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        lString("Ringkasan Perkembangan Belajar", "Study Progress Overview"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = assessment.statusLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${assessment.readinessScore}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { assessment.readinessScore / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )

            Text(
                text = assessment.summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniCard(
                    label = lString("Catatan", "Notes"),
                    value = "${assessment.noteCoveragePercent}%",
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    label = lString("Latihan", "Practice"),
                    value = "${assessment.quizMasteryPercent}%",
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    label = lString("Tugas", "Tasks"),
                    value = "${assessment.taskCompletionPercent}%",
                    modifier = Modifier.weight(1f)
                )
            }

            if (assessment.strengths.isNotEmpty()) {
                Text(
                    lString("Sudah berjalan baik:", "Going well:"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                assessment.strengths.forEach { s ->
                    Text("• $s", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (assessment.focusAreas.isNotEmpty()) {
                Text(
                    lString("Perlu ditindaklanjuti:", "Needs attention:"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                assessment.focusAreas.forEach { f ->
                    Text("• $f", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (recommendedSlots.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column {
                    Text(
                        lString("Saran Waktu Belajar", "Suggested Study Times"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        lString(
                            "Disesuaikan dengan jam kosong di jadwalmu, tugas belum selesai, dan SKS mata kuliah.",
                            "Matched to free hours in your schedule, unfinished tasks, and course credits."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            recommendedSlots.forEach { slot ->
                val alreadyAdded = slot.id in addedSlotIds
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${slot.dayLabel} • ${slot.timeRangeLabel}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = slot.courseCode,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = slot.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = slot.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    runCatching {
                                        database.addAgenda(
                                            day = slot.dayIndex,
                                            time = slot.startTime,
                                            title = slot.title,
                                            kind = "BELAJAR",
                                            detail = "${slot.timeRangeLabel} • ${slot.reason}",
                                            courseId = slot.courseCode
                                        )
                                        addedSlotIds = addedSlotIds + slot.id
                                        localVersion++
                                        onFeedback(
                                            if (isEn) "Added \"${slot.title}\" to ${slot.dayLabel} schedule."
                                            else "\"${slot.title}\" ditambahkan ke jadwal hari ${slot.dayLabel}."
                                        )
                                    }
                                },
                                enabled = !alreadyAdded,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (alreadyAdded) Icons.Default.CheckCircle else Icons.Default.EventAvailable,
                                    null,
                                    Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (alreadyAdded) lString("Terjadwal", "Scheduled")
                                    else lString("Ke Jadwal", "To Schedule"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        database.addStudyPlan(
                                            courseId = slot.courseCode,
                                            title = slot.title,
                                            detail = "${slot.dayLabel}, ${slot.timeRangeLabel} • ${slot.reason}"
                                        )
                                        localVersion++
                                        onFeedback(
                                            if (isEn) "Added to study plan."
                                            else "Ditambahkan ke rencana belajar."
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    lString("Ke Rencana", "To Study Plan"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricMiniCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatHistorySheet(
    database: StudentHubDatabase,
    onOpenSession: (AiChatSessionRow) -> Unit,
    onDismiss: () -> Unit
) {
    var sessions by remember {
        mutableStateOf(runCatching { database.aiChatSessions() }.getOrDefault(emptyList()))
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        lString("Riwayat Sesi Diskusi", "Saved Discussion Sessions"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        lString(
                            "Pilih sesi percakapan sebelumnya untuk melanjutkan belajar.",
                            "Select a previous conversation session to continue studying."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = lString("Tutup", "Close"))
                }
            }

            if (sessions.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Forum, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            lString("Belum ada riwayat sesi diskusi.", "No discussion sessions yet."),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            lString(
                                "Buka catatan kuliah lalu pilih Studio Belajar untuk mulai berdiskusi.",
                                "Open a class note and select Study Studio to start a discussion."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sessions.forEach { session ->
                        val dateLabel = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID")).format(Date(session.updatedAt))
                        val msgCount = remember(session.id) {
                            runCatching { database.aiChatMessages(session.id).size }.getOrDefault(0)
                        }
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSession(session) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = (session.courseId.ifBlank { lString("Umum", "General") }) +
                                            " • $msgCount " + lString("pesan", "messages") + " • $dateLabel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        runCatching {
                                            database.deleteAiChatSession(session.id)
                                            sessions = database.aiChatSessions()
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = lString("Hapus", "Delete"),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("StudentHub Note Summary", text))
}
