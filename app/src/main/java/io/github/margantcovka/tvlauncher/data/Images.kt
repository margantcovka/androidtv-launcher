package io.github.margantcovka.tvlauncher.data

import android.graphics.Bitmap
import android.graphics.Color

/** Обработка картинок, которая делается один раз на приложение, а не на каждом кадре. */
internal object Images {

    private const val BACKDROP_W = 64
    private const val BACKDROP_H = 36

    /**
     * Сильно размытая и притемнённая копия картинки. Живой blur на весь экран
     * тормозит на слабом GPU телевизора, а маленькая размытая картинка,
     * растянутая на экран, стоит как обычная отрисовка изображения.
     */
    fun backdrop(source: Bitmap): Bitmap {
        // Уменьшаем в несколько шагов: один большой шаг даёт рябь вместо усреднения.
        var bmp = source
        while (bmp.width / 2 >= BACKDROP_W * 2) {
            bmp = Bitmap.createScaledBitmap(bmp, bmp.width / 2, bmp.height / 2, true)
        }
        bmp = Bitmap.createScaledBitmap(bmp, BACKDROP_W, BACKDROP_H, true)

        val px = IntArray(BACKDROP_W * BACKDROP_H)
        bmp.getPixels(px, 0, BACKDROP_W, 0, 0, BACKDROP_W, BACKDROP_H)
        repeat(3) {
            boxBlur(px, BACKDROP_W, BACKDROP_H, radius = 3, horizontal = true)
            boxBlur(px, BACKDROP_W, BACKDROP_H, radius = 3, horizontal = false)
        }
        for (i in px.indices) px[i] = tone(px[i])
        return Bitmap.createBitmap(px, BACKDROP_W, BACKDROP_H, Bitmap.Config.ARGB_8888)
    }

    /**
     * Средний цвет крайних столбцов иконки (средние 60% высоты, без скруглённых углов).
     * null, если края прозрачные — тогда фон плитки нейтральный.
     */
    fun edgeColors(icon: Bitmap): Pair<Int, Int>? {
        val left = columnAverage(icon, (icon.width * 0.03f).toInt()) ?: return null
        val right = columnAverage(icon, (icon.width * 0.97f).toInt() - 1) ?: return null
        return left to right
    }

    private fun columnAverage(bmp: Bitmap, x: Int): Int? {
        val top = (bmp.height * 0.2f).toInt()
        val count = maxOf(1, (bmp.height * 0.6f).toInt())
        var r = 0; var g = 0; var b = 0; var n = 0
        for (y in top until top + count) {
            val c = bmp.getPixel(x, y)
            if (Color.alpha(c) < 200) continue
            r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++
        }
        if (n < count / 2) return null
        return Color.rgb(r / n, g / n, b / n)
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val line = IntArray(len)
        for (l in 0 until lines) {
            for (i in 0 until len) line[i] = px[if (horizontal) l * w + i else i * w + l]
            for (i in 0 until len) {
                var r = 0; var g = 0; var b = 0; var n = 0
                for (k in -radius..radius) {
                    val c = line[(i + k).coerceIn(0, len - 1)]
                    r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++
                }
                px[if (horizontal) l * w + i else i * w + l] = Color.rgb(r / n, g / n, b / n)
            }
        }
    }

    /** Чуть насыщеннее и темнее, как у фона top shelf в tvOS. */
    private fun tone(c: Int): Int {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        val gray = 0.299f * r + 0.587f * g + 0.114f * b
        fun ch(v: Int) = ((gray + (v - gray) * 1.4f) * 0.72f).toInt().coerceIn(0, 255)
        return Color.rgb(ch(r), ch(g), ch(b))
    }
}
