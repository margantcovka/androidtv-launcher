package io.github.margantcovka.tvlauncher.data

import android.content.ComponentName
import android.content.Context
import io.github.margantcovka.tvlauncher.R

/** Собирает все плитки главного экрана и применяет порядок и скрытие из [LauncherPrefs]. */
class LauncherRepository(context: Context, spec: TileSpec) {

    val prefs = LauncherPrefs(context)
    private val apps = AppSource(context, spec)
    private val symbols = SymbolTiles(context, spec)
    private val inputs = InputSource(context, symbols)

    /** Последний набор плиток в порядке по умолчанию: настройки переставляют его без перечитывания системы. */
    private var tiles: List<LauncherTile> = emptyList()

    fun load(): Catalog {
        val activities = apps.launcherActivities()
        // ТВ-приложение производителя становится плиткой «Телевизор» в источниках, а не приложением.
        val liveTv = activities.firstOrNull { it.activityInfo.packageName in TV_APPS }
            ?.let { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        tiles = apps.load(activities, exclude = TV_APPS) + inputs.load(liveTv) + more()
        return arrange()
    }

    fun arrange(): Catalog = Catalog.arrange(tiles, prefs)

    private fun more(): List<LauncherTile> {
        val list = mutableListOf(
            symbols.tile("more:launcher", "Главный экран", Section.MORE, TileAction.LauncherSettings, R.drawable.ic_apps),
            symbols.tile("more:settings", "Настройки", Section.MORE, TileAction.SystemSettings, R.drawable.ic_settings),
        )
        // Если стандартный экран отключён (чтобы Home открывал этот лаунчер), плитка ведёт
        // на его страницу в настройках с кнопкой «Включить» — так можно вернуться без компьютера.
        when (apps.isEnabled(STOCK_HOME.packageName)) {
            true -> list += symbols.tile("more:stockhome", "Стандартный экран", Section.MORE, TileAction.Activity(STOCK_HOME), R.drawable.ic_home)
            false -> list += symbols.tile("more:stockhome", "Стандартный экран", Section.MORE, TileAction.AppDetails(STOCK_HOME.packageName), R.drawable.ic_home)
            null -> Unit
        }
        return list
    }

    private companion object {
        val TV_APPS = setOf("com.mediatek.wwtv.tvcenter", "com.android.tv", "com.google.android.tv")
        val STOCK_HOME = ComponentName("com.google.android.tvlauncher", "com.google.android.tvlauncher.MainActivity")
    }
}
