package org.umn.application.studenthub.ui.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.umn.application.studenthub.ui.localization.AppLanguage
import org.umn.application.studenthub.ui.localization.LocalAppLanguage
import org.umn.application.studenthub.ui.localization.lString
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun InteractiveStudyCanvas(
    canvasData: CanvasStudyData,
    onInsertToNote: (String) -> Unit,
    onAskInChat: (String) -> Unit,
    onCanvasScoreEarned: (Int) -> Unit
) {
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
    var activeMode by rememberSaveable { mutableIntStateOf(0) }

    if (canvasData.nodes.isEmpty()) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = lString("Kanvas belum memiliki materi", "No material for canvas yet"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = lString(
                        "Tulis minimal beberapa kalimat pada catatan agar peta konsep dan latihan pencocokan istilah bisa dibuat dari catatanmu.",
                        "Write at least a few sentences in your note to build a concept map and term matching exercise."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeMode == 0,
                onClick = { activeMode = 0 },
                label = { Text(lString("Peta Konsep", "Concept Map")) },
                leadingIcon = { Icon(Icons.Default.AccountTree, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = activeMode == 1,
                onClick = { activeMode = 1 },
                label = { Text(lString("Cocok Istilah", "Term Match")) },
                leadingIcon = { Icon(Icons.Default.Extension, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = activeMode == 2,
                onClick = { activeMode = 2 },
                label = { Text(lString("Catatan Kerja", "Scratchpad")) },
                leadingIcon = { Icon(Icons.Default.EditNote, null, Modifier.size(18.dp)) }
            )
        }

        when (activeMode) {
            0 -> MindMapCanvasView(
                canvasData = canvasData,
                isEn = isEn,
                onInsertToNote = onInsertToNote,
                onAskInChat = onAskInChat,
                onNodeExplored = { onCanvasScoreEarned(75) }
            )
            1 -> ConceptMatchChallengeView(
                pairs = canvasData.matchPairs,
                isEn = isEn,
                onScoreUpdated = onCanvasScoreEarned
            )
            2 -> CoWriterScratchpadView(
                canvasData = canvasData,
                onInsertToNote = onInsertToNote
            )
        }
    }
}

@Composable
private fun MindMapCanvasView(
    canvasData: CanvasStudyData,
    isEn: Boolean,
    onInsertToNote: (String) -> Unit,
    onAskInChat: (String) -> Unit,
    onNodeExplored: () -> Unit
) {
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedNodeId by rememberSaveable(canvasData.centralTopic) {
        mutableStateOf(canvasData.nodes.firstOrNull()?.id.orEmpty())
    }
    val selectedNode = canvasData.nodes.firstOrNull { it.id == selectedNodeId } ?: canvasData.nodes.firstOrNull()

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val outlineColor = MaterialTheme.colorScheme.outline
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                lString(
                    "Geser area kanvas atau ketuk kotak konsep untuk melihat kutipan catatan.",
                    "Drag the canvas or tap a concept card to view the note excerpt."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { panOffset = Offset.Zero },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(lString("Reset Posisi", "Reset View"))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, outlineVariantColor, RoundedCornerShape(12.dp))
                .pointerInput(canvasData.nodes) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        panOffset = Offset(
                            x = (panOffset.x + dragAmount.x).coerceIn(-240f, 240f),
                            y = (panOffset.y + dragAmount.y).coerceIn(-160f, 160f)
                        )
                    }
                }
                .pointerInput(canvasData.nodes, panOffset) {
                    detectTapGestures { tapOffset ->
                        val center = Offset(size.width / 2f + panOffset.x, size.height / 2f + panOffset.y)
                        val rx = size.width * 0.34f
                        val ry = size.height * 0.34f
                        val hitHalfW = 58.dp.toPx()
                        val hitHalfH = 28.dp.toPx()
                        val tapped = canvasData.nodes.firstOrNull { node ->
                            val rad = Math.toRadians(node.angleDeg.toDouble())
                            val scale = if (node.radiusFraction > 0.34f) 1.0f else 0.86f
                            val nodePos = Offset(
                                x = center.x + (cos(rad) * rx * scale).toFloat(),
                                y = center.y + (sin(rad) * ry * scale).toFloat()
                            )
                            abs(tapOffset.x - nodePos.x) <= hitHalfW && abs(tapOffset.y - nodePos.y) <= hitHalfH
                        }
                        if (tapped != null) {
                            selectedNodeId = tapped.id
                            onNodeExplored()
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridStep = 28.dp.toPx()
                val offsetX = (panOffset.x % gridStep + gridStep) % gridStep
                val offsetY = (panOffset.y % gridStep + gridStep) % gridStep
                var gx = offsetX
                while (gx < size.width) {
                    var gy = offsetY
                    while (gy < size.height) {
                        drawCircle(
                            color = outlineVariantColor.copy(alpha = 0.45f),
                            radius = 1.2.dp.toPx(),
                            center = Offset(gx, gy)
                        )
                        gy += gridStep
                    }
                    gx += gridStep
                }

                val center = Offset(size.width / 2f + panOffset.x, size.height / 2f + panOffset.y)
                val rx = size.width * 0.34f
                val ry = size.height * 0.34f

                canvasData.nodes.forEach { node ->
                    val rad = Math.toRadians(node.angleDeg.toDouble())
                    val scale = if (node.radiusFraction > 0.34f) 1.0f else 0.86f
                    val nodeCenter = Offset(
                        x = center.x + (cos(rad) * rx * scale).toFloat(),
                        y = center.y + (sin(rad) * ry * scale).toFloat()
                    )
                    val isSelected = node.id == selectedNode?.id
                    drawLine(
                        color = if (isSelected) primaryColor else outlineColor.copy(alpha = 0.5f),
                        start = center,
                        end = nodeCenter,
                        strokeWidth = if (isSelected) 2.5.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = if (isSelected) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )
                }

                val centerLayout = textMeasurer.measure(
                    text = canvasData.centralTopic,
                    style = TextStyle(
                        color = onPrimaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = 112.dp.toPx().roundToInt())
                )
                val centerW = (centerLayout.size.width + 24.dp.toPx()).coerceAtLeast(84.dp.toPx())
                val centerH = (centerLayout.size.height + 16.dp.toPx()).coerceAtLeast(38.dp.toPx())
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(center.x - centerW / 2f, center.y - centerH / 2f),
                    size = Size(centerW, centerH),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
                drawText(
                    textLayoutResult = centerLayout,
                    topLeft = Offset(
                        center.x - centerLayout.size.width / 2f,
                        center.y - centerLayout.size.height / 2f
                    )
                )

                canvasData.nodes.forEach { node ->
                    val rad = Math.toRadians(node.angleDeg.toDouble())
                    val scale = if (node.radiusFraction > 0.34f) 1.0f else 0.86f
                    val nodeCenter = Offset(
                        x = center.x + (cos(rad) * rx * scale).toFloat(),
                        y = center.y + (sin(rad) * ry * scale).toFloat()
                    )
                    val isSelected = node.id == selectedNode?.id

                    val labelLayout = textMeasurer.measure(
                        text = node.label,
                        style = TextStyle(
                            color = if (isSelected) onPrimaryContainer else onSurfaceColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        constraints = Constraints(maxWidth = 98.dp.toPx().roundToInt())
                    )
                    val boxW = (labelLayout.size.width + 20.dp.toPx()).coerceAtLeast(68.dp.toPx())
                    val boxH = (labelLayout.size.height + 14.dp.toPx()).coerceAtLeast(34.dp.toPx())
                    val topLeft = Offset(nodeCenter.x - boxW / 2f, nodeCenter.y - boxH / 2f)
                    val corner = CornerRadius(10.dp.toPx(), 10.dp.toPx())

                    drawRoundRect(
                        color = if (isSelected) primaryContainer else surfaceContainerHigh,
                        topLeft = topLeft,
                        size = Size(boxW, boxH),
                        cornerRadius = corner
                    )
                    drawRoundRect(
                        color = if (isSelected) primaryColor else outlineColor,
                        topLeft = topLeft,
                        size = Size(boxW, boxH),
                        cornerRadius = corner,
                        style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.dp.toPx())
                    )
                    drawText(
                        textLayoutResult = labelLayout,
                        topLeft = Offset(
                            nodeCenter.x - labelLayout.size.width / 2f,
                            nodeCenter.y - labelLayout.size.height / 2f
                        )
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            canvasData.nodes.forEachIndexed { idx, node ->
                FilterChip(
                    selected = node.id == selectedNode?.id,
                    onClick = {
                        selectedNodeId = node.id
                        onNodeExplored()
                    },
                    label = { Text("${idx + 1}. ${node.label}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        selectedNode?.let { node ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = node.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = node.detail,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val q = if (isEn) "Explain more about \"${node.label}\" based on this note."
                                else "Jelaskan lebih lanjut bagian \"${node.label}\" di catatan ini."
                                onAskInChat(q)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Forum, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(lString("Tanya di Diskusi", "Ask in Chat"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { onInsertToNote("\n• ${node.label}: ${node.detail}") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(lString("Tambah ke Catatan", "Add to Note"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConceptMatchChallengeView(
    pairs: List<ConceptMatchPair>,
    isEn: Boolean,
    onScoreUpdated: (Int) -> Unit
) {
    if (pairs.isEmpty()) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = lString(
                    "Belum ada istilah yang bisa dicocokkan. Tambahkan kalimat atau definisi pada catatanmu.",
                    "No terms to match yet. Add sentences or definitions to your note."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    var roundKey by rememberSaveable { mutableIntStateOf(0) }
    var selectedTermId by rememberSaveable(roundKey) { mutableStateOf<String?>(null) }
    var matchedIds by rememberSaveable(roundKey) { mutableStateOf(listOf<String>()) }
    var attempts by rememberSaveable(roundKey) { mutableIntStateOf(0) }
    var feedbackMessage by rememberSaveable(roundKey) { mutableStateOf<String?>(null) }

    val shuffledDefinitions = remember(pairs, roundKey) {
        pairs.shuffled()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
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
                        lString("Cocokkan Istilah & Penjelasan", "Match Terms & Definitions"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        lString(
                            "Terjawab: ${matchedIds.size}/${pairs.size} • Percobaan: $attempts",
                            "Matched: ${matchedIds.size}/${pairs.size} • Attempts: $attempts"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { roundKey++ }) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(lString("Acak", "Shuffle"))
                }
            }
        }

        feedbackMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (matchedIds.size == pairs.size) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        lString(
                            "Semua pasangan istilah sudah terjawab dengan benar.",
                            "All term pairs matched."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Text(
            lString("1. Pilih istilah:", "1. Select a term:"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pairs.forEach { pair ->
                val isMatched = pair.id in matchedIds
                val isSelected = selectedTermId == pair.id
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = !isMatched) {
                            selectedTermId = pair.id
                            feedbackMessage = null
                        },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = when {
                            isMatched -> MaterialTheme.colorScheme.surfaceContainerHighest
                            isSelected -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isMatched -> Icons.Default.CheckCircle
                                isSelected -> Icons.Default.RadioButtonChecked
                                else -> Icons.Default.RadioButtonUnchecked
                            },
                            contentDescription = when {
                                isMatched -> lString("Sudah cocok", "Matched")
                                isSelected -> lString("Dipilih", "Selected")
                                else -> lString("Belum dipilih", "Not selected")
                            },
                            tint = if (isMatched || isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = pair.term,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Text(
            lString("2. Pilih penjelasan yang sesuai:", "2. Tap the matching definition:"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shuffledDefinitions.forEach { pair ->
                val isMatched = pair.id in matchedIds
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = !isMatched && selectedTermId != null) {
                            val currentTerm = selectedTermId ?: return@clickable
                            attempts++
                            if (currentTerm == pair.id) {
                                val nextMatched = matchedIds + pair.id
                                matchedIds = nextMatched
                                selectedTermId = null
                                feedbackMessage = if (isEn) "Correct match." else "Pasangan sesuai."
                                val ratio = (nextMatched.size.toFloat() / pairs.size.coerceAtLeast(1) * 100).roundToInt()
                                onScoreUpdated(ratio)
                            } else {
                                feedbackMessage = if (isEn) {
                                    "Not a match. Try another definition."
                                } else {
                                    "Belum sesuai. Coba penjelasan lainnya."
                                }
                            }
                        },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (isMatched) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isMatched) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = lString("Sudah cocok", "Matched"),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = pair.definition,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isMatched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoWriterScratchpadView(
    canvasData: CanvasStudyData,
    onInsertToNote: (String) -> Unit
) {
    var selectedPreset by rememberSaveable { mutableIntStateOf(0) }
    var scratchText by rememberSaveable(canvasData.centralTopic, selectedPreset) {
        mutableStateOf(
            when (selectedPreset) {
                0 -> canvasData.eli5Text
                1 -> canvasData.mnemonicText
                else -> canvasData.caseStudyText
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            lString(
                "Pilih draf kutipan di bawah, sunting sesuai bahasamu sendiri, lalu tambahkan ke catatan.",
                "Pick a draft below, edit it in your own words, then add it to your note."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedPreset == 0,
                onClick = {
                    selectedPreset = 0
                    scratchText = canvasData.eli5Text
                },
                label = { Text(lString("Inti Materi", "Core Takeaway")) }
            )
            FilterChip(
                selected = selectedPreset == 1,
                onClick = {
                    selectedPreset = 1
                    scratchText = canvasData.mnemonicText
                },
                label = { Text(lString("Singkatan", "Initials")) }
            )
            FilterChip(
                selected = selectedPreset == 2,
                onClick = {
                    selectedPreset = 2
                    scratchText = canvasData.caseStudyText
                },
                label = { Text(lString("Poin Lanjutan", "Further Points")) }
            )
        }

        OutlinedTextField(
            value = scratchText,
            onValueChange = { scratchText = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp),
            label = { Text(lString("Catatan Kerja", "Scratchpad")) }
        )

        Button(
            onClick = {
                if (scratchText.isNotBlank()) {
                    onInsertToNote("\n\n" + scratchText.trim())
                }
            },
            enabled = scratchText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(lString("Tambahkan ke Catatan", "Add to Note"))
        }
    }
}
