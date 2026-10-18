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
import io.github.margantcovka.tvlauncher.data.TvApp
import io.github.margantcovka.tvlauncher.ui.HomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val repository by lazy { AppRepository(applicationContext) }
    private var apps by mutableStateOf<List<TvApp>?>(null)
    private var homePresses by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true
        setContent {
            HomeScreen(apps = apps, isHome = isHome, homePresses = homePresses, onLaunch = repository::launch)
        }
    }

    override fun onResume() {
        super.onResume()
        // Перечитываем список при каждом возвращении: могли поставить или удалить приложение.
        lifecycleScope.launch {
            apps = withContext(Dispatchers.Default) { repository.load() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }
}
