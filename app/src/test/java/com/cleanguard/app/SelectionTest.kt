package com.cleanguard.app

import com.cleanguard.app.demo.DemoData
import com.cleanguard.app.domain.apps.AppListFilter
import com.cleanguard.app.domain.selection.Selection
import com.cleanguard.app.domain.storage.FileItem
import com.cleanguard.app.domain.storage.FileSource
import com.cleanguard.app.domain.storage.StorageCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testes de seleção múltipla de aplicativos e de arquivos. */
class SelectionTest {

    private val now = 1_780_000_000_000L
    private val self = "com.cleanguard.app"

    @Test
    fun `alternar selecionar e limpar`() {
        var sel = Selection<String>()
        sel = sel.toggle("a").toggle("b")
        assertEquals(2, sel.count)
        sel = sel.toggle("a")
        assertFalse(sel.isSelected("a"))
        assertTrue(sel.isSelected("b"))
        assertTrue(sel.clear().isEmpty)
    }

    @Test
    fun `selecionar todos respeita a regra de quem pode ser desinstalado`() {
        val apps = DemoData.apps(now).map { it.copy(isDemo = false) } +
            DemoData.apps(now).first().copy(packageName = "com.android.settings", isSystemApp = true, isDemo = false) +
            DemoData.apps(now).first().copy(packageName = self, isDemo = false)

        val sel = Selection<String>().selectAll(apps, { it.packageName }) { AppListFilter.canUninstall(it, self) }

        assertFalse(sel.isSelected("com.android.settings"))
        assertFalse(sel.isSelected(self))
        assertEquals(DemoData.apps(now).size, sel.count)
    }

    @Test
    fun `apps fictícios do modo demo nao podem ser desinstalados`() {
        val sel = Selection<String>().selectAll(DemoData.apps(now), { it.packageName }) { AppListFilter.canUninstall(it, self) }
        assertTrue(sel.isEmpty)
    }

    @Test
    fun `soma de tamanho dos apps selecionados`() {
        val apps = DemoData.apps(now)
        val sel = Selection<String>().select(apps[0].packageName).select(apps[1].packageName)
        assertEquals(apps[0].totalSizeBytes + apps[1].totalSizeBytes, sel.sumOf(apps, { it.packageName }, { it.totalSizeBytes }))
        assertEquals(2, sel.selectedItems(apps) { it.packageName }.size)
    }

    @Test
    fun `retainOnly remove apps desinstalados ou arquivos excluidos`() {
        val sel = Selection(setOf("a", "b", "c")).retainOnly(setOf("a", "c", "z"))
        assertEquals(setOf("a", "c"), sel.keys)
    }

    @Test
    fun `selecao de arquivos ignora itens que o provedor nao permite excluir`() {
        val files = DemoData.files(now) + FileItem(
            id = "content://tree/locked",
            name = "bloqueado.pdf",
            location = "Documents/",
            sizeBytes = 10,
            mimeType = "application/pdf",
            category = StorageCalculator.categorize("bloqueado.pdf", "application/pdf"),
            dateModified = 0,
            source = FileSource.DOCUMENT_TREE,
            canDelete = false,
        )
        val sel = Selection<String>().selectAll(files, { it.id }) { it.canDelete }
        assertEquals(DemoData.files(now).size, sel.count)
        assertFalse(sel.isSelected("content://tree/locked"))
        assertEquals(StorageCalculator.totalBytes(DemoData.files(now)), sel.sumOf(files, { it.id }, { it.sizeBytes }))
    }

    @Test
    fun `duplicatas sugeridas do modo demo preservam o original`() {
        val images = DemoData.files(now).filter { it.mimeType?.startsWith("image/") == true }
        val groups = StorageCalculator.groupDuplicates(images) { DemoData.demoFileHash(it) }
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].files.size)
        val sel = Selection<String>().selectAll(groups[0].suggestedToDelete, { it.id })
        assertEquals(2, sel.count)
        // O mais antigo (i1) é mantido.
        assertFalse(sel.isSelected("demo://i1"))
    }
}
