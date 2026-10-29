package com.example.model

import java.io.File

enum class ImportSource(val displayName: String) {
    LOCAL_DEVICE("Local Device"),
    GOOGLE_DRIVE("Google Drive"),
    WEB_URL("Web URL / Cloud"),
    PHOTO_SCAN("Index Photo Scan"),
    SCIENCE_LIBRARY("Science Library")
}

data class PdfDocumentInfo(
    val id: String,
    val title: String,
    val source: ImportSource,
    val localFile: File,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val sourceDescription: String = ""
)

data class ChapterItem(
    val id: String,
    val index: Int,
    val title: String,
    val startPage: Int, // 1-indexed for human display
    val endPage: Int,   // 1-indexed inclusive
    val detectedBy: String = "Manual",
    val summary: String? = null,
    val keyTopics: List<String> = emptyList()
) {
    val pageCount: Int get() = if (endPage >= startPage) endPage - startPage + 1 else 0
}

enum class IntelligenceMode {
    HIGH_THINKING, // gemini-3.1-pro-preview with ThinkingLevel.HIGH
    FAST_AUTO,     // gemini-3.1-flash-lite
    PHOTO_SCAN,    // gemini-3.1-pro-preview multimodal
    SUMMARY        // gemini-3.5-flash
}

sealed class ProcessingState {
    object Idle : ProcessingState()
    data class Loading(val message: String, val progress: Float = -1f) : ProcessingState()
    data class Success(val message: String) : ProcessingState()
    data class Error(val errorMessage: String) : ProcessingState()
}

data class SplitResult(
    val zipFile: File,
    val chapterFiles: List<GeneratedChapterFile>,
    val totalChapters: Int,
    val zipSizeBytes: Long
)

data class GeneratedChapterFile(
    val chapterTitle: String,
    val startPage: Int,
    val endPage: Int,
    val file: File,
    val sizeBytes: Long
)
