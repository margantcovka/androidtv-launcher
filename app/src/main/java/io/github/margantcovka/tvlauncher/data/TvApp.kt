package io.github.margantcovka.tvlauncher.data

import android.content.ComponentName
import androidx.compose.ui.graphics.ImageBitmap

/** Приложение на плитке. Все картинки готовятся один раз при загрузке списка. */
class TvApp(
    val component: ComponentName,
    val title: String,
    /** Готовая плитка со скруглёнными углами в размере плитки в фокусе. */
    val tile: ImageBitmap,
    /** Размытый фон top shelf: [Images.SHELF_W]×[Images.SHELF_H] пикселей ARGB. */
    val shelf: IntArray,
    /** lastUpdateTime пакета — по нему понимаем, что плитку пора перерисовать. */
    val updatedAt: Long,
) {
    val key: String get() = component.flattenToShortString()
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
