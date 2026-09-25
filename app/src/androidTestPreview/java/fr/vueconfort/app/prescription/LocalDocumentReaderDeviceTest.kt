package fr.vueconfort.app.prescription

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.vueconfort.app.model.PrescriptionSource
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic documents only: no application profile, personal document or URI store is read. */
@RunWith(AndroidJUnit4::class)
class LocalDocumentReaderDeviceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun bundledModelReadsSyntheticPngAndKeepsSpatialOdOgRows() = runBlocking {
        val source = png()
        try {
            val result = LocalDocumentReader(context).read(Uri.fromFile(source))
            try {
                assertEquals(PrescriptionSource.IMAGE, result.source)
                assertEquals(1, result.pageCount)
                assertEquals(0, result.pageIndex)
                assertTrue(result.text.contains("OD"))
                assertTrue(result.text.contains("OG"))
                val od = result.text.lineSequence().first { Regex("\\bOD\\b").containsMatchIn(it) }
                val og = result.text.lineSequence().first { Regex("\\bOG\\b").containsMatchIn(it) }
                assertTrue(od, od.contains("SPH") && od.contains("CYL") && od.contains("90"))
                assertTrue(og, og.contains("SPH") && og.contains("CYL") && og.contains("80"))
                val draft = PrescriptionDocumentParser.parse(result.text)
                assertTrue(draft.warnings.joinToString(), draft.canPrefill)
                assertEquals(1.25f, draft.rightEye.sphere ?: Float.NaN, 0f)
                assertEquals(2.00f, draft.leftEye.sphere ?: Float.NaN, 0f)
                assertEquals(-0.50f, draft.rightEye.cylinder ?: Float.NaN, 0f)
                assertEquals(-0.50f, draft.leftEye.cylinder ?: Float.NaN, 0f)
                assertEquals(90, draft.rightEye.axisDegrees ?: -1)
                assertEquals(80, draft.leftEye.axisDegrees ?: -1)
                assertEquals(2.00f, draft.rightEye.addition ?: Float.NaN, 0f)
                assertEquals(2.00f, draft.leftEye.addition ?: Float.NaN, 0f)
                assertFalse(draft.toUnconfirmedPrescription(result.source).confirmedByUser)
                assertPreviewBounded(result.preview)
                assertFalse(result.preview.isRecycled)
                assertNoTemporaryCopy()
            } finally { result.preview.recycle() }
        } finally { source.delete() }
    }

    @Test
    fun pdfReadsOnlyTheChosenPageAndReturnsItsCount() = runBlocking {
        val source = pdf(2)
        try {
            val result = LocalDocumentReader(context).read(Uri.fromFile(source), pageIndex = 1)
            try {
                assertEquals(PrescriptionSource.PDF, result.source)
                assertEquals(2, result.pageCount)
                assertEquals(1, result.pageIndex)
                assertTrue(result.text, result.text.contains("PAGE GAUCHE"))
                assertFalse(result.text, result.text.contains("PAGE DROITE"))
                assertTrue(result.text, result.text.contains("SPH") && result.text.contains("CYL") && result.text.contains("AXE"))
                val draft = PrescriptionDocumentParser.parse(result.text)
                if (Regex("\\bOG\\b").containsMatchIn(result.text)) {
                    assertTrue(draft.warnings.joinToString(), draft.canPrefill)
                    assertEquals(1.25f, draft.leftEye.sphere ?: Float.NaN, 0f)
                    assertEquals(-0.50f, draft.leftEye.cylinder ?: Float.NaN, 0f)
                    assertEquals(90, draft.leftEye.axisDegrees ?: -1)
                    assertEquals(2.00f, draft.leftEye.addition ?: Float.NaN, 0f)
                } else {
                    // Real OCR may read the letter O as zero. Do not silently repair the eye.
                    assertFalse(draft.canPrefill)
                    assertFalse(draft.rightEye.hasValues)
                    assertFalse(draft.leftEye.hasValues)
                }
                assertPreviewBounded(result.preview)
                assertNoTemporaryCopy()
            } finally { result.preview.recycle() }
        } finally { source.delete() }
    }

    @Test
    fun oversizedDocumentsAndExcessPdfPagesHaveFrenchErrorsAndNoTempCopy() = runBlocking {
        val oversized = File.createTempFile("synthetic-reader-large-", ".png", context.cacheDir)
        val tooManyPages = pdf(21)
        try {
            RandomAccessFile(oversized, "rw").use { it.setLength(20L * 1024 * 1024 + 1) }
            try {
                LocalDocumentReader(context).read(Uri.fromFile(oversized))
                fail("The size limit must reject this synthetic file")
            } catch (error: LocalDocumentReadException) { assertTrue(error.message.orEmpty().contains("20 Mo")) }
            try {
                LocalDocumentReader(context).read(Uri.fromFile(tooManyPages))
                fail("The page limit must reject this synthetic document")
            } catch (error: LocalDocumentReadException) { assertTrue(error.message.orEmpty().contains("20 pages")) }
            assertNoTemporaryCopy()
        } finally { oversized.delete(); tooManyPages.delete() }
    }

    @Test
    fun cancellationOrCompletionLeavesNoTemporaryDocument() = runBlocking {
        val source = png()
        var completed: DocumentReadResult? = null
        try {
            val job = async(Dispatchers.Default) {
                LocalDocumentReader(context).read(Uri.fromFile(source)).also { completed = it }
            }
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!job.isCompleted && temporaryCopies().isEmpty() && System.nanoTime() < deadline) delay(1)
            job.cancelAndJoin()
            assertNoTemporaryCopy()
        } finally { completed?.preview?.recycle(); source.delete() }
    }

    @Test
    fun malformedInputLeavesNoPrivateTemporaryCopy() = runBlocking {
        val source = File.createTempFile("synthetic-reader-invalid-", ".png", context.cacheDir)
        try {
            // A PNG signature with an incomplete image must fail during decoding.
            source.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0))
            try {
                LocalDocumentReader(context).read(Uri.fromFile(source))
                fail("The malformed synthetic image must be rejected")
            } catch (error: LocalDocumentReadException) { assertFalse(error.message.isNullOrBlank()) }
            assertNoTemporaryCopy()
        } finally { source.delete() }
    }

    @Test
    fun startupCleanupRemovesOnlyKnownSyntheticOrphanCopies() = runBlocking {
        val directory = File(context.cacheDir, "local-document-reader").apply { mkdirs() }
        val dedicated = File.createTempFile("prescription-import-orphan-", ".tmp", directory)
        val legacy = File.createTempFile("prescription-import-orphan-", ".tmp", context.cacheDir)
        val unrelated = File.createTempFile("synthetic-reader-unrelated-", ".tmp", directory)
        try {
            dedicated.writeText("synthetic orphan")
            legacy.writeText("synthetic orphan")
            unrelated.writeText("synthetic unrelated cache")
            LocalDocumentReader.clearAbandonedTemporaryCopies(context)
            assertFalse(dedicated.exists())
            assertFalse(legacy.exists())
            assertTrue(unrelated.exists())
        } finally { dedicated.delete(); legacy.delete(); unrelated.delete() }
    }

    private fun png(): File {
        val source = File.createTempFile("synthetic-reader-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(1800, 1000, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 42f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            canvas.drawText("DOCUMENT SYNTHETIQUE POUR TEST", 80f, 100f, paint)
            fun row(label: String, sphere: String, axis: String, y: Float) {
                canvas.drawText(label, 80f, y, paint)
                canvas.drawText("SPH $sphere", 240f, y, paint)
                canvas.drawText("CYL -0.50", 640f, y, paint)
                canvas.drawText("AXE $axis", 1020f, y, paint)
                canvas.drawText("ADD +2.00", 1380f, y, paint)
            }
            row("OD", "+1.25", "90", 300f)
            row("OG", "+2.00", "80", 470f)
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return source
        } finally { bitmap.recycle() }
    }

    private fun pdf(pages: Int): File {
        val source = File.createTempFile("synthetic-reader-", ".pdf", context.cacheDir)
        val document = PdfDocument()
        try {
            repeat(pages) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(900, 650, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 25f; typeface = Typeface.DEFAULT }
                page.canvas.drawText("DOCUMENT SYNTHETIQUE POUR TEST", 50f, 100f, paint)
                page.canvas.drawText(if (index == 0) "PAGE DROITE" else "PAGE GAUCHE", 50f, 150f, paint)
                val label = if (index == 0) "OD" else "OG"
                page.canvas.drawText("$label SPH +1.25 CYL -0.50 AXE 90 ADD +2.00", 50f, 230f, paint)
                document.finishPage(page)
            }
            source.outputStream().use { document.writeTo(it) }
        } finally { document.close() }
        return source
    }

    private fun assertPreviewBounded(bitmap: Bitmap) {
        assertTrue(bitmap.width <= 2048 && bitmap.height <= 2048)
        assertTrue(bitmap.width.toLong() * bitmap.height <= 4_000_000L)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
    }

    private fun temporaryCopies() = listOf(context.cacheDir, File(context.cacheDir, "local-document-reader"))
        .flatMap { it.listFiles()?.filter { file -> file.name.startsWith("prescription-import-") }.orEmpty() }
    private fun assertNoTemporaryCopy() = assertTrue("Reader temporary files must be removed", temporaryCopies().isEmpty())
}
