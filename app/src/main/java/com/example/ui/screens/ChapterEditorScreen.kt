package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DocumentImporter
import com.example.model.ChapterItem
import com.example.model.ProcessingState
import com.example.viewmodel.PdfSplitViewModel
import kotlinx.coroutines.launch

@Composable
fun ChapterEditorScreen(
    viewModel: PdfSplitViewModel,
    onNavigateToPreview: () -> Unit,
    onNavigateToExport: () -> Unit,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val doc by viewModel.currentDoc.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val processingState by viewModel.processingState.collectAsState()
    val splitResult by viewModel.splitResult.collectAsState()

    var showIndexTextDialog by remember { mutableStateOf(false) }
    var editingChapter by remember { mutableStateOf<ChapterItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var viewingSummaryChapter by remember { mutableStateOf<ChapterItem?>(null) }

    // Photo picker for index
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val bitmap = DocumentImporter.loadBitmapFromUri(context, uri)
                if (bitmap != null) {
                    viewModel.analyzePhotoIndex(bitmap)
                }
            }
        }
    }

    if (showIndexTextDialog && doc != null) {
        IndexTextDialog(
            docTitle = doc!!.title,
            totalPages = doc!!.pageCount,
            onDismiss = { showIndexTextDialog = false },
            onSubmit = { text ->
                showIndexTextDialog = false
                viewModel.analyzeIndexWithHighThinking(text)
            }
        )
    }

    if (showAddDialog && doc != null) {
        EditChapterDialog(
            initialChapter = null,
            maxPages = doc!!.pageCount,
            onDismiss = { showAddDialog = false },
            onSave = { title, start, end ->
                showAddDialog = false
                viewModel.addChapter(title, start, end)
            }
        )
    }

    if (editingChapter != null && doc != null) {
        EditChapterDialog(
            initialChapter = editingChapter,
            maxPages = doc!!.pageCount,
            onDismiss = { editingChapter = null },
            onSave = { title, start, end ->
                viewModel.updateChapter(editingChapter!!.id, title, start, end)
                editingChapter = null
            }
        )
    }

    if (viewingSummaryChapter != null) {
        ChapterSummaryDialog(
            chapter = viewingSummaryChapter!!,
            onDismiss = { viewingSummaryChapter = null }
        )
    }

    // Auto-navigate to export when splitting succeeds
    LaunchedEffect(splitResult) {
        if (splitResult != null) {
            onNavigateToExport()
        }
    }

    if (doc == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("No document loaded", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onBackToHome) {
                    Text("Select a Document")
                }
            }
        }
        return
    }

    Scaffold(
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToPreview,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preview_chapters_nav_button")
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Preview Pages")
                    }

                    Button(
                        onClick = { viewModel.splitAndExportZip() },
                        enabled = chapters.isNotEmpty() && processingState !is ProcessingState.Loading,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("split_and_export_button")
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Split & Zip (${chapters.size})")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            // Header: Document info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    doc!!.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = onBackToHome) {
                                Icon(Icons.Default.Close, contentDescription = "Change Document")
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                label = { Text("${doc!!.pageCount} Pages") },
                                leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("${doc!!.fileSizeBytes / 1024} KB") },
                                leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text(doc!!.source.displayName) },
                                leadingIcon = { Icon(Icons.Default.Source, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
            }

            // Visual Page Coverage Timeline Bar
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
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
                                "Document Page Coverage",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${chapters.size} Chapters Mapped",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        PageCoverageBar(
                            totalPages = doc!!.pageCount,
                            chapters = chapters
                        )
                    }
                }
            }

            // Gemini Intelligence Action Toolbar
            item {
                Text(
                    "Chapter Intelligence & Segregation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        ActionChip(
                            title = "🧠 High Thinking",
                            subtitle = "Gemini 3.1 Pro",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { showIndexTextDialog = true },
                            tag = "high_thinking_action_chip"
                        )
                    }
                    item {
                        ActionChip(
                            title = "⚡ Fast Auto",
                            subtitle = "Gemini 3.1 Flash Lite",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = { viewModel.quickAutoSegregate() },
                            tag = "fast_auto_action_chip"
                        )
                    }
                    item {
                        ActionChip(
                            title = "📷 Scan Index Photo",
                            subtitle = "Multimodal Vision",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            onClick = { photoPicker.launch("image/*") },
                            tag = "photo_scan_action_chip"
                        )
                    }
                    item {
                        ActionChip(
                            title = "➕ Custom Chapter",
                            subtitle = "Manual Range",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { showAddDialog = true },
                            tag = "add_chapter_action_chip"
                        )
                    }
                }
            }

            // Processing state banner
            item {
                when (val state = processingState) {
                    is ProcessingState.Loading -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Text(
                                    state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    is ProcessingState.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    state.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }

            // Chapters List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Segregated Chapters (${chapters.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            // Chapter Cards
            items(chapters, key = { it.id }) { chapter ->
                ChapterCard(
                    chapter = chapter,
                    totalPages = doc!!.pageCount,
                    onEdit = { editingChapter = chapter },
                    onDelete = { viewModel.removeChapter(chapter.id) },
                    onPreview = {
                        viewModel.selectChapter(chapter.id)
                        onNavigateToPreview()
                    },
                    onSummarize = {
                        viewModel.summarizeChapter(chapter.id)
                        viewingSummaryChapter = chapter
                    },
                    onViewSummary = {
                        viewingSummaryChapter = chapter
                    }
                )
            }
        }
    }
}

@Composable
fun ActionChip(
    title: String,
    subtitle: String,
    containerColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ChapterCard(
    chapter: ChapterItem,
    totalPages: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit,
    onSummarize: () -> Unit,
    onViewSummary: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chapter_card_${chapter.index}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${chapter.index}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        chapter.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Page range chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "Pages ${chapter.startPage} - ${chapter.endPage}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    "${chapter.pageCount} pages",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        chapter.detectedBy,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp
                    )
                }
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Preview Pages", fontSize = 12.sp)
                }

                if (chapter.summary == null) {
                    Button(
                        onClick = onSummarize,
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("AI Summary", fontSize = 12.sp)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onViewSummary,
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("View Summary", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PageCoverageBar(
    totalPages: Int,
    chapters: List<ChapterItem>
) {
    val colors = listOf(
        Color(0xFF4F46E5),
        Color(0xFF7C3AED),
        Color(0xFF0D9488),
        Color(0xFFD946EF),
        Color(0xFF2563EB),
        Color(0xFF059669),
        Color(0xFFEA580C)
    )
    val bgColor = MaterialTheme.colorScheme.surfaceVariant

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(9.dp))
    ) {
        drawRect(color = bgColor)
        if (totalPages > 0) {
            chapters.forEachIndexed { i, ch ->
                val startX = ((ch.startPage - 1).toFloat() / totalPages.toFloat()).coerceIn(0f, 1f) * size.width
                val barW = (ch.pageCount.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f) * size.width
                if (barW > 0) {
                    drawRect(
                        color = colors[i % colors.size],
                        topLeft = androidx.compose.ui.geometry.Offset(startX, 0f),
                        size = androidx.compose.ui.geometry.Size(barW, size.height)
                    )
                }
            }
        }
    }
}
