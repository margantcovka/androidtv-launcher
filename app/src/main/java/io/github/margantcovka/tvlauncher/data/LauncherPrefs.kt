package io.github.margantcovka.tvlauncher.data

import android.content.Context

/** Порядок и скрытые плитки. Ключи — [LauncherTile.key]. */
class LauncherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun order(): List<String> = prefs.getString(KEY_ORDER, null)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    fun setOrder(keys: List<String>) = prefs.edit().putString(KEY_ORDER, keys.joinToString("\n")).apply()

    fun resetOrder() = prefs.edit().remove(KEY_ORDER).apply()

    fun hidden(): Set<String> = prefs.getStringSet(KEY_HIDDEN, null)?.toSet() ?: emptySet()

    fun setHidden(key: String, hidden: Boolean) {
        val next = hidden().toMutableSet().apply { if (hidden) add(key) else remove(key) }
        prefs.edit().putStringSet(KEY_HIDDEN, next).apply()
    }

    private companion object {
        const val KEY_ORDER = "order"
        const val KEY_HIDDEN = "hidden"
    }
}

/** Все плитки в порядке показа (вместе со скрытыми) и множество скрытых. */
class Catalog(val all: List<LauncherTile>, val hidden: Set<String>) {

    fun visible(section: Section): List<LauncherTile> = all.filter { it.section == section && it.key !in hidden }

    /** Полный порядок ключей, где видимые плитки раздела стоят в [visibleKeys], а скрытые остались на местах. */
    fun orderWith(section: Section, visibleKeys: List<String>): List<String> {
        val queue = ArrayDeque(visibleKeys)
        return all.map { tile ->
            if (tile.section == section && tile.key !in hidden && queue.isNotEmpty()) queue.removeFirst() else tile.key
        }
    }

    companion object {
        /** Сохранённый порядок поверх порядка по умолчанию; новые плитки — в конце своего раздела. */
        fun arrange(tiles: List<LauncherTile>, prefs: LauncherPrefs): Catalog {
            val index = prefs.order().withIndex().associate { (i, key) -> key to i }
            val arranged = tiles.withIndex()
                .sortedWith(compareBy({ it.value.section.ordinal }, { index[it.value.key] ?: Int.MAX_VALUE }, { it.index }))
                .map { it.value }
            return Catalog(arranged, prefs.hidden())
        }
    }
}
