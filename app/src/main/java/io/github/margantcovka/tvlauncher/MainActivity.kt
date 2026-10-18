package io.github.margantcovka.tvlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.github.margantcovka.tvlauncher.data.AppRepository
import io.github.margantcovka.tvlauncher.data.Images
import io.github.margantcovka.tvlauncher.data.TileSpec
import io.github.margantcovka.tvlauncher.data.TvApp
import io.github.margantcovka.tvlauncher.ui.HomeScreen
import io.github.margantcovka.tvlauncher.ui.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private lateinit var spec: TileSpec
    private lateinit var repository: AppRepository
    private var apps by mutableStateOf<List<TvApp>?>(null)
    private var homePresses by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Debug.flags = intent?.getIntExtra("dbg", 0) ?: 0
        spec = tileSpec()
        repository = AppRepository(applicationContext, spec)
        val isHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true
        setContent {
            HomeScreen(apps = apps, spec = spec, isHome = isHome, homePresses = homePresses, onLaunch = repository::launch)
        }
        // Экран целиком закрывает непрозрачный фон полки: фон окна — лишняя заливка на каждом кадре.
        window.setBackgroundDrawable(null)
    }

    override fun onResume() {
        super.onResume()
        // Перечитываем список при каждом возвращении: могли поставить или удалить приложение.
        // Неизменившиеся плитки берутся из кэша, и если ничего не поменялось, экран не трогаем.
        lifecycleScope.launch {
            val fresh = withContext(Dispatchers.Default) { repository.load() }
            val old = apps
            if (old == null || old.size != fresh.size || old.indices.any { old[it] !== fresh[it] }) apps = fresh
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    /** Размер плитки в пикселях при фокусном масштабе — под него готовятся картинки. */
    private fun tileSpec(): TileSpec {
        val dm = resources.displayMetrics
        val d = dm.density
        val screenDp = dm.widthPixels / d
        val tileDp = (screenDp - 2 * Tokens.sidePadding.value - (Tokens.columns - 1) * Tokens.columnGap.value) / Tokens.columns
        val w = (tileDp * d * Tokens.focusedScale).roundToInt()
        val h = (w * 9f / 16f).roundToInt()
        return Images.tileSpec(w, h, Tokens.tileCorner.value * d * Tokens.focusedScale, Tokens.tileShadowBlur.value * d)
    }
}

