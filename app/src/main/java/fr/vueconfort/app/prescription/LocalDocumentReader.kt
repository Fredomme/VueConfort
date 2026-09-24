package fr.vueconfort.app.prescription

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fr.vueconfort.app.model.PrescriptionSource
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The caller owns preview after a successful return; the reader never retains it. */
data class DocumentReadResult(
    val text: String,
    val preview: Bitmap,
    val pageCount: Int,
    val pageIndex: Int,
    val source: PrescriptionSource,
    val displayName: String,
)

/** A user-facing message, deliberately without raw provider paths or document contents. */
class LocalDocumentReadException(message: String) : IOException(message)

/**
 * On-device OCR using the bundled Latin model. No network API, upload, text log or
 * persistent document store. A bounded private-cache copy supplies a seekable PDF
 * descriptor and enforces the byte limit even for providers with unknown size.
 * The copy is deleted on success, failure and coroutine cancellation.
 */
class LocalDocumentReader(context: Context) {
    private val applicationContext = context.applicationContext
    private val resolver = applicationContext.contentResolver

    suspend fun read(uri: Uri, pageIndex: Int = 0): DocumentReadResult = READ_MUTEX.withLock {
        var ownedPreview: Bitmap? = null
        try {
            val result = withContext(Dispatchers.IO) {
                coroutineContext.ensureActive()
                if (uri.scheme != "content" && uri.scheme != "file")
                    throw LocalDocumentReadException("Sélectionnez une image ou un PDF enregistré sur votre appareil.")
                if (pageIndex < 0) throw LocalDocumentReadException("Cette page n’existe pas dans le document.")
                val metadata = metadata(uri)
                if (metadata.bytes != null && metadata.bytes > MAX_BYTES) throw tooLarge()
                val declaredMime = resolver.getType(uri)?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                    ?.let { if (it == "image/jpg") "image/jpeg" else it }
                if (!declaredMime.isNullOrEmpty() && declaredMime != "application/octet-stream" && declaredMime !in MIME_TYPES)
                    throw unsupported()
                val temporary = File.createTempFile(TEMP_PREFIX, ".tmp", preparePrivateCache())
                try {
                    copyBounded(uri, temporary)
                    coroutineContext.ensureActive()
                    val detectedMime = detectMime(temporary)
                    if (!declaredMime.isNullOrEmpty() && declaredMime != "application/octet-stream" && declaredMime != detectedMime)
                        throw LocalDocumentReadException("Le contenu du fichier ne correspond pas à son format annoncé. Choisissez un autre fichier.")
                    val decoded = if (detectedMime == "application/pdf") renderPdf(temporary, pageIndex)
                        else {
                            if (pageIndex != 0) throw LocalDocumentReadException("Une image ne contient qu’une seule page.")
                            DecodedPage(decodeImage(temporary, detectedMime), 1, PrescriptionSource.IMAGE)
                        }
                    ownedPreview = decoded.bitmap
                    // The bitmap is now independent of the source. Remove the document
                    // before the potentially longer OCR operation, not only on return.
                    deleteTemporary(temporary)
                    coroutineContext.ensureActive()
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val recognized = try {
                        val task = recognizer.process(InputImage.fromBitmap(decoded.bitmap, 0))
                        // ML Kit does not cancel this task when our coroutine is cancelled.
                        // Keep its bitmap and recognizer alive until completion, then honour
                        // cancellation before transferring ownership to the caller.
                        withContext(NonCancellable) { awaitCompletion(task) }
                    } finally {
                        recognizer.close()
                    }
                    coroutineContext.ensureActive()
                    DocumentReadResult(
                        text = spatialRows(recognized),
                        preview = decoded.bitmap,
                        pageCount = decoded.count,
                        pageIndex = pageIndex,
                        source = decoded.source,
                        displayName = metadata.name.ifBlank {
                            if (decoded.source == PrescriptionSource.PDF) "Document PDF" else "Image importée"
                        },
                    )
                } finally {
                    // Executed on the IO worker, including after awaiting a cancelled OCR.
                    deleteTemporary(temporary)
                }
            }
            // withContext can discard a completed result if cancellation races its return.
            // Keep ownership outside that boundary until the caller can really receive it.
            coroutineContext.ensureActive()
            ownedPreview = null
            result
        } catch (error: CancellationException) {
            throw error
        } catch (error: LocalDocumentReadException) {
            throw error
        } catch (_: OutOfMemoryError) {
            throw LocalDocumentReadException("La mémoire disponible ne suffit pas pour ce document. Fermez d’autres applications ou choisissez une image moins grande.")
        } catch (_: SecurityException) {
            throw LocalDocumentReadException("L’accès au document a expiré ou a été refusé. Sélectionnez-le à nouveau.")
        } catch (_: FileNotFoundException) {
            throw LocalDocumentReadException("Ce document n’est plus accessible. Sélectionnez-le à nouveau.")
        } catch (_: IOException) {
            throw LocalDocumentReadException("Impossible de lire ce document. Vérifiez qu’il est complet et choisissez-le à nouveau.")
        } catch (_: Exception) {
            throw LocalDocumentReadException("La lecture locale du texte a échoué. Réessayez avec une image nette ou utilisez la saisie manuelle.")
        } finally {
            ownedPreview?.recycle()
        }
    }

    private data class Metadata(val name: String, val bytes: Long?)
    private data class DecodedPage(val bitmap: Bitmap, val count: Int, val source: PrescriptionSource)

    /** Called under the process-wide read mutex, before any active copy can exist. */
    private fun preparePrivateCache(forceCleanup: Boolean = false): File {
        val directory = File(applicationContext.cacheDir, CACHE_DIRECTORY)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Private cache unavailable")
        if (forceCleanup || directory.absolutePath !in INITIALIZED_CACHE_PATHS) {
            // An Android process kill does not execute finally. Remove only our known
            // temporary copies on the next process's first read, including legacy files.
            for (parent in listOf(directory, applicationContext.cacheDir)) {
                parent.listFiles()?.filter { it.isFile && it.name.startsWith(TEMP_PREFIX) && it.name.endsWith(".tmp") }
                    ?.forEach(::deleteTemporary)
            }
            INITIALIZED_CACHE_PATHS.add(directory.absolutePath)
        }
        return directory
    }

    private fun deleteTemporary(file: File) {
        if (file.exists() && !file.delete())
            throw LocalDocumentReadException("La copie temporaire du document n’a pas pu être supprimée. Fermez puis rouvrez VueConfort.")
    }

    private fun metadata(uri: Uri): Metadata {
        var name = if (uri.scheme == "file") uri.lastPathSegment.orEmpty() else ""
        var bytes: Long? = null
        if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) bytes = File(path).length().takeIf { it >= 0 }
        } else {
            // Metadata is optional; the actual stream is always byte-counted below.
            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn).orEmpty()
                        if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) bytes = cursor.getLong(sizeColumn).takeIf { it >= 0 }
                    }
                }
            } catch (_: IllegalArgumentException) {
                // Some providers do not expose OpenableColumns.
            } catch (_: UnsupportedOperationException) {
                // Reading the content is still possible without its display metadata.
            }
        }
        val safeName = name.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().take(160)
        return Metadata(safeName, bytes)
    }

    private suspend fun copyBounded(uri: Uri, destination: File) {
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException()
        input.use { source ->
            destination.outputStream().use { target ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count
                    if (total > MAX_BYTES) throw tooLarge()
                    target.write(buffer, 0, count)
                }
                if (total == 0L) throw LocalDocumentReadException("Le fichier sélectionné est vide.")
            }
        }
    }

    private fun detectMime(file: File): String {
        val header = ByteArray(1024)
        val count = file.inputStream().use { it.read(header) }
        fun byte(index: Int) = header[index].toInt() and 255
        if (count >= 3 && byte(0) == 0xff && byte(1) == 0xd8 && byte(2) == 0xff) return "image/jpeg"
        if (count >= 8 && header.copyOf(8).contentEquals(PNG_SIGNATURE)) return "image/png"
        if (count >= 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WEBP") return "image/webp"
        if (count >= 5 && String(header, 0, count, Charsets.ISO_8859_1).contains("%PDF-")) return "application/pdf"
        throw unsupported()
    }

    private fun decodeImage(file: File, expectedMime: String): Bitmap {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            if (info.mimeType != expectedMime) throw unsupported()
            val (width, height) = dimensions(info.size.width, info.size.height, enlarge = false)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(width, height)
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            decoder.setOnPartialImageListener { false }
            // Transparent PNG/WebP documents are read against the same white preview.
            decoder.setPostProcessor { canvas ->
                canvas.drawColor(Color.WHITE, PorterDuff.Mode.DST_OVER)
                PixelFormat.OPAQUE
            }
        }
    }

    private fun renderPdf(file: File, pageIndex: Int): DecodedPage {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Throwable) {
            descriptor.close()
            when (error) {
                is SecurityException -> throw LocalDocumentReadException("Ce PDF est protégé par un mot de passe ou des restrictions. Choisissez une copie PDF déverrouillée.")
                is OutOfMemoryError -> throw error
                is CancellationException -> throw error
                else -> throw LocalDocumentReadException("Ce PDF est endommagé ou ne peut pas être ouvert sur cet appareil.")
            }
        }
        var ownedBitmap: Bitmap? = null
        try {
            val result = renderer.use { pdf ->
                if (pdf.pageCount !in 1..MAX_PAGES)
                    throw LocalDocumentReadException("Choisissez un PDF contenant entre 1 et 20 pages.")
                if (pageIndex !in 0 until pdf.pageCount)
                    throw LocalDocumentReadException("Cette page n’existe pas dans le document (${pdf.pageCount} pages).")
                pdf.openPage(pageIndex).use { page ->
                    val (width, height) = dimensions(page.width, page.height, enlarge = true)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    ownedBitmap = bitmap
                    bitmap.eraseColor(Color.WHITE)
                    val matrix = Matrix().apply { setScale(width.toFloat() / page.width, height.toFloat() / page.height) }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    DecodedPage(bitmap, pdf.pageCount, PrescriptionSource.PDF)
                }
            }
            ownedBitmap = null
            return result
        } finally {
            ownedBitmap?.recycle()
        }
    }

    private fun dimensions(width: Int, height: Int, enlarge: Boolean): Pair<Int, Int> {
        if (width <= 0 || height <= 0) throw LocalDocumentReadException("Les dimensions de ce document sont invalides.")
        val scale = min(
            if (enlarge) Double.POSITIVE_INFINITY else 1.0,
            min(MAX_SIDE.toDouble() / max(width, height), sqrt(MAX_PIXELS.toDouble() / (width.toDouble() * height))),
        )
        return max(1, floor(width * scale).toInt()) to max(1, floor(height * scale).toInt())
    }

    private suspend fun <T> awaitCompletion(task: Task<T>): T = suspendCoroutine { continuation ->
        task.addOnCompleteListener(DIRECT_EXECUTOR) { completed ->
            if (completed.isSuccessful) continuation.resume(completed.result)
            else if (completed.isCanceled) continuation.resumeWithException(CancellationException("Local text task cancelled"))
            else continuation.resumeWithException(completed.exception ?: IOException("Local OCR failed"))
        }
    }

    private data class TextPiece(val text: String, val bounds: Rect)

    /** Geometry orders rows and their words; it never infers prescription values or labels. */
    private fun spatialRows(recognized: Text): String {
        val pieces = mutableListOf<TextPiece>()
        for (block in recognized.textBlocks) for (line in block.lines) {
            val bounds = line.boundingBox ?: return recognized.text.trim()
            val words = if (line.elements.isNotEmpty() && line.elements.all { it.boundingBox != null })
                line.elements.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text }
            else line.text
            if (words.isNotBlank()) pieces.add(TextPiece(words.replace(Regex("\\s+"), " ").trim(), Rect(bounds)))
        }
        if (pieces.isEmpty()) return recognized.text.trim()
        val rows = mutableListOf<MutableList<TextPiece>>()
        for (piece in pieces.sortedWith(compareBy<TextPiece> { it.bounds.exactCenterY() }.thenBy { it.bounds.left })) {
            val row = rows.lastOrNull()
            val anchor = row?.firstOrNull()
            val sameRow = anchor != null &&
                abs(piece.bounds.exactCenterY() - anchor.bounds.exactCenterY()) <=
                min(piece.bounds.height(), anchor.bounds.height()).coerceAtLeast(1) * 0.45f
            if (sameRow) row!!.add(piece) else rows.add(mutableListOf(piece))
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.bounds.left }.joinToString(" ") { it.text } }.trim()
    }

    private fun tooLarge() = LocalDocumentReadException("Ce fichier dépasse 20 Mo. Choisissez une image ou un PDF moins volumineux.")
    private fun unsupported() = LocalDocumentReadException("Format non pris en charge. Choisissez une image JPEG, PNG ou WebP, ou un PDF.")

    companion object {
        /** Optional app-start cleanup; serialized with reads, so no active copy is removed. */
        suspend fun clearAbandonedTemporaryCopies(context: Context) {
            READ_MUTEX.withLock {
                withContext(Dispatchers.IO) { LocalDocumentReader(context).preparePrivateCache(forceCleanup = true) }
            }
        }

        private const val MAX_BYTES = 20L * 1024 * 1024
        private const val MAX_SIDE = 2048
        private const val MAX_PIXELS = 4_000_000
        private const val MAX_PAGES = 20
        private const val TEMP_PREFIX = "prescription-import-"
        private const val CACHE_DIRECTORY = "local-document-reader"
        private val READ_MUTEX = Mutex()
        // Only accessed while READ_MUTEX is held. No per-read instance can purge an active copy.
        private val INITIALIZED_CACHE_PATHS = mutableSetOf<String>()
        private val MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "application/pdf")
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        private val DIRECT_EXECUTOR = Executor { command -> command.run() }
    }
}
