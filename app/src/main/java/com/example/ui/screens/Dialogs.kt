package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ChapterItem

@Composable
fun WebUrlDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    val sampleUrls = listOf(
        "Open Textbook: Physics Fundamentals" to "https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/tracemonkey.pdf",
        "Google Drive Shared File Sample" to "https://drive.google.com/file/d/1sample_drive_id/view"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Download PDF from Web or Drive", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Enter a direct PDF download link or a shared Google Drive link. The app will automatically resolve and download the file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("PDF or Drive URL") },
                    placeholder = { Text("https://example.com/document.pdf") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("url_input_field"),
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
                )

                Text("Quick Preset Samples:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                sampleUrls.forEach { (name, link) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { urlText = link },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlText.isNotBlank()) {
                        onSubmit(urlText.trim())
                    }
                },
                enabled = urlText.isNotBlank(),
                modifier = Modifier.testTag("url_submit_button")
            ) {
                Text("Download & Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun IndexTextDialog(
    docTitle: String,
    totalPages: Int,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var rawText by remember {
        mutableStateOf(
            "Contents\n" +
            "Preface & Notation ...... 1\n" +
            "Chapter 1: Principles and Foundations ...... 5\n" +
            "Chapter 2: Structural Mechanisms ...... 14\n" +
            "Chapter 3: Experimental Analysis & Methods ...... 24\n" +
            "Chapter 4: Advanced Systems & Discussion ...... 33\n" +
            "Appendix & Bibliography ...... 40"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("🧠 High-Thinking Index Reasoning", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Gemini 3.1 Pro (with High Thinking Mode) will reason through roman numeral page offsets, front matter, chapter hierarchies, and align them to the real $totalPages PDF pages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("Table of Contents / Index Text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("index_text_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rawText.isNotBlank()) {
                        onSubmit(rawText.trim())
                    }
                },
                enabled = rawText.isNotBlank(),
                modifier = Modifier.testTag("index_analyze_submit")
            ) {
                Text("Analyze with AI")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditChapterDialog(
    initialChapter: ChapterItem?,
    maxPages: Int,
    onDismiss: () -> Unit,
    onSave: (title: String, startPage: Int, endPage: Int) -> Unit
) {
    var title by remember { mutableStateOf(initialChapter?.title ?: "New Chapter") }
    var startPageStr by remember { mutableStateOf((initialChapter?.startPage ?: 1).toString()) }
    var endPageStr by remember { mutableStateOf((initialChapter?.endPage ?: maxPages.coerceAtMost(10)).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialChapter != null) "Edit Chapter Range" else "Add Chapter", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Chapter Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("chapter_title_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = startPageStr,
                        onValueChange = { startPageStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Start Page") },
                        modifier = Modifier.weight(1f).testTag("start_page_input")
                    )
                    OutlinedTextField(
                        value = endPageStr,
                        onValueChange = { endPageStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("End Page") },
                        modifier = Modifier.weight(1f).testTag("end_page_input")
                    )
                }

                Text(
                    "Total document has $maxPages pages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = startPageStr.toIntOrNull() ?: 1
                    val e = endPageStr.toIntOrNull() ?: s
                    onSave(title.trim(), s, e)
                },
                modifier = Modifier.testTag("chapter_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ChapterSummaryDialog(
    chapter: ChapterItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(chapter.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "Pages ${chapter.startPage} - ${chapter.endPage} (${chapter.pageCount} pages)",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text("AI Chapter Summary (Gemini 3.5 Flash):", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    chapter.summary ?: "No summary available yet.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (chapter.keyTopics.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Key Concepts Covered:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    chapter.keyTopics.forEach { topic ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(topic, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
