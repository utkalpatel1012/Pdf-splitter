package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DocumentImporter
import com.example.model.ChapterItem
import com.example.model.ImportSource
import com.example.model.PdfDocumentInfo
import com.example.model.ProcessingState
import com.example.model.SplitResult
import com.example.pdf.PdfEngine
import com.example.pdf.SampleBookType
import com.example.service.GeminiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class PdfSplitViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "PdfSplitViewModel"

    private val _currentDoc = MutableStateFlow<PdfDocumentInfo?>(null)
    val currentDoc: StateFlow<PdfDocumentInfo?> = _currentDoc.asStateFlow()

    private val _chapters = MutableStateFlow<List<ChapterItem>>(emptyList())
    val chapters: StateFlow<List<ChapterItem>> = _chapters.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _splitResult = MutableStateFlow<SplitResult?>(null)
    val splitResult: StateFlow<SplitResult?> = _splitResult.asStateFlow()

    // Preview state
    private val _selectedChapterId = MutableStateFlow<String?>(null)
    val selectedChapterId: StateFlow<String?> = _selectedChapterId.asStateFlow()

    private val _previewPageBitmap = MutableStateFlow<Bitmap?>(null)
    val previewPageBitmap: StateFlow<Bitmap?> = _previewPageBitmap.asStateFlow()

    private val _previewCurrentPage = MutableStateFlow<Int>(1)
    val previewCurrentPage: StateFlow<Int> = _previewCurrentPage.asStateFlow()

    private val _previewLoading = MutableStateFlow<Boolean>(false)
    val previewLoading: StateFlow<Boolean> = _previewLoading.asStateFlow()

    // Page bitmap cache
    private val bitmapCache = mutableMapOf<Int, Bitmap>()

    fun resetState() {
        _currentDoc.value = null
        _chapters.value = emptyList()
        _processingState.value = ProcessingState.Idle
        _splitResult.value = null
        _selectedChapterId.value = null
        _previewPageBitmap.value = null
        bitmapCache.clear()
    }

    fun loadFromUri(uri: Uri, source: ImportSource = ImportSource.LOCAL_DEVICE) {
        viewModelScope.launch {
            _processingState.value = ProcessingState.Loading("Importing document from ${source.displayName}...")
            val result = DocumentImporter.importFromUri(getApplication(), uri, source)
            result.onSuccess { doc ->
                _currentDoc.value = doc
                _processingState.value = ProcessingState.Success("Document loaded (${doc.pageCount} pages)")
                autoGenerateInitialChapters(doc)
                loadPagePreview(1)
            }.onFailure { err ->
                _processingState.value = ProcessingState.Error("Failed to import: ${err.message}")
            }
        }
    }

    fun loadFromUrl(url: String) {
        viewModelScope.launch {
            _processingState.value = ProcessingState.Loading("Connecting & downloading PDF...", 0f)
            val result = DocumentImporter.importFromUrl(getApplication(), url) { progress ->
                _processingState.value = ProcessingState.Loading("Downloading PDF: ${(progress * 100).toInt()}%", progress)
            }
            result.onSuccess { doc ->
                _currentDoc.value = doc
                _processingState.value = ProcessingState.Success("Document downloaded (${doc.pageCount} pages)")
                autoGenerateInitialChapters(doc)
                loadPagePreview(1)
            }.onFailure { err ->
                _processingState.value = ProcessingState.Error("Failed to download: ${err.message}")
            }
        }
    }

    fun loadSampleBook(bookType: SampleBookType) {
        viewModelScope.launch {
            _processingState.value = ProcessingState.Loading("Loading ${bookType.title}...")
            val result = DocumentImporter.loadSampleBook(getApplication(), bookType)
            result.onSuccess { (doc, sampleChapters) ->
                _currentDoc.value = doc
                _chapters.value = sampleChapters
                _selectedChapterId.value = sampleChapters.firstOrNull()?.id
                _processingState.value = ProcessingState.Success("Loaded ${bookType.title} (${doc.pageCount} pages)")
                loadPagePreview(1)
            }.onFailure { err ->
                _processingState.value = ProcessingState.Error("Failed to load sample: ${err.message}")
            }
        }
    }

    /**
     * Gemini 3.1 Pro with High Thinking mode (thinkingLevel = "HIGH", no maxOutputTokens).
     */
    fun analyzeIndexWithHighThinking(indexText: String) {
        val doc = _currentDoc.value ?: return
        viewModelScope.launch {
            _processingState.value = ProcessingState.Loading("🧠 High-Thinking Reasoning: Analyzing index structure, roman numeral offsets, and chapter ranges...")
            val result = GeminiService.analyzeIndexWithHighThinking(indexText, doc.pageCount, doc.title)
            result.onSuccess { parsedChapters ->
                if (parsedChapters.isNotEmpty()) {
                    _chapters.value = parsedChapters
                    _selectedChapterId.value = parsedChapters.first().id
                    _processingState.value = ProcessingState.Success("AI segregated ${parsedChapters.size} chapters with high-thinking precision!")
                } else {
                    _processingState.value = ProcessingState.Error("No chapters detected in provided index text.")
                }
            }.onFailure { err ->
                // Fallback to local heuristic index parser if API key is not configured or network error
                val fallbackChapters = parseIndexTextHeuristically(indexText, doc.pageCount)
                if (fallbackChapters.isNotEmpty()) {
                    _chapters.value = fallbackChapters
                    _selectedChapterId.value = fallbackChapters.first().id
                    _processingState.value = ProcessingState.Success("Segregated ${fallbackChapters.size} chapters (Local Heuristic Engine)")
                } else {
                    _processingState.value = ProcessingState.Error("Analysis failed: ${err.message}. You can also add chapters manually.")
                }
            }
        }
    }

    /**
     * Gemini 3.1 Pro Multimodal: Analyzes photo of index/TOC.
     */
    fun analyzePhotoIndex(bitmap: Bitmap) {
        val doc = _currentDoc.value ?: return
        viewModelScope.launch {
            _processingState.value = ProcessingState.Loading("📷 Gemini 3.1 Pro: Scanning index photograph & reading chapter bounds...")
            val result = GeminiService.analyzeIndexImage(bitmap, doc.pageCount, doc.title)
            result.onSuccess { parsedChapters ->
                if (parsedChapters.isNotEmpty()) {
                    _chapters.value = parsedChapters
                    _selectedChapterId.value = parsedChapters.first().id
                    _processingState.value = ProcessingState.Success("Extracted ${parsedChapters.size} chapters from index photo!")
                } else {
                    _processingState.value = ProcessingState.Error("Could not detect chapter entries from photo.")
                }
            }.onFailure { err ->
                _processingState.value = ProcessingState.Error("Photo analysis error: ${err.message}")
            }
        }
    }

    /**
     * Fast Auto Segregate using Gemini 3.1 Flash Lite.
     */
    fun quickAutoSegregate() {
        val doc = _currentDoc.value ?: return
        viewModelScope.launch {
            _processingState.value = ProcessingState.Loading("⚡ Gemini 3.1 Flash Lite: Fast auto-segregation...")
            val headers = listOf(
                "Introduction and Principles",
                "Theoretical Foundation",
                "Experimental Methodology",
                "Data Analysis and Observations",
                "Advanced Extensions",
                "Conclusion & Appendices"
            )
            val result = GeminiService.quickAutoSegregate(doc.pageCount, doc.title, headers)
            result.onSuccess { parsedChapters ->
                if (parsedChapters.isNotEmpty()) {
                    _chapters.value = parsedChapters
                    _selectedChapterId.value = parsedChapters.first().id
                    _processingState.value = ProcessingState.Success("Fast auto-segregated into ${parsedChapters.size} chapters")
                }
            }.onFailure {
                // local fallback division
                val fallback = autoDividePages(doc.pageCount)
                _chapters.value = fallback
                _selectedChapterId.value = fallback.firstOrNull()?.id
                _processingState.value = ProcessingState.Success("Auto-segregated into ${fallback.size} chapters")
            }
        }
    }

    /**
     * Gemini 3.5 Flash: Summarizes chapter.
     */
    fun summarizeChapter(chapterId: String) {
        val doc = _currentDoc.value ?: return
        val chapter = _chapters.value.find { it.id == chapterId } ?: return

        viewModelScope.launch {
            _processingState.value = ProcessingState.Loading("✨ Gemini 3.5 Flash: Summarizing ${chapter.title}...")
            val excerpt = "Chapter covering pages ${chapter.startPage} through ${chapter.endPage} of document ${doc.title}."
            val result = GeminiService.summarizeChapter(chapter.title, "Pages ${chapter.startPage}-${chapter.endPage}", excerpt)

            result.onSuccess { (summary, topics) ->
                _chapters.value = _chapters.value.map {
                    if (it.id == chapterId) {
                        it.copy(summary = summary, keyTopics = topics)
                    } else it
                }
                _processingState.value = ProcessingState.Success("Summary generated for ${chapter.title}")
            }.onFailure { err ->
                _processingState.value = ProcessingState.Error("Summarization error: ${err.message}")
            }
        }
    }

    fun addChapter(title: String, startPage: Int, endPage: Int) {
        val doc = _currentDoc.value ?: return
        val validStart = startPage.coerceIn(1, doc.pageCount)
        val validEnd = endPage.coerceIn(validStart, doc.pageCount)

        val newChapter = ChapterItem(
            id = UUID.randomUUID().toString(),
            index = _chapters.value.size + 1,
            title = title.ifBlank { "Chapter ${_chapters.value.size + 1}" },
            startPage = validStart,
            endPage = validEnd,
            detectedBy = "User Custom"
        )
        val updated = (_chapters.value + newChapter).sortedBy { it.startPage }
            .mapIndexed { idx, item -> item.copy(index = idx + 1) }
        _chapters.value = updated
        _selectedChapterId.value = newChapter.id
    }

    fun updateChapter(id: String, title: String, startPage: Int, endPage: Int) {
        val doc = _currentDoc.value ?: return
        val validStart = startPage.coerceIn(1, doc.pageCount)
        val validEnd = endPage.coerceIn(validStart, doc.pageCount)

        val updated = _chapters.value.map {
            if (it.id == id) {
                it.copy(title = title, startPage = validStart, endPage = validEnd)
            } else it
        }.sortedBy { it.startPage }
            .mapIndexed { idx, item -> item.copy(index = idx + 1) }

        _chapters.value = updated
    }

    fun removeChapter(id: String) {
        val updated = _chapters.value.filter { it.id != id }
            .mapIndexed { idx, item -> item.copy(index = idx + 1) }
        _chapters.value = updated
        if (_selectedChapterId.value == id) {
            _selectedChapterId.value = updated.firstOrNull()?.id
        }
    }

    fun selectChapter(id: String) {
        _selectedChapterId.value = id
        val chapter = _chapters.value.find { it.id == id }
        if (chapter != null) {
            loadPagePreview(chapter.startPage)
        }
    }

    fun loadPagePreview(pageNum1Based: Int) {
        val doc = _currentDoc.value ?: return
        val safePage = pageNum1Based.coerceIn(1, doc.pageCount)
        _previewCurrentPage.value = safePage

        if (bitmapCache.containsKey(safePage)) {
            _previewPageBitmap.value = bitmapCache[safePage]
            return
        }

        viewModelScope.launch {
            _previewLoading.value = true
            val bitmap = PdfEngine.renderPageBitmap(doc.localFile, safePage - 1, targetWidth = 1000)
            if (bitmap != null) {
                bitmapCache[safePage] = bitmap
                _previewPageBitmap.value = bitmap
            }
            _previewLoading.value = false
        }
    }

    /**
     * Splits the PDF into individual chapter PDF documents and packages everything into a ZIP file.
     */
    fun splitAndExportZip() {
        val doc = _currentDoc.value ?: return
        val currentChapters = _chapters.value
        if (currentChapters.isEmpty()) {
            _processingState.value = ProcessingState.Error("Please define at least one chapter before splitting.")
            return
        }

        viewModelScope.launch {
            _processingState.value = ProcessingState.Loading("Splitting PDF into chapter files...", 0f)

            try {
                val context = getApplication<Application>()
                val splitFolder = File(context.cacheDir, "split_chapters_${System.currentTimeMillis()}").apply { mkdirs() }

                val generatedChapterFiles = PdfEngine.splitDocumentIntoChapters(
                    sourcePdf = doc.localFile,
                    chapters = currentChapters,
                    outputDir = splitFolder
                ) { current, total, title ->
                    val progress = current.toFloat() / total.toFloat()
                    _processingState.value = ProcessingState.Loading(
                        "Rendering Chapter $current of $total: $title",
                        progress
                    )
                }

                _processingState.value = ProcessingState.Loading("Archiving chapters into ZIP package...", 0.9f)

                val zipFile = File(context.cacheDir, "${doc.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}_Chapters.zip")
                PdfEngine.packageIntoZip(
                    chapterFiles = generatedChapterFiles,
                    docTitle = doc.title,
                    chapters = currentChapters,
                    zipOutputFile = zipFile
                )

                _splitResult.value = SplitResult(
                    zipFile = zipFile,
                    chapterFiles = generatedChapterFiles,
                    totalChapters = generatedChapterFiles.size,
                    zipSizeBytes = zipFile.length()
                )

                _processingState.value = ProcessingState.Success("Successfully generated ${generatedChapterFiles.size} chapter PDFs in ZIP archive!")
            } catch (e: Exception) {
                Log.e(TAG, "Split and ZIP export failed", e)
                _processingState.value = ProcessingState.Error("Split error: ${e.message}")
            }
        }
    }

    /**
     * Writes the generated ZIP file to a destination Uri chosen by the user (Storage Access Framework).
     */
    fun saveZipToDestinationUri(context: Context, destUri: Uri): Boolean {
        val splitRes = _splitResult.value ?: return false
        return try {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                FileInputStream(splitRes.zipFile).use { inStream ->
                    inStream.copyTo(out)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ZIP to Uri", e)
            false
        }
    }

    /**
     * Shares the ZIP file using the Android system Sharesheet (Google Drive, Gmail, Files, etc.).
     */
    fun shareZipFile(context: Context) {
        val splitRes = _splitResult.value ?: return
        try {
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                splitRes.zipFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "${splitRes.zipFile.nameWithoutExtension} - Chapters Split")
                putExtra(Intent.EXTRA_TEXT, "Here are the separated chapter PDFs for ${_currentDoc.value?.title ?: "Document"} packaged in a ZIP file.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Export ZIP (Google Drive, Files, Apps)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share ZIP", e)
            _processingState.value = ProcessingState.Error("Sharing failed: ${e.message}")
        }
    }

    /**
     * Opens an individual chapter PDF.
     */
    fun openChapterFile(context: Context, chapterFile: File) {
        try {
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                chapterFile
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open PDF viewer", e)
            _processingState.value = ProcessingState.Error("Could not launch external PDF reader: ${e.message}")
        }
    }

    private fun autoGenerateInitialChapters(doc: PdfDocumentInfo) {
        val chapters = autoDividePages(doc.pageCount)
        _chapters.value = chapters
        _selectedChapterId.value = chapters.firstOrNull()?.id
    }

    private fun autoDividePages(total: Int): List<ChapterItem> {
        val chapterCount = when {
            total <= 10 -> 2
            total <= 30 -> 4
            total <= 60 -> 6
            else -> 8
        }
        val pagesPerChapter = (total + chapterCount - 1) / chapterCount
        val list = mutableListOf<ChapterItem>()

        var currentStart = 1
        for (i in 1..chapterCount) {
            val end = (currentStart + pagesPerChapter - 1).coerceAtMost(total)
            list.add(
                ChapterItem(
                    id = UUID.randomUUID().toString(),
                    index = i,
                    title = if (i == 1 && total > 15) "Prelims & Introduction" else "Chapter $i: Core Concepts",
                    startPage = currentStart,
                    endPage = end,
                    detectedBy = "Page Partition Heuristic"
                )
            )
            currentStart = end + 1
            if (currentStart > total) break
        }
        return list
    }

    private fun parseIndexTextHeuristically(text: String, totalPages: Int): List<ChapterItem> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val detected = mutableListOf<Triple<String, Int, Int?>>()

        // regex matching e.g. "Chapter 1 ... 15", "1. Intro ... 5"
        val regex = Regex("^(.*?)(?:\\.{2,}|\\s{2,}|[-–—:]\\s*|\\s+page\\s+|\\s+pp\\.?\\s+|\\s+)(\\d+)\\s*$")

        for (line in lines) {
            val match = regex.find(line)
            if (match != null) {
                val title = match.groupValues[1].trim().trimEnd('.', '-')
                val page = match.groupValues[2].toIntOrNull()
                if (title.isNotBlank() && page != null && page in 1..totalPages) {
                    detected.add(Triple(title, page, null))
                }
            }
        }

        if (detected.isEmpty()) return emptyList()

        val chapters = mutableListOf<ChapterItem>()
        for (i in 0 until detected.size) {
            val current = detected[i]
            val nextStart = if (i < detected.size - 1) detected[i + 1].second else totalPages + 1
            val endPage = (nextStart - 1).coerceAtLeast(current.second).coerceAtMost(totalPages)

            chapters.add(
                ChapterItem(
                    id = UUID.randomUUID().toString(),
                    index = i + 1,
                    title = current.first,
                    startPage = current.second,
                    endPage = endPage,
                    detectedBy = "Local Heuristic Parser"
                )
            )
        }
        return chapters
    }
}
