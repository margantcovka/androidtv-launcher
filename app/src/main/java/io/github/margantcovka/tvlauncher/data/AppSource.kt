package io.github.margantcovka.tvlauncher.data

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

/** TV-приложения из PackageManager с их баннерами. */
class AppSource(private val context: Context, private val spec: TileSpec) {

    private val pm: PackageManager = context.packageManager

    /** Готовые плитки по ключу: при возврате на экран не перерисовываем то, что не менялось. */
    private val cache = HashMap<String, LauncherTile>()

    /** Activity лаунчера для каждого TV-приложения, кроме нас и [exclude]. */
    fun launcherActivities(): List<ResolveInfo> =
        queryActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName to it.activityInfo.name }

    fun load(activities: List<ResolveInfo>, exclude: Set<String>): List<LauncherTile> {
        val tiles = activities
            .filter { it.activityInfo.packageName !in exclude }
            .mapNotNull { info ->
                runCatching { cachedOrBuild(info) }
                    .onFailure { Log.w(TAG, "skip ${info.activityInfo.packageName}", it) }
                    .getOrNull()
            }
            .sortedBy { it.title.lowercase() }
        cache.keys.retainAll(tiles.map { it.key }.toSet())
        return tiles
    }

    /** null — пакета нет; иначе включён ли он (отключённый через `pm disable-user` остаётся установленным). */
    fun isEnabled(pkg: String): Boolean? = runCatching { pm.getApplicationInfo(pkg, 0).enabled }.getOrNull()

    private fun queryActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private fun cachedOrBuild(info: ResolveInfo): LauncherTile {
        val activity = info.activityInfo
        val component = ComponentName(activity.packageName, activity.name)
        val key = "app:" + component.flattenToShortString()
        val updatedAt = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(activity.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        cache[key]?.let { if (it.version == updatedAt) return it }
        return build(info, component, key, updatedAt).also { cache[key] = it }
    }

    private fun build(info: ResolveInfo, component: ComponentName, key: String, updatedAt: Long): LauncherTile {
        val activity = info.activityInfo
        val title = info.loadLabel(pm).toString().trim().ifEmpty { activity.packageName }
        val banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
        val content: Bitmap = if (banner != null) {
            banner.toBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
        } else {
            val icon = info.loadIcon(pm).toBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            Images.iconContent(icon, spec.widthPx, spec.heightPx, FALLBACK_TILE)
        }
        val image = Images.roundedTile(content, spec).apply { prepareToDraw() }
        return LauncherTile(key, title, Section.APPS, TileAction.App(component), image.asImageBitmap(), Images.shelfPixels(content), updatedAt)
    }

    private companion object {
        const val TAG = "AppSource"
        const val ICON_SIZE = 192
        const val FALLBACK_TILE = 0xFF2C2C2E.toInt()
    }
}
