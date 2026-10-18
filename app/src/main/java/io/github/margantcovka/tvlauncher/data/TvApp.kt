package io.github.margantcovka.tvlauncher.data

import android.content.ComponentName
import androidx.compose.ui.graphics.ImageBitmap

/** Приложение на плитке. Картинки готовятся один раз при загрузке списка. */
class TvApp(
    val component: ComponentName,
    val title: String,
    /** Баннер 16:9 или, если баннера нет, иконка приложения. */
    val art: ImageBitmap,
    val isBanner: Boolean,
    /** Цвета левого и правого края иконки (ARGB), чтобы квадратная иконка тянулась на всю ширину плитки. */
    val edgeColors: Pair<Int, Int>?,
    /** Маленькая заранее размытая картинка для top shelf. */
    val backdrop: ImageBitmap,
) {
    val key: String get() = component.flattenToShortString()
}
