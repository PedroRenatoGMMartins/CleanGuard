package com.cleanguard.app

import com.cleanguard.app.core.util.Formatters
import com.cleanguard.app.domain.storage.DeviceStorage
import com.cleanguard.app.domain.storage.FileCategory
import com.cleanguard.app.domain.storage.FileItem
import com.cleanguard.app.domain.storage.FileSource
import com.cleanguard.app.domain.storage.MediaAccess
import com.cleanguard.app.domain.storage.StorageCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testes de cálculo e classificação de armazenamento. */
class StorageCalculationTest {

    private val mb = StorageCalculator.MB

    private fun file(id: String, name: String, sizeMb: Long, mime: String? = null, location: String = "DCIM/", date: Long = 0) =
        FileItem(
            id = id,
            name = name,
            location = location,
            sizeBytes = sizeMb * mb,
            mimeType = mime,
            category = StorageCalculator.categorize(name, mime, location),
            dateModified = date,
            source = FileSource.DEMO,
        )

    @Test
    fun `fracao usada e espaco usado`() {
        val device = DeviceStorage(totalBytes = 128_000_000_000, freeBytes = 32_000_000_000)
        assertEquals(96_000_000_000, device.usedBytes)
        assertEquals(0.75f, device.usedFraction, 0.0001f)
        assertEquals(0f, StorageCalculator.usedFraction(0, 0), 0f)
        // Livre maior que total (dado inconsistente) não gera valor negativo.
        assertEquals(0f, StorageCalculator.usedFraction(100, 200), 0f)
    }

    @Test
    fun `categorizacao por mime e extensao`() {
        assertEquals(FileCategory.IMAGE, StorageCalculator.categorize("a.jpg", "image/jpeg"))
        assertEquals(FileCategory.VIDEO, StorageCalculator.categorize("a.mp4", "video/mp4"))
        assertEquals(FileCategory.AUDIO, StorageCalculator.categorize("a.mp3", "audio/mpeg"))
        assertEquals(FileCategory.DOCUMENT, StorageCalculator.categorize("a.pdf", null))
        assertEquals(FileCategory.ARCHIVE, StorageCalculator.categorize("a.zip", null))
        assertEquals(FileCategory.APK, StorageCalculator.categorize("app.apk", null))
        assertEquals(FileCategory.TEMPORARY, StorageCalculator.categorize("video.mp4.crdownload", null))
        assertEquals(FileCategory.TEMPORARY, StorageCalculator.categorize("~\$relatorio.docx", null))
        assertEquals(FileCategory.TEMPORARY, StorageCalculator.categorize("thumb.jpg", "image/jpeg", "DCIM/.thumbnails/"))
        assertEquals(FileCategory.OTHER, StorageCalculator.categorize("dados.bin", null))
    }

    @Test
    fun `arquivos grandes por categoria`() {
        assertTrue(StorageCalculator.isLarge(file("1", "v.mp4", 60, "video/mp4")))
        assertFalse(StorageCalculator.isLarge(file("2", "v.mp4", 20, "video/mp4")))
        assertTrue(StorageCalculator.isLarge(file("3", "a.mp3", 15, "audio/mpeg")))
        assertTrue(StorageCalculator.isLarge(file("4", "x.bin", 150)))
    }

    @Test
    fun `duplicatas exigem mesmo tamanho e mesmo conteudo`() {
        val a = file("a", "a.jpg", 5, "image/jpeg", date = 1)
        val b = file("b", "b.jpg", 5, "image/jpeg", date = 2)
        val c = file("c", "c.jpg", 5, "image/jpeg", date = 3) // mesmo tamanho, conteúdo diferente
        val d = file("d", "d.jpg", 7, "image/jpeg")           // tamanho único
        val hashes = mapOf("a" to "h1", "b" to "h1", "c" to "h2", "d" to "h1")

        val groups = StorageCalculator.groupDuplicates(listOf(a, b, c, d)) { hashes[it.id] }

        assertEquals(1, groups.size)
        assertEquals(setOf("a", "b"), groups[0].files.map { it.id }.toSet())
        assertEquals(5 * mb, groups[0].wastedBytes)
        // Sugestão: manter o mais antigo (a) e apagar a cópia (b).
        assertEquals(listOf("b"), groups[0].suggestedToDelete.map { it.id })
    }

    @Test
    fun `arquivo ilegivel nao entra em grupo de duplicatas`() {
        val a = file("a", "a.jpg", 5, "image/jpeg")
        val b = file("b", "b.jpg", 5, "image/jpeg")
        val groups = StorageCalculator.groupDuplicates(listOf(a, b)) { if (it.id == "a") "h" else null }
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `soma nao conta o mesmo arquivo duas vezes`() {
        val a = file("a", "a.mp4", 100, "video/mp4")
        assertEquals(100 * mb, StorageCalculator.totalBytes(listOf(a, a)))
    }

    @Test
    fun `relatorio separa categorias e estima o recuperavel`() {
        val files = listOf(
            file("v", "grande.mp4", 300, "video/mp4"),
            file("t", "x.tmp", 4, location = "Documents/"),
            file("dl", "musica.mp3", 12, "audio/mpeg", location = "Download/"),
            file("i1", "a.jpg", 5, "image/jpeg", date = 1),
            file("i2", "b.jpg", 5, "image/jpeg", date = 2),
        )
        val dupes = StorageCalculator.groupDuplicates(files.filter { it.category == FileCategory.IMAGE }) { "same" }
        val report = StorageCalculator.buildReport(DeviceStorage(1000 * mb, 100 * mb), MediaAccess.FULL, files, dupes, now = 0)

        assertEquals(listOf("v"), report.largeFiles.map { it.id })
        assertEquals(listOf("v"), report.largeVideos.map { it.id })
        assertEquals(listOf("dl"), report.downloads.map { it.id })
        assertEquals(listOf("dl"), report.largeAudios.map { it.id })
        assertEquals(listOf("t"), report.temporaryFiles.map { it.id })
        assertEquals(1, report.duplicateGroups.size)
        // temporário (4 MB) + cópia duplicada (5 MB)
        assertEquals(9 * mb, report.reclaimableEstimateBytes)
        assertEquals(5, report.scannedFiles)
    }

    @Test
    fun `estimativa do painel nunca soma valores negativos`() {
        assertEquals(30L, StorageCalculator.dashboardReclaimable(10, 20, -5))
    }

    @Test
    fun `formatacao de bytes`() {
        assertEquals("999 B", Formatters.bytes(999))
        assertEquals("1,5 KB", Formatters.bytes(1_500))
        assertEquals("250 MB", Formatters.bytes(250_000_000))
        assertEquals("1,2 GB", Formatters.bytes(1_200_000_000))
        assertEquals("—", Formatters.bytes(-1))
    }
}
