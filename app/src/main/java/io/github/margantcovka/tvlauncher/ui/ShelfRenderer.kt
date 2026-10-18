package io.github.margantcovka.tvlauncher.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.margantcovka.tvlauncher.data.Images

/**
 * Фон всего экрана одной непрозрачной картинкой 64×36: размытый фон полки, затухание
 * к цвету фона и плавная смена приложения смешиваются на CPU (2 тыс. пикселей —
 * доли миллисекунды). GPU рисует ровно один растянутый непрозрачный прямоугольник
 * вместо двух полупрозрачных картинок на весь экран, градиента и слоя прозрачности.
 */
class ShelfRenderer(private val background: Int) {

    private val w = Images.SHELF_W
    private val shelfRows = Images.SHELF_H
    private val rows = SCREEN_ROWS

    private val blank = IntArray(w * shelfRows) { background }
    private var from = blank
    private var to = blank
    /** Что было на экране на последнем кадре (без затухания), чтобы новая смена начиналась с него. */
    private val shown = blank.copyOf()
    private val out = IntArray(w * rows)

    /**
     * Сколько смешивать с фоном в каждой строке: градиент полки вниз, ниже полки — только фон.
     * Сверху тоже притемнено — там белым написаны приветствие и время.
     */
    private val rowFade = FloatArray(rows) { r ->
        if (r >= shelfRows) return@FloatArray 1f
        val y = (r + 0.5f) / shelfRows
        val down = if (y < 0.45f) 0.25f * y / 0.45f else 0.25f + 0.75f * (y - 0.45f) / 0.55f
        val top = if (y < 0.3f) 0.45f * (1f - y / 0.3f) else 0f
        maxOf(down, top)
    }

    // Два буфера по очереди: RenderThread может ещё загружать прошлый кадр, пока мы пишем новый.
    private val bitmaps = Array(2) { Bitmap.createBitmap(w, rows, Bitmap.Config.ARGB_8888).apply { setHasAlpha(false) } }
    private val images = bitmaps.map { it.asImageBitmap() }
    private var current = 0
    private var lastProgress = Float.NaN
    private var lastVisibility = Float.NaN

    fun retarget(next: IntArray?) {
        from = shown.copyOf()
        to = next ?: blank
        lastProgress = Float.NaN
    }

    /** [progress] — доля нового фона (0..1), [visibility] — насколько видна полка (0..1). */
    fun render(progress: Float, visibility: Float): ImageBitmap {
        if (progress == lastProgress && visibility == lastVisibility) return images[current]
        lastProgress = progress
        lastVisibility = visibility

        val bgR = Color.red(background); val bgG = Color.green(background); val bgB = Color.blue(background)
        for (i in shown.indices) shown[i] = lerpColor(from[i], to[i], progress)
        for (r in 0 until rows) {
            // Полка уходит: смешиваем строку с фоном сильнее.
            val keep = (1f - rowFade[r]) * visibility
            for (x in 0 until w) {
                val idx = r * w + x
                if (r >= shelfRows || keep <= 0f) { out[idx] = background; continue }
                val c = shown[(r * w) + x]
                out[idx] = Color.rgb(
                    (bgR + (Color.red(c) - bgR) * keep).toInt(),
                    (bgG + (Color.green(c) - bgG) * keep).toInt(),
                    (bgB + (Color.blue(c) - bgB) * keep).toInt(),
                )
            }
        }
        current = 1 - current
        bitmaps[current].setPixels(out, 0, w, 0, 0, w, rows)
        return images[current]
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
        )
    }

    private companion object {
        /** 36 строк на 540 dp; полка (420 dp) — первые 28. */
        const val SCREEN_ROWS = 36
    }
}
