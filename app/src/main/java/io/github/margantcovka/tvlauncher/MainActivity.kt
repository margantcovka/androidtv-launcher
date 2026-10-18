package io.github.margantcovka.tvlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.github.margantcovka.tvlauncher.data.AppRepository
import io.github.margantcovka.tvlauncher.data.Images
import io.github.margantcovka.tvlauncher.data.TileSpec
import io.github.margantcovka.tvlauncher.data.TvApp
import io.github.margantcovka.tvlauncher.ui.HalfRateFrameClock
import io.github.margantcovka.tvlauncher.ui.HomeScreen
import io.github.margantcovka.tvlauncher.ui.Tokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
        if (Debug.off(Debug.RGB565)) window.setFormat(android.graphics.PixelFormat.RGB_565)
        spec = tileSpec()
        repository = AppRepository(applicationContext, spec)
        val isHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true
        if (Debug.off(Debug.PROBE_VIEW)) {
            setContentView(ProbeView(this))
            if (Debug.off(Debug.SMALL_WINDOW)) window.setLayout(640, 360)
            return
        }
        val content = ComposeView(this)
        if (!Debug.off(Debug.FULL_RATE)) content.setParentCompositionContext(halfRateRecomposer())
        content.setContent {
            HomeScreen(apps = apps, spec = spec, isHome = isHome, homePresses = homePresses, onLaunch = repository::launch)
        }
        setContentView(content)
        // Экран целиком закрывает непрозрачный фон полки: фон окна — лишняя заливка на каждом кадре.
        window.setBackgroundDrawable(null)
    }

    private var recomposerScope: CoroutineScope? = null

    /** Своя композиция с часами 30 кадров/с (см. [HalfRateFrameClock]). */
    private fun halfRateRecomposer(): Recomposer {
        val main = AndroidUiDispatcher.Main
        val context = main + HalfRateFrameClock(main[MonotonicFrameClock]!!)
        val recomposer = Recomposer(context)
        recomposerScope = CoroutineScope(context).also { scope ->
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
        }
        return recomposer
    }

    override fun onDestroy() {
        recomposerScope?.cancel()
        super.onDestroy()
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

/** Минимальная непрерывная анимация без Compose — проверка, сколько кадров даёт сама платформа. */
private class ProbeView(context: android.content.Context) : android.view.View(context) {
    private val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
    private var x = 0f
    override fun onDraw(canvas: android.graphics.Canvas) {
        canvas.drawColor(android.graphics.Color.BLACK)
        x = (x + 8f) % width.coerceAtLeast(1)
        canvas.drawRect(x, 400f, x + 120f, 520f, paint)
        invalidate()
    }
}
