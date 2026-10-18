package io.github.margantcovka.tvlauncher.data

import android.content.ComponentName
import androidx.compose.ui.graphics.ImageBitmap

/** Раздел главного экрана: приложения, потом источники, потом «Ещё». */
enum class Section { APPS, SOURCES, MORE }

/** Что делает плитка по OK. */
sealed interface TileAction {
    data class App(val component: ComponentName) : TileAction
    /** Вход ТВ (HDMI, AV) по id из TvInputManager. */
    data class Input(val inputId: String) : TileAction
    /** Явная activity: эфирное ТВ, стандартный домашний экран. */
    data class Activity(val component: ComponentName) : TileAction
    data object SystemSettings : TileAction
    /** Системная страница приложения — там можно снова включить отключённый стандартный экран. */
    data class AppDetails(val packageName: String) : TileAction
    data object LauncherSettings : TileAction
}

/** Плитка главного экрана. Все картинки готовятся один раз при загрузке. */
class LauncherTile(
    val key: String,
    val title: String,
    val section: Section,
    val action: TileAction,
    /** Готовая плитка со скруглёнными углами в размере плитки в фокусе. */
    val image: ImageBitmap,
    /** Размытый фон top shelf: [Images.SHELF_W]×[Images.SHELF_H] пикселей ARGB. */
    val shelf: IntArray,
    /** Меняется, когда плитку надо перерисовать (обновление пакета, подключение входа). */
    val version: Long,
    /** Название уже нарисовано на картинке (входы, «Ещё»): подпись под плиткой не нужна. */
    val labelInImage: Boolean = false,
) {
    /** Плитку можно скрыть: свои настройки и системные всегда остаются, иначе до них не добраться. */
    val hideable: Boolean
        get() = action !is TileAction.LauncherSettings && action !is TileAction.SystemSettings
}

/**
 * Размеры плитки в пикселях при максимальном (фокусном) масштабе и общая тень.
 * Плитка в фокусе рисуется 1:1, без растягивания.
 */
class TileSpec(
    val widthPx: Int,
    val heightPx: Int,
    val cornerPx: Float,
    val shadow: ImageBitmap,
    /** Отступ тени от краёв плитки в пикселях картинки тени. */
    val shadowMarginPx: Int,
)
