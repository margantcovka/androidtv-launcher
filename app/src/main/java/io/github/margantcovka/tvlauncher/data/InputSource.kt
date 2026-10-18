package io.github.margantcovka.tvlauncher.data

import android.content.ComponentName
import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log
import io.github.margantcovka.tvlauncher.R

/**
 * Источники: эфирное ТВ (если есть тюнер и приложение ТВ) и внешние входы из TvInputManager.
 * Переключение на вход — ACTION_VIEW с passthrough-URI, его обрабатывает ТВ-приложение производителя.
 */
class InputSource(private val context: Context, private val symbols: SymbolTiles) {

    private val tv: TvInputManager? = context.getSystemService(TvInputManager::class.java)

    fun load(liveTv: ComponentName?): List<LauncherTile> {
        val inputs = runCatching { tv?.tvInputList.orEmpty() }
            .onFailure { Log.w(TAG, "tv inputs", it) }
            .getOrDefault(emptyList())
        val tiles = mutableListOf<LauncherTile>()
        if (liveTv != null && inputs.any { it.type == TvInputInfo.TYPE_TUNER }) {
            tiles += symbols.tile("source:livetv", "Телевизор", Section.SOURCES, TileAction.Activity(liveTv), R.drawable.ic_tv)
        }
        inputs
            .filter { it.type != TvInputInfo.TYPE_TUNER && it.type != TvInputInfo.TYPE_OTHER && it.parentId == null && !it.isHidden(context) }
            .sortedWith(compareBy({ typeOrder(it.type) }, { it.id }))
            .forEach { info ->
                val title = (info.loadCustomLabel(context) ?: info.loadLabel(context)).toString().ifBlank { info.id }
                val disconnected = runCatching { tv?.getInputState(info.id) == TvInputManager.INPUT_STATE_DISCONNECTED }.getOrDefault(false)
                val icon = if (info.type == TvInputInfo.TYPE_HDMI) R.drawable.ic_hdmi else R.drawable.ic_input
                tiles += symbols.tile("source:" + info.id, title, Section.SOURCES, TileAction.Input(info.id), icon, dimmed = disconnected)
            }
        return tiles
    }

    private fun typeOrder(type: Int) = when (type) {
        TvInputInfo.TYPE_HDMI -> 0
        TvInputInfo.TYPE_DISPLAY_PORT, TvInputInfo.TYPE_DVI, TvInputInfo.TYPE_VGA -> 1
        else -> 2
    }

    private companion object {
        const val TAG = "InputSource"
    }
}
