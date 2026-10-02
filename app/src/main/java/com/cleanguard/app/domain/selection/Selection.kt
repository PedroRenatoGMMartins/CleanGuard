package com.cleanguard.app.domain.selection

/**
 * Seleção múltipla imutável, usada para apps (chave = nome do pacote) e arquivos (chave = URI).
 * Cada operação devolve uma nova instância — ideal para StateFlow/Compose.
 */
data class Selection<K>(val keys: Set<K> = emptySet()) {

    val count: Int get() = keys.size
    val isEmpty: Boolean get() = keys.isEmpty()

    fun isSelected(key: K): Boolean = key in keys

    fun toggle(key: K): Selection<K> =
        if (key in keys) copy(keys = keys - key) else copy(keys = keys + key)

    fun select(key: K): Selection<K> = copy(keys = keys + key)

    fun deselect(key: K): Selection<K> = copy(keys = keys - key)

    /** Seleciona todos os itens informados, respeitando a regra de quais podem ser selecionados. */
    fun <T> selectAll(items: Collection<T>, keyOf: (T) -> K, isSelectable: (T) -> Boolean = { true }): Selection<K> =
        copy(keys = keys + items.filter(isSelectable).map(keyOf))

    /** Remove da seleção as chaves que não existem mais (ex.: app desinstalado, arquivo apagado). */
    fun retainOnly(validKeys: Set<K>): Selection<K> = copy(keys = keys intersect validKeys)

    fun clear(): Selection<K> = Selection()

    /** Soma um valor (ex.: tamanho em bytes) dos itens selecionados. */
    fun <T> sumOf(items: Collection<T>, keyOf: (T) -> K, valueOf: (T) -> Long): Long =
        items.filter { keyOf(it) in keys }.sumOf(valueOf)

    fun <T> selectedItems(items: Collection<T>, keyOf: (T) -> K): List<T> =
        items.filter { keyOf(it) in keys }
}
