package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.ImportSource
import com.example.model.PdfDocumentInfo
import com.example.pdf.PdfEngine
import com.example.pdf.SampleBookType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object DocumentImporter {
    private const val TAG = "DocumentImporter"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Imports a PDF from a content Uri (Local Device or Google Drive via SAF).
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        source: ImportSource = ImportSource.LOCAL_DEVICE
    ): Result<PdfDocumentInfo> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var fileName = "document_${System.currentTimeMillis()}.pdf"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }

            val storageDir = File(context.cacheDir, "imported_docs").apply { mkdirs() }
            val destinationFile = File(storageDir, "${UUID.randomUUID()}_$fileName")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open input stream from URI"))

            val pageCount = getPdfPageCount(destinationFile)
            if (pageCount <= 0) {
                destinationFile.delete()
                return@withContext Result.failure(IllegalStateException("File is not a valid PDF or has 0 pages."))
            }

            val cleanTitle = fileName.removeSuffix(".pdf")
            Result.success(
                PdfDocumentInfo(
                    id = UUID.randomUUID().toString(),
                    title = cleanTitle,
                    source = source,
                    localFile = destinationFile,
                    pageCount = pageCount,
                    fileSizeBytes = destinationFile.length(),
                    sourceDescription = "Imported from ${source.displayName}"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import from URI", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads a PDF from a Web URL or Google Drive link.
     */
    suspend fun importFromUrl(
        context: Context,
        rawUrl: String,
        onProgress: (Float) -> Unit
    ): Result<PdfDocumentInfo> = withContext(Dispatchers.IO) {
        try {
            val effectiveUrl = resolveDownloadUrl(rawUrl)
            val request = Request.Builder().url(effectiveUrl).build()

            val titleFromUrl = extractFileNameFromUrl(rawUrl).removeSuffix(".pdf")
            val storageDir = File(context.cacheDir, "imported_docs").apply { mkdirs() }
            val destinationFile = File(storageDir, "${UUID.randomUUID()}_$titleFromUrl.pdf")

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Download failed with HTTP ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(IllegalStateException("Empty response body"))
                val contentLength = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                onProgress(totalRead.toFloat() / contentLength.toFloat())
                            }
                        }
                    }
                }
            }

            val pageCount = getPdfPageCount(destinationFile)
            if (pageCount <= 0) {
                destinationFile.delete()
                return@withContext Result.failure(IllegalStateException("Downloaded file is not a valid PDF."))
            }

            val isDrive = rawUrl.contains("drive.google.com")
            val source = if (isDrive) ImportSource.GOOGLE_DRIVE else ImportSource.WEB_URL

            Result.success(
                PdfDocumentInfo(
                    id = UUID.randomUUID().toString(),
                    title = titleFromUrl.ifBlank { "Web Document" },
                    source = source,
                    localFile = destinationFile,
                    pageCount = pageCount,
                    fileSizeBytes = destinationFile.length(),
                    sourceDescription = if (isDrive) "Downloaded from Google Drive" else "Downloaded from Web"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import from URL", e)
            Result.failure(e)
        }
    }

    /**
     * Loads a built-in scientific sample textbook.
     */
    suspend fun loadSampleBook(
        context: Context,
        bookType: SampleBookType
    ): Result<Pair<PdfDocumentInfo, List<com.example.model.ChapterItem>>> = withContext(Dispatchers.IO) {
        try {
            val file = PdfEngine.generateSampleScienceBook(context, bookType)
            val pageCount = getPdfPageCount(file)

            val docInfo = PdfDocumentInfo(
                id = UUID.randomUUID().toString(),
                title = bookType.title,
                source = ImportSource.SCIENCE_LIBRARY,
                localFile = file,
                pageCount = pageCount,
                fileSizeBytes = file.length(),
                sourceDescription = bookType.subtitle
            )

            val chapters = bookType.chapters.mapIndexed { idx, (title, range) ->
                com.example.model.ChapterItem(
                    id = UUID.randomUUID().toString(),
                    index = idx + 1,
                    title = title,
                    startPage = range.first,
                    endPage = range.last,
                    detectedBy = "Authoritative Science Index",
                    summary = "Official chapter covering theoretical framework and experimental applications.",
                    keyTopics = listOf("STEM Principles", "Formulations", "Core Analysis")
                )
            }

            Result.success(Pair(docInfo, chapters))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sample book", e)
            Result.failure(e)
        }
    }

    /**
     * Loads a bitmap from an image Uri (for index photo scan).
     */
    suspend fun loadBitmapFromUri(
        context: Context,
        uri: Uri
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode image bitmap", e)
            null
        }
    }

    private fun getPdfPageCount(file: File): Int {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read PDF page count", e)
            0
        } finally {
            try {
                renderer?.close()
                pfd?.close()
            } catch (ignored: Exception) { }
        }
    }

    private fun resolveDownloadUrl(url: String): String {
        // Detect Google Drive links and convert to direct download URL
        if (url.contains("drive.google.com")) {
            val fileIdPattern = Pattern.compile("/d/([a-zA-Z0-9_-]+)")
            val matcher = fileIdPattern.matcher(url)
            if (matcher.find()) {
                val fileId = matcher.group(1)
                return "https://drive.google.com/uc?export=download&id=$fileId"
            }
            val idParamPattern = Pattern.compile("id=([a-zA-Z0-9_-]+)")
            val idMatcher = idParamPattern.matcher(url)
            if (idMatcher.find()) {
                val fileId = idMatcher.group(1)
                return "https://drive.google.com/uc?export=download&id=$fileId"
            }
        }
        return url
    }

    private fun extractFileNameFromUrl(url: String): String {
        return try {
            val lastSegment = url.substringAfterLast("/").substringBefore("?")
            if (lastSegment.isNotBlank() && lastSegment.length < 50) {
                lastSegment
            } else {
                "document_${System.currentTimeMillis()}"
            }
        } catch (e: Exception) {
            "document_${System.currentTimeMillis()}"
        }
    }
}
