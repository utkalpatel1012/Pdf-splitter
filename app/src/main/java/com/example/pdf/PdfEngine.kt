package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.model.ChapterItem
import com.example.model.GeneratedChapterFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class SampleBookType(
    val title: String,
    val subtitle: String,
    val pageCount: Int,
    val chapters: List<Pair<String, IntRange>>
) {
    QUANTUM_PHYSICS(
        title = "Principles of Quantum Mechanics & Modern Physics",
        subtitle = "University Physics Series — Vol. IV",
        pageCount = 42,
        chapters = listOf(
            "Front Matter & Preface" to 1..4,
            "Chapter 1: The Wave-Particle Duality & Planck's Hypothesis" to 5..12,
            "Chapter 2: The Schrödinger Equation & Wave Functions" to 13..21,
            "Chapter 3: Quantum Operators & Eigenvalues" to 22..30,
            "Chapter 4: The Hydrogen Atom & Angular Momentum" to 31..38,
            "Appendix & Fundamental Physical Constants" to 39..42
        )
    ),
    MOLECULAR_BIOLOGY(
        title = "Molecular Biology & Cellular Genetics",
        subtitle = "Comprehensive Laboratory & Lecture Guide",
        pageCount = 36,
        chapters = listOf(
            "Prelims & Table of Contents" to 1..3,
            "Chapter 1: DNA Replication & Chromatin Architecture" to 4..11,
            "Chapter 2: Transcription Mechanisms & RNA Polymerases" to 12..19,
            "Chapter 3: Protein Translation & Ribosomal Assembly" to 20..27,
            "Chapter 4: CRISPR Gene Editing & Biotechnological Tools" to 28..33,
            "Glossary & Chemical Reagents Index" to 34..36
        )
    ),
    DATA_STRUCTURES(
        title = "Algorithms, Data Structures & System Architecture",
        subtitle = "Advanced Computing & Software Engineering Handbook",
        pageCount = 48,
        chapters = listOf(
            "Introduction & Table of Contents" to 1..4,
            "Chapter 1: Asymptotic Analysis & Big-O Notation" to 5..13,
            "Chapter 2: Trees, Heaps & Balanced Search Structures" to 14..23,
            "Chapter 3: Graph Algorithms, Dijkstra & Network Flows" to 24..35,
            "Chapter 4: Dynamic Programming & Optimization" to 36..44,
            "Appendix: Complexity Classes & Proofs" to 45..48
        )
    )
}

object PdfEngine {
    private const val TAG = "PdfEngine"

    /**
     * Renders a specific 0-indexed page from a PDF file into a Bitmap.
     */
    suspend fun renderPageBitmap(
        file: File,
        pageIndexZeroBased: Int,
        targetWidth: Int = 900
    ): Bitmap? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null

        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)

            if (pageIndexZeroBased < 0 || pageIndexZeroBased >= renderer.pageCount) {
                return@withContext null
            }

            page = renderer.openPage(pageIndexZeroBased)

            val width = targetWidth
            val height = (targetWidth.toFloat() / page.width.toFloat() * page.height.toFloat()).toInt()

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering page $pageIndexZeroBased", e)
            null
        } finally {
            try {
                page?.close()
                renderer?.close()
                pfd?.close()
            } catch (ignored: Exception) { }
        }
    }

    /**
     * Splits a source PDF into separate chapter PDFs based on the provided chapter ranges.
     */
    suspend fun splitDocumentIntoChapters(
        sourcePdf: File,
        chapters: List<ChapterItem>,
        outputDir: File,
        onProgress: (current: Int, total: Int, currentTitle: String) -> Unit
    ): List<GeneratedChapterFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<GeneratedChapterFile>()
        outputDir.mkdirs()

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount

            chapters.forEachIndexed { idx, chapter ->
                val safeStart = (chapter.startPage - 1).coerceIn(0, totalPages - 1)
                val safeEnd = (chapter.endPage - 1).coerceIn(safeStart, totalPages - 1)

                onProgress(idx + 1, chapters.size, chapter.title)

                val sanitizedTitle = chapter.title.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "")
                    .trim()
                    .replace(Regex("\\s+"), "_")
                    .take(50)
                val fileName = String.format("%02d_%s.pdf", idx + 1, sanitizedTitle)
                val chapterPdfFile = File(outputDir, fileName)

                val newDoc = PdfDocument()

                for (pIndex in safeStart..safeEnd) {
                    val page = renderer.openPage(pIndex)
                    try {
                        val renderWidth = 1200
                        val renderHeight = (renderWidth.toFloat() / page.width.toFloat() * page.height.toFloat()).toInt()

                        val pageBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                        val bCanvas = Canvas(pageBitmap)
                        bCanvas.drawColor(Color.WHITE)
                        page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val pageInfo = PdfDocument.PageInfo.Builder(renderWidth, renderHeight, pIndex - safeStart + 1).create()
                        val docPage = newDoc.startPage(pageInfo)
                        val docCanvas = docPage.canvas
                        docCanvas.drawBitmap(pageBitmap, 0f, 0f, null)
                        newDoc.finishPage(docPage)

                        pageBitmap.recycle()
                    } finally {
                        page.close()
                    }
                }

                FileOutputStream(chapterPdfFile).use { out ->
                    newDoc.writeTo(out)
                }
                newDoc.close()

                result.add(
                    GeneratedChapterFile(
                        chapterTitle = chapter.title,
                        startPage = chapter.startPage,
                        endPage = chapter.endPage,
                        file = chapterPdfFile,
                        sizeBytes = chapterPdfFile.length()
                    )
                )
            }
        } finally {
            try {
                renderer?.close()
                pfd?.close()
            } catch (ignored: Exception) { }
        }

        result
    }

    /**
     * Packages all split chapter files and table of contents metadata into a ZIP file.
     */
    suspend fun packageIntoZip(
        chapterFiles: List<GeneratedChapterFile>,
        docTitle: String,
        chapters: List<ChapterItem>,
        zipOutputFile: File
    ): File = withContext(Dispatchers.IO) {
        if (zipOutputFile.exists()) {
            zipOutputFile.delete()
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipOutputFile))).use { zos ->
            // 1. Add each chapter PDF
            val buffer = ByteArray(8192)
            chapterFiles.forEach { chapterFile ->
                val entry = ZipEntry(chapterFile.file.name)
                zos.putNextEntry(entry)
                FileInputStream(chapterFile.file).use { fis ->
                    var count: Int
                    while (fis.read(buffer).also { count = it } != -1) {
                        zos.write(buffer, 0, count)
                    }
                }
                zos.closeEntry()
            }

            // 2. Add Table of Contents text summary
            val tocContent = buildString {
                appendLine("==================================================")
                appendLine("  CHAPTERSPLIT - TABLE OF CONTENTS SUMMARY")
                appendLine("  Document: $docTitle")
                appendLine("  Total Split Chapters: ${chapters.size}")
                appendLine("==================================================")
                appendLine()
                chapters.forEachIndexed { i, ch ->
                    appendLine(String.format("[%02d] %s", i + 1, ch.title))
                    appendLine("     Pages: ${ch.startPage} - ${ch.endPage} (${ch.pageCount} pages)")
                    appendLine("     Segregated by: ${ch.detectedBy}")
                    if (!ch.summary.isNullOrBlank()) {
                        appendLine("     Summary: ${ch.summary}")
                    }
                    if (ch.keyTopics.isNotEmpty()) {
                        appendLine("     Key Topics: ${ch.keyTopics.joinToString(", ")}")
                    }
                    appendLine()
                }
                appendLine("Generated with ChapterSplit AI")
            }

            val tocEntry = ZipEntry("00_TABLE_OF_CONTENTS.txt")
            zos.putNextEntry(tocEntry)
            zos.write(tocContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. Add JSON Manifest for automated workflows
            val manifestContent = buildString {
                appendLine("{")
                appendLine("  \"documentTitle\": \"$docTitle\",")
                appendLine("  \"chapterCount\": ${chapters.size},")
                appendLine("  \"chapters\": [")
                chapters.forEachIndexed { i, ch ->
                    appendLine("    {")
                    appendLine("      \"index\": ${i + 1},")
                    appendLine("      \"title\": \"${ch.title.replace("\"", "\\\"")}\",")
                    appendLine("      \"startPage\": ${ch.startPage},")
                    appendLine("      \"endPage\": ${ch.endPage},")
                    appendLine("      \"pageCount\": ${ch.pageCount}")
                    appendLine("    }${if (i < chapters.size - 1) "," else ""}")
                }
                appendLine("  ]")
                appendLine("}")
            }
            val manifestEntry = ZipEntry("manifest.json")
            zos.putNextEntry(manifestEntry)
            zos.write(manifestContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        zipOutputFile
    }

    /**
     * Generates a realistic, rich multi-chapter sample scientific textbook in PDF format.
     */
    suspend fun generateSampleScienceBook(
        context: Context,
        bookType: SampleBookType
    ): File = withContext(Dispatchers.IO) {
        val samplesDir = File(context.cacheDir, "sample_books")
        samplesDir.mkdirs()
        val targetFile = File(samplesDir, "${bookType.name.lowercase()}.pdf")

        if (targetFile.exists() && targetFile.length() > 1024) {
            return@withContext targetFile
        }

        val doc = PdfDocument()
        val pageWidth = 800
        val pageHeight = 1100

        val titlePaint = Paint().apply {
            color = Color.rgb(30, 27, 75)
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.rgb(79, 70, 229)
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 14f
            isAntiAlias = true
        }

        val accentPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }

        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val footerPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 12f
            isAntiAlias = true
        }

        for (pageNum in 1..bookType.pageCount) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            // Page Background
            canvas.drawColor(Color.WHITE)

            // Header Banner
            canvas.drawRect(Rect(40, 40, pageWidth - 40, 70), accentPaint)
            canvas.drawText("${bookType.title} • ${bookType.subtitle}", 50f, 62f, footerPaint)

            // Determine which chapter this page belongs to
            val currentChapter = bookType.chapters.find { pageNum in it.second }
            val chapterTitle = currentChapter?.first ?: "Chapter Content"

            if (pageNum == 1) {
                // Book Title Cover
                canvas.drawRect(Rect(50, 150, pageWidth - 50, 400), accentPaint)
                canvas.drawRect(Rect(50, 150, pageWidth - 50, 400), borderPaint)

                canvas.drawText(bookType.title, 70f, 220f, titlePaint)
                canvas.drawText(bookType.subtitle, 70f, 260f, headerPaint)
                canvas.drawText("Comprehensive Reference Guide for STEM Scholars & Engineers", 70f, 310f, bodyPaint)
                canvas.drawText("Total Pages: ${bookType.pageCount} | Includes Complete Chapter Index", 70f, 340f, footerPaint)

                // Quick overview
                canvas.drawText("Book Structure & Overview:", 70f, 460f, headerPaint)
                var y = 500f
                bookType.chapters.forEach { (title, range) ->
                    canvas.drawText("• $title (Pages ${range.first} - ${range.last})", 70f, y, bodyPaint)
                    y += 35f
                }
            } else if (pageNum == 2 || pageNum == 3) {
                // Table of Contents Page
                canvas.drawText("TABLE OF CONTENTS / INDEX", 50f, 130f, titlePaint)
                canvas.drawLine(50f, 150f, (pageWidth - 50).toFloat(), 150f, borderPaint)

                var y = 200f
                bookType.chapters.forEachIndexed { i, (title, range) ->
                    canvas.drawText("${i + 1}. $title", 60f, y, headerPaint)
                    val dots = "..........................................................."
                    canvas.drawText(dots, 450f, y, footerPaint)
                    canvas.drawText("pp. ${range.first} - ${range.last}", 680f, y, headerPaint)
                    y += 30f

                    canvas.drawText("   Scientific concepts, mathematical formulations, and experimental verification.", 80f, y, footerPaint)
                    y += 45f
                }
            } else {
                // Chapter Pages
                val isChapterStart = currentChapter?.second?.first == pageNum

                if (isChapterStart) {
                    canvas.drawRect(Rect(50, 110, pageWidth - 50, 190), accentPaint)
                    canvas.drawRect(Rect(50, 110, pageWidth - 50, 190), borderPaint)
                    canvas.drawText(chapterTitle, 70f, 160f, headerPaint)
                } else {
                    canvas.drawText(chapterTitle, 50f, 120f, headerPaint)
                    canvas.drawLine(50f, 135f, (pageWidth - 50).toFloat(), 135f, borderPaint)
                }

                var y = if (isChapterStart) 230f else 170f

                canvas.drawText("1. Theoretical Framework & Foundational Principles", 50f, y, headerPaint)
                y += 30f
                canvas.drawText("The physical systems governed by these principles exhibit fundamental symmetry.", 50f, y, bodyPaint)
                y += 25f
                canvas.drawText("Experimental evidence confirms high fidelity and rigorous reproducibility across domains.", 50f, y, bodyPaint)
                y += 40f

                // Visual Formula / Diagram Card
                canvas.drawRect(Rect(50, y.toInt(), pageWidth - 50, (y + 110).toInt()), accentPaint)
                canvas.drawRect(Rect(50, y.toInt(), pageWidth - 50, (y + 110).toInt()), borderPaint)
                canvas.drawText("Mathematical Representation (Section ${pageNum}.1):", 70f, y + 35f, footerPaint)
                canvas.drawText("Ψ(r, t) = A · exp[ i(k · r - ωt) ]  ⇒  Ĥ |Ψ⟩ = E |Ψ⟩", 70f, y + 70f, titlePaint)
                y += 140f

                canvas.drawText("2. Applications, Laboratory Analysis & Observations", 50f, y, headerPaint)
                y += 30f
                canvas.drawText("State observations during phase transitions demonstrate nonlinear divergence.", 50f, y, bodyPaint)
                y += 25f
                canvas.drawText("Parameters are tuned using gradient descent with dynamic step decay.", 50f, y, bodyPaint)
                y += 25f
                canvas.drawText("Further analysis is provided in the proceeding sections and accompanying lab dataset.", 50f, y, bodyPaint)
            }

            // Footer with Page Number
            canvas.drawLine(50f, pageHeight - 60f, (pageWidth - 50).toFloat(), pageHeight - 60f, borderPaint)
            canvas.drawText("Page $pageNum of ${bookType.pageCount}", (pageWidth / 2f) - 40f, pageHeight - 35f, footerPaint)

            doc.finishPage(page)
        }

        FileOutputStream(targetFile).use { out ->
            doc.writeTo(out)
        }
        doc.close()

        targetFile
    }
}
