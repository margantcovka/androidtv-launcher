package io.github.margantcovka.tvlauncher.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.net.Uri
import android.provider.Settings
import android.util.Log

/** Выполняет действия плиток. */
class Launcher(private val context: Context) {

    /** false, если система не нашла, чем открыть. [TileAction.LauncherSettings] открывает экран сам UI. */
    fun open(action: TileAction): Boolean {
        val intent = when (action) {
            is TileAction.App -> Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(action.component)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            is TileAction.Input -> Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(action.inputId))
            is TileAction.Activity -> Intent(Intent.ACTION_MAIN).setComponent(action.component)
            TileAction.SystemSettings -> Intent(Settings.ACTION_SETTINGS)
            is TileAction.AppDetails -> details(action.packageName)
            TileAction.LauncherSettings -> return false
        }
        return start(intent)
    }

    fun appInfo(action: TileAction.App): Boolean = start(details(action.component.packageName))

    private fun details(packageName: String) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "start $intent", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "start $intent", e)
        false
    }

    private companion object {
        const val TAG = "Launcher"
    }
}
