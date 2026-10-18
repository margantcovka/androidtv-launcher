package io.github.margantcovka.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import io.github.margantcovka.tvlauncher.data.TileSpec
import io.github.margantcovka.tvlauncher.data.TvApp
import kotlinx.coroutines.launch

/**
 * Домашний экран: сверху top shelf (размытый фон выбранного приложения и его название),
 * ниже сетка по 5 плиток. Уход ниже первой строки сдвигает сетку поверх top shelf.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    apps: List<TvApp>?,
    spec: TileSpec,
    isHome: Boolean,
    homePresses: Int,
    onLaunch: (TvApp) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val firstTile = remember { FocusRequester() }
    // Фокус меняется на каждое нажатие: читаем его только там, где он нужен,
    // чтобы не перекомпоновывать весь экран.
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val inFirstRow by remember { derivedStateOf { focusedIndex < Tokens.columns } }

    val density = LocalDensity.current
    val bringIntoView = remember(density) {
        RowAnchoredBringIntoView(
            isFirstRow = { focusedIndex < Tokens.columns },
            firstRowTopPx = with(density) { Tokens.shelfHeight.toPx() },
            otherRowTopPx = with(density) { Tokens.scrolledRowTop.toPx() },
        )
    }

    fun backToTop() {
        scope.launch {
            gridState.animateScrollToItem(0)
            runCatching { firstTile.requestFocus() }
        }
    }

    // Фокус на первую плитку, когда список впервые загрузился.
    val hasApps = !apps.isNullOrEmpty()
    LaunchedEffect(hasApps) { if (hasApps) runCatching { firstTile.requestFocus() } }
    // Повторное нажатие Home на домашнем экране возвращает в начало.
    LaunchedEffect(homePresses) { if (homePresses > 0) backToTop() }
    BackHandler(enabled = isHome || !inFirstRow) { if (!inFirstRow) backToTop() }

    Box(Modifier.fillMaxSize()) {
        FocusedShelf(apps, { focusedKey }, inFirstRow)

        when {
            apps == null -> Unit
            apps.isEmpty() -> BasicText(
                "Нет приложений для Android TV",
                style = TextStyle(color = Tokens.textSecondary),
                modifier = Modifier.align(Alignment.Center),
            )
            else -> CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(Tokens.columns),
                    state = gridState,
                    contentPadding = PaddingValues(start = Tokens.sidePadding, end = Tokens.sidePadding, bottom = Tokens.gridBottomSlack),
                    horizontalArrangement = Arrangement.spacedBy(Tokens.columnGap),
                    verticalArrangement = Arrangement.spacedBy(Tokens.rowGap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "shelf") {
                        Spacer(Modifier.height(Tokens.shelfHeight - Tokens.rowGap))
                    }
                    itemsIndexed(apps, key = { _, app -> app.key }, contentType = { _, _ -> "tile" }) { index, app ->
                        Tile(
                            app = app,
                            spec = spec,
                            onFocused = {
                                focusedKey = app.key
                                focusedIndex = index
                            },
                            onClick = { onLaunch(app) },
                            modifier = if (index == 0) Modifier.focusRequester(firstTile) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/** Отдельная функция, чтобы смена фокуса перекомпоновывала только полку. */
@Composable
private fun FocusedShelf(apps: List<TvApp>?, focusedKey: () -> String?, visible: Boolean) {
    val key = focusedKey()
    val app = apps?.firstOrNull { it.key == key } ?: apps?.firstOrNull()
    TopShelf(app, visible)
}

/**
 * Куда прокручивать сетку при переходе фокуса. Первая строка всегда стоит под top shelf,
 * остальные поднимаются к верху экрана — как в tvOS. Стандартное поведение Compose на TV
 * держит фокус на 30% высоты, и первая строка уезжала бы на полку.
 */
@OptIn(ExperimentalFoundationApi::class)
private class RowAnchoredBringIntoView(
    private val isFirstRow: () -> Boolean,
    private val firstRowTopPx: Float,
    private val otherRowTopPx: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - if (isFirstRow()) firstRowTopPx else otherRowTopPx
}
