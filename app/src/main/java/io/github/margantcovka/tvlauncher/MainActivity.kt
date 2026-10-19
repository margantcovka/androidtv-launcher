package io.github.margantcovka.tvlauncher

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.github.margantcovka.tvlauncher.data.Catalog
import io.github.margantcovka.tvlauncher.data.Images
import io.github.margantcovka.tvlauncher.data.Launcher
import io.github.margantcovka.tvlauncher.data.LauncherRepository
import io.github.margantcovka.tvlauncher.data.LauncherTile
import io.github.margantcovka.tvlauncher.data.TileAction
import io.github.margantcovka.tvlauncher.data.TileSpec
import io.github.margantcovka.tvlauncher.data.Weather
import io.github.margantcovka.tvlauncher.data.WeatherSource
import io.github.margantcovka.tvlauncher.ui.CityScreen
import io.github.margantcovka.tvlauncher.ui.HomeScreen
import io.github.margantcovka.tvlauncher.ui.SettingsScreen
import io.github.margantcovka.tvlauncher.ui.WeatherSettings
import io.github.margantcovka.tvlauncher.ui.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private enum class Screen { HOME, SETTINGS, CITY }

    private lateinit var spec: TileSpec
    private lateinit var repository: LauncherRepository
    private lateinit var launcher: Launcher
    private var catalog by mutableStateOf<Catalog?>(null)
    private var screen by mutableStateOf(Screen.HOME)
    private var homePresses by mutableIntStateOf(0)
    private lateinit var weatherSource: WeatherSource
    private var weather by mutableStateOf<Weather?>(null)
    private var weatherSettings by mutableStateOf(WeatherSettings(enabled = true, fahrenheit = false, city = null, ipCity = null))
    private var weatherJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Debug.flags = intent?.getIntExtra("dbg", 0) ?: 0
        spec = tileSpec()
        repository = LauncherRepository(applicationContext, spec)
        launcher = Launcher(this)
        weatherSource = WeatherSource(applicationContext)
        weatherSettings = weatherSettings()
        weather = weatherSource.cached()
        val isHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true
        setContent {
            val current = catalog
            if (screen == Screen.CITY) {
                CityScreen(
                    automaticLabel = weatherSettings.ipCity,
                    search = { query -> withContext(Dispatchers.IO) { runCatching { weatherSource.search(query) }.getOrNull() } },
                    onPick = { place -> weatherSource.manualPlace = place; weatherChanged(); screen = Screen.SETTINGS },
                    onBack = { screen = Screen.SETTINGS },
                )
            } else if (screen == Screen.SETTINGS && current != null) {
                SettingsScreen(
                    catalog = current,
                    onToggleHidden = { tile -> repository.prefs.setHidden(tile.key, tile.key !in current.hidden); rearrange() },
                    onResetOrder = { repository.prefs.resetOrder(); rearrange(); toast(getString(R.string.toast_order_reset)) },
                    weather = weatherSettings,
                    onToggleWeather = { weatherSource.enabled = !weatherSource.enabled; weatherChanged() },
                    onToggleUnits = { weatherSource.fahrenheit = !weatherSource.fahrenheit; weatherChanged() },
                    onChooseCity = { screen = Screen.CITY },
                    onBack = { screen = Screen.HOME },
                )
            } else {
                HomeScreen(
                    catalog = current,
                    spec = spec,
                    isHome = isHome,
                    homePresses = homePresses,
                    weather = weather,
                    onOpen = ::open,
                    onAppInfo = { tile -> (tile.action as? TileAction.App)?.let(launcher::appInfo) },
                    onHide = { tile -> repository.prefs.setHidden(tile.key, true); rearrange() },
                    onReorder = { section, keys -> current?.let { repository.prefs.setOrder(it.orderWith(section, keys)) }; rearrange() },
                )
            }
        }
        // Экран целиком закрывает непрозрачный фон полки: фон окна — лишняя заливка на каждом кадре.
        window.setBackgroundDrawable(null)
    }

    override fun onResume() {
        super.onResume()
        startWeather()
        // Перечитываем список при каждом возвращении: могли поставить или удалить приложение,
        // подключить HDMI. Неизменившиеся плитки берутся из кэша, и если ничего не поменялось, экран не трогаем.
        lifecycleScope.launch {
            val fresh = withContext(Dispatchers.Default) { repository.load() }
            if (!fresh.sameAs(catalog)) catalog = fresh
        }
    }

    override fun onPause() {
        super.onPause()
        weatherJob?.cancel()
    }

    private companion object {
        /** Паузы перед повтором после сбоя: короткий сбой сервиса почти незаметен, долгий не долбит сеть. */
        val RETRY_MS = longArrayOf(15_000, 30_000, 60_000, 120_000)
    }

    /** Погода обновляется, пока лаунчер на экране: сразу, если устарела, потом раз в полчаса. */
    private fun startWeather() {
        weatherJob?.cancel()
        if (!weatherSource.enabled) return
        weatherJob = lifecycleScope.launch {
            var failures = 0
            while (true) {
                if (weatherSource.isStale() || weather == null) {
                    val fresh = withContext(Dispatchers.IO) { weatherSource.fetch() }
                    if (fresh != null) weather = fresh
                    failures = if (fresh != null) 0 else failures + 1
                }
                weatherSettings = weatherSettings()
                delay(if (failures > 0) RETRY_MS[minOf(failures, RETRY_MS.size) - 1] else WeatherSource.MAX_AGE_MS)
            }
        }
    }

    private fun weatherChanged() {
        weatherSettings = weatherSettings()
        weather = weatherSource.cached()
        startWeather()
    }

    private fun weatherSettings() = WeatherSettings(
        enabled = weatherSource.enabled,
        fahrenheit = weatherSource.fahrenheit,
        city = weatherSource.manualPlace?.city,
        ipCity = weatherSource.ipCity(),
    )

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (screen != Screen.HOME) screen = Screen.HOME else homePresses++
        }
    }

    private fun open(tile: LauncherTile) {
        if (tile.action == TileAction.LauncherSettings) {
            screen = Screen.SETTINGS
        } else if (!launcher.open(tile.action)) {
            toast(getString(R.string.toast_open_failed, tile.title))
        }
    }

    private fun rearrange() {
        catalog = repository.arrange()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun Catalog.sameAs(other: Catalog?): Boolean =
        other != null && hidden == other.hidden && all.size == other.all.size && all.indices.all { all[it] === other.all[it] }

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
