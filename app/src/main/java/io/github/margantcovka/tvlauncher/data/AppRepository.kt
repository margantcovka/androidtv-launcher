package io.github.margantcovka.tvlauncher.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Список TV-приложений из PackageManager и их запуск. */
class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    fun load(): List<TvApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return queryActivities(intent)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName to it.activityInfo.name }
            .mapNotNull { info ->
                runCatching { toApp(info) }
                    .onFailure { Log.w(TAG, "skip ${info.activityInfo.packageName}", it) }
                    .getOrNull()
            }
            .sortedBy { it.title.lowercase() }
    }

    fun launch(app: TvApp) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "launch ${app.key}", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "launch ${app.key}", e)
        }
    }

    private fun queryActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private fun toApp(info: ResolveInfo): TvApp {
        val activity = info.activityInfo
        val component = ComponentName(activity.packageName, activity.name)
        val title = info.loadLabel(pm).toString().trim().ifEmpty { activity.packageName }
        val banner: Drawable? = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)

        return if (banner != null) {
            val art = banner.toBitmap(BANNER_W, BANNER_H, Bitmap.Config.ARGB_8888)
            TvApp(component, title, art.asImageBitmap(), isBanner = true, edgeColors = null, backdrop = Images.backdrop(art).asImageBitmap())
        } else {
            val icon = info.loadIcon(pm).toBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            val edges = Images.edgeColors(icon)
            TvApp(component, title, icon.asImageBitmap(), isBanner = false, edgeColors = edges, backdrop = Images.backdrop(iconTile(icon, edges)).asImageBitmap())
        }
    }

    /** Иконка посередине широкой плитки, по бокам цвета её краёв — источник для размытого фона. */
    private fun iconTile(icon: Bitmap, edges: Pair<Int, Int>?): Bitmap {
        val tile = Bitmap.createBitmap(BANNER_W, BANNER_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tile)
        val paint = Paint()
        paint.color = edges?.first ?: FALLBACK_TILE
        canvas.drawRect(0f, 0f, BANNER_W / 2f, BANNER_H.toFloat(), paint)
        paint.color = edges?.second ?: FALLBACK_TILE
        canvas.drawRect(BANNER_W / 2f, 0f, BANNER_W.toFloat(), BANNER_H.toFloat(), paint)
        val size = BANNER_H * 0.8f
        val left = (BANNER_W - size) / 2f
        val top = (BANNER_H - size) / 2f
        canvas.drawBitmap(icon, null, android.graphics.RectF(left, top, left + size, top + size), Paint(Paint.FILTER_BITMAP_FLAG))
        return tile
    }

    private companion object {
        const val TAG = "AppRepository"
        const val BANNER_W = 480
        const val BANNER_H = 270
        const val ICON_SIZE = 192
        const val FALLBACK_TILE = 0xFF2C2C2E.toInt()
    }
}
