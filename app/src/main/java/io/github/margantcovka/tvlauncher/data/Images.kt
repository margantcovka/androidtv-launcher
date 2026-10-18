package io.github.margantcovka.tvlauncher.data

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Всё, что дорого рисовать на слабом GPU телевизора (скруглённые углы, тени, размытие),
 * готовится здесь один раз на CPU. На экране остаются только готовые картинки.
 */
object Images {

    /** Фон top shelf в пикселях: ширина экрана × высота области полки (960×420 dp). */
    const val SHELF_W = 64
    const val SHELF_H = 28

    /** Плитка со скруглёнными углами; содержимое обрезается по центру (cover). */
    fun roundedTile(content: Bitmap, spec: TileSpec): Bitmap {
        val out = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
        val scale = maxOf(spec.widthPx.toFloat() / content.width, spec.heightPx.toFloat() / content.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((spec.widthPx - content.width * scale) / 2f, (spec.heightPx - content.height * scale) / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        }
        Canvas(out).drawRoundRect(RectF(0f, 0f, spec.widthPx.toFloat(), spec.heightPx.toFloat()), spec.cornerPx, spec.cornerPx, paint)
        return out
    }

    /** Квадратная иконка посередине широкой плитки, по бокам — цвета её краёв. */
    fun iconContent(icon: Bitmap, width: Int, height: Int, fallback: Int): Bitmap {
        val edges = edgeColors(icon)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        paint.color = edges?.first ?: fallback
        canvas.drawRect(0f, 0f, width / 2f, height.toFloat(), paint)
        paint.color = edges?.second ?: fallback
        canvas.drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), paint)
        val size = height * if (edges != null) 1f else 0.72f
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        canvas.drawBitmap(icon, null, RectF(left, top, left + size, top + size), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Общая мягкая тень под плиткой в фокусе (размер плитки + поля под размытие). */
    fun tileShadow(widthPx: Int, heightPx: Int, cornerPx: Float, blurPx: Float): Pair<Bitmap, Int> {
        val margin = (blurPx * 2).toInt()
        val out = Bitmap.createBitmap(widthPx + 2 * margin, heightPx + 2 * margin, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 0, 0, 0)
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        val dy = blurPx * 0.5f
        Canvas(out).drawRoundRect(
            RectF(margin.toFloat(), margin + dy, (margin + widthPx).toFloat(), margin + heightPx + dy),
            cornerPx, cornerPx, paint,
        )
        return out to margin
    }

    fun tileSpec(widthPx: Int, heightPx: Int, cornerPx: Float, shadowBlurPx: Float): TileSpec {
        val (shadow, margin) = tileShadow(widthPx, heightPx, cornerPx, shadowBlurPx)
        return TileSpec(widthPx, heightPx, cornerPx, shadow.asImageBitmap(), margin)
    }

    /**
     * Сильно размытая, насыщенная и притемнённая копия для фона top shelf,
     * обрезанная по центру под пропорции области полки.
     */
    fun shelfPixels(source: Bitmap): IntArray {
        val cropH = minOf(source.height, source.width * SHELF_H / SHELF_W)
        var bmp = Bitmap.createBitmap(source, 0, (source.height - cropH) / 2, source.width, cropH)
        // Уменьшаем в несколько шагов: один большой шаг даёт рябь вместо усреднения.
        while (bmp.width / 2 >= SHELF_W * 2) {
            bmp = Bitmap.createScaledBitmap(bmp, bmp.width / 2, maxOf(1, bmp.height / 2), true)
        }
        bmp = Bitmap.createScaledBitmap(bmp, SHELF_W, SHELF_H, true)
        val px = IntArray(SHELF_W * SHELF_H)
        bmp.getPixels(px, 0, SHELF_W, 0, 0, SHELF_W, SHELF_H)
        repeat(3) {
            boxBlur(px, SHELF_W, SHELF_H, radius = 3, horizontal = true)
            boxBlur(px, SHELF_W, SHELF_H, radius = 3, horizontal = false)
        }
        for (i in px.indices) px[i] = tone(px[i])
        return px
    }

    /**
     * Средний цвет крайних столбцов иконки (средние 60% высоты, без скруглённых углов).
     * null, если края прозрачные — тогда фон плитки нейтральный.
     */
    private fun edgeColors(icon: Bitmap): Pair<Int, Int>? {
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
