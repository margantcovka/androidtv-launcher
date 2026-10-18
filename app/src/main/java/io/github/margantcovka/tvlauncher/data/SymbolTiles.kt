package io.github.margantcovka.tvlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.margantcovka.tvlauncher.R

/** Плитки без баннера (входы, настройки): тёмный градиент, белый значок и подпись, как в tvOS. */
class SymbolTiles(private val context: Context, private val spec: TileSpec) {

    private val typeface = ResourcesCompat.getFont(context, R.font.inter_display_semibold)
    private val cache = HashMap<String, LauncherTile>()

    fun tile(key: String, title: String, section: Section, action: TileAction, @DrawableRes icon: Int, dimmed: Boolean = false): LauncherTile {
        val version = if (dimmed) 1L else 0L
        cache[key]?.let { if (it.version == version && it.title == title) return it }
        val content = draw(title, icon, dimmed)
        val image = Images.roundedTile(content, spec).apply { prepareToDraw() }
        return LauncherTile(key, title, section, action, image.asImageBitmap(), Images.shelfPixels(content), version, labelInImage = true)
            .also { cache[key] = it }
    }

    private fun draw(title: String, @DrawableRes icon: Int, dimmed: Boolean): Bitmap {
        val w = spec.widthPx
        val h = spec.heightPx
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val top = if (dimmed) 0xFF242426.toInt() else 0xFF48484C.toInt()
        val bottom = if (dimmed) 0xFF1A1A1C.toInt() else 0xFF2C2C2E.toInt()
        canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP) })

        val ink = if (dimmed) 0x80FFFFFF.toInt() else 0xFFFFFFFF.toInt()
        val iconSize = (h * 0.34f).toInt()
        val drawable = ResourcesCompat.getDrawable(context.resources, icon, null)!!.mutate().apply { setTint(ink) }
        val iconBitmap = drawable.toBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        canvas.drawBitmap(iconBitmap, (w - iconSize) / 2f, h * 0.2f, Paint(Paint.FILTER_BITMAP_FLAG))

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = h * 0.13f
            textAlign = Paint.Align.CENTER
            typeface = this@SymbolTiles.typeface
        }
        val label = ellipsize(title, text, w * 0.86f)
        canvas.drawText(label, w / 2f, h * 0.8f, text)
        return out
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
