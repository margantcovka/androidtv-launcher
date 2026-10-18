package io.github.margantcovka.tvlauncher.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Список TV-приложений из PackageManager и их запуск. */
class AppRepository(private val context: Context, private val spec: TileSpec) {

    private val pm: PackageManager = context.packageManager

    /** Готовые плитки по ключу компонента: при возврате на экран не перерисовываем то, что не менялось. */
    private val cache = HashMap<String, TvApp>()

    fun load(): List<TvApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val apps = queryActivities(intent)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName to it.activityInfo.name }
            .mapNotNull { info ->
                runCatching { cachedOrBuild(info) }
                    .onFailure { Log.w(TAG, "skip ${info.activityInfo.packageName}", it) }
                    .getOrNull()
            }
            .sortedBy { it.title.lowercase() }
        cache.keys.retainAll(apps.map { it.key }.toSet())
        return apps
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

    private fun cachedOrBuild(info: ResolveInfo): TvApp {
        val activity = info.activityInfo
        val component = ComponentName(activity.packageName, activity.name)
        val updatedAt = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(activity.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        cache[component.flattenToShortString()]?.let { if (it.updatedAt == updatedAt) return it }
        return build(info, component, updatedAt).also { cache[it.key] = it }
    }

    private fun build(info: ResolveInfo, component: ComponentName, updatedAt: Long): TvApp {
        val activity = info.activityInfo
        val title = info.loadLabel(pm).toString().trim().ifEmpty { activity.packageName }
        val banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
        val content: Bitmap = if (banner != null) {
            banner.toBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
        } else {
            val icon = info.loadIcon(pm).toBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            Images.iconContent(icon, spec.widthPx, spec.heightPx, FALLBACK_TILE)
        }
        val tile = Images.roundedTile(content, spec).apply { prepareToDraw() }
        return TvApp(component, title, tile.asImageBitmap(), Images.shelfPixels(content), updatedAt)
    }

    private companion object {
        const val TAG = "AppRepository"
        const val ICON_SIZE = 192
        const val FALLBACK_TILE = 0xFF2C2C2E.toInt()
    }
}
