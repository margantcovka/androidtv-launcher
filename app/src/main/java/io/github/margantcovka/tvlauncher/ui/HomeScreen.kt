package io.github.margantcovka.tvlauncher.ui

import io.github.margantcovka.tvlauncher.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.margantcovka.tvlauncher.data.Catalog
import io.github.margantcovka.tvlauncher.data.LauncherTile
import io.github.margantcovka.tvlauncher.data.Section
import io.github.margantcovka.tvlauncher.data.TileAction
import io.github.margantcovka.tvlauncher.data.TileSpec
import kotlinx.coroutines.launch

private val sectionStyle = TextStyle(color = Tokens.textSecondary, fontSize = Tokens.sectionLabelSize, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold)

/** Перестановка: раздел, текущий порядок его видимых плиток и перемещаемая плитка. */
private class MoveState(val section: Section, val keys: SnapshotStateList<String>, val original: List<String>, val key: String)

/**
 * Домашний экран: сверху top shelf (размытый фон выбранной плитки и её название),
 * ниже сетка по 5 плиток — приложения, источники, «Ещё». Уход ниже первой строки
 * сдвигает сетку поверх top shelf. Удержание OK открывает меню плитки.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    catalog: Catalog?,
    spec: TileSpec,
    isHome: Boolean,
    homePresses: Int,
    onOpen: (LauncherTile) -> Unit,
    onAppInfo: (LauncherTile) -> Unit,
    onHide: (LauncherTile) -> Unit,
    onReorder: (Section, List<String>) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }

    var focusedKey by remember { mutableStateOf<String?>(null) }
    var menuTile by remember { mutableStateOf<LauncherTile?>(null) }
    var moving by remember { mutableStateOf<MoveState?>(null) }
    /** Ключ плитки, на которую вернуть фокус после меню, скрытия или Home. */
    var restoreFocus by remember { mutableStateOf<String?>(null) }
    var restoreRequest by remember { mutableIntStateOf(0) }

    val sections: List<Pair<Section, List<LauncherTile>>> = Section.entries.mapNotNull { section ->
        val visible = catalog?.visible(section).orEmpty()
        val move = moving
        val tiles = if (move != null && move.section == section) {
            val byKey = visible.associateBy { it.key }
            move.keys.mapNotNull { byKey[it] }
        } else visible
        if (tiles.isEmpty()) null else section to tiles
    }
    val firstRowKeys = sections.firstOrNull()?.second?.take(Tokens.columns)?.map { it.key }.orEmpty().toSet()
    val currentFirstRow by rememberUpdatedState(firstRowKeys)
    val inFirstRow by remember { derivedStateOf { focusedKey == null || focusedKey in currentFirstRow } }
    val focusedTile = catalog?.all?.firstOrNull { it.key == focusedKey } ?: sections.firstOrNull()?.second?.firstOrNull()

    val density = LocalDensity.current
    val bringIntoView = remember(density) {
        RowAnchoredBringIntoView(
            isFirstRow = { focusedKey == null || focusedKey in currentFirstRow },
            firstRowTopPx = with(density) { Tokens.shelfHeight.toPx() },
            otherRowTopPx = with(density) { Tokens.scrolledRowTop.toPx() },
        )
    }

    fun focus(key: String?) {
        restoreFocus = key
        restoreRequest++
    }
    LaunchedEffect(restoreRequest) {
        if (restoreRequest == 0) return@LaunchedEffect
        withFrameNanos { } // дать плиткам снова стать фокусируемыми
        val key = restoreFocus ?: sections.firstOrNull()?.second?.firstOrNull()?.key ?: return@LaunchedEffect
        runCatching { requester(key).requestFocus() }
    }

    fun backToTop() {
        menuTile = null
        moving = null
        scope.launch { gridState.animateScrollToItem(0) }
        focus(sections.firstOrNull()?.second?.firstOrNull()?.key)
    }

    fun finishMove(save: Boolean) {
        val move = moving ?: return
        moving = null
        if (save && move.keys.toList() != move.original) onReorder(move.section, move.keys.toList())
        focus(move.key)
    }

    fun shift(delta: Int, clamp: Boolean) {
        val move = moving ?: return
        val from = move.keys.indexOf(move.key)
        var to = from + delta
        if (clamp) to = to.coerceIn(0, move.keys.lastIndex)
        if (to == from || to !in move.keys.indices) return
        move.keys.removeAt(from)
        move.keys.add(to, move.key)
    }

    // Фокус на первую плитку, когда список впервые загрузился.
    val hasTiles = sections.isNotEmpty()
    LaunchedEffect(hasTiles) { if (hasTiles && focusedKey == null) focus(null) }
    // Повторное нажатие Home на домашнем экране возвращает в начало.
    LaunchedEffect(homePresses) { if (homePresses > 0) backToTop() }
    BackHandler(enabled = menuTile == null && moving == null && (isHome || !inFirstRow)) { if (!inFirstRow) backToTop() }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (moving == null) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent event.key != Key.Back
                if (event.nativeKeyEvent.repeatCount > 0 && event.key in confirmKeys) return@onPreviewKeyEvent true
                when (event.key) {
                    Key.DirectionLeft -> shift(-1, clamp = false)
                    Key.DirectionRight -> shift(1, clamp = false)
                    Key.DirectionUp -> shift(-Tokens.columns, clamp = true)
                    Key.DirectionDown -> shift(Tokens.columns, clamp = true)
                    in confirmKeys -> finishMove(save = true)
                    Key.Back -> finishMove(save = false)
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        TopShelf(focusedTile, inFirstRow)

        when {
            catalog == null -> Unit
            sections.isEmpty() -> BasicText(
                stringResource(R.string.no_apps),
                style = TextStyle(color = Tokens.textSecondary, fontFamily = InterDisplay),
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
                    sections.forEachIndexed { sectionIndex, (section, tiles) ->
                        if (sectionIndex > 0) {
                            item(key = "label:$section", span = { GridItemSpan(maxLineSpan) }, contentType = "label") {
                                BasicText(sectionTitle(section), style = sectionStyle, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        itemsIndexed(tiles, key = { _, tile -> tile.key }, contentType = { _, _ -> "tile" }) { index, tile ->
                            Tile(
                                tile = tile,
                                spec = spec,
                                moving = moving?.key == tile.key,
                                position = index,
                                onFocused = { focusedKey = tile.key },
                                onClick = { onOpen(tile) },
                                onLongClick = { menuTile = tile },
                                modifier = Modifier.focusRequester(requester(tile.key)),
                            )
                        }
                    }
                }
            }
        }

        menuTile?.let { tile ->
            val close = { menuTile = null; focus(tile.key) }
            val moveLabel = stringResource(R.string.menu_move)
            val hideLabel = stringResource(R.string.menu_hide)
            val infoLabel = stringResource(R.string.menu_app_info)
            val cancelLabel = stringResource(R.string.menu_cancel)
            val options = buildList {
                add(MenuOption(moveLabel) {
                    menuTile = null
                    val keys = catalog?.visible(tile.section).orEmpty().map { it.key }
                    moving = MoveState(tile.section, mutableStateListOf(*keys.toTypedArray()), keys, tile.key)
                    focus(tile.key)
                })
                if (tile.hideable) add(MenuOption(hideLabel) {
                    menuTile = null
                    val siblings = catalog?.visible(tile.section).orEmpty()
                    val i = siblings.indexOfFirst { it.key == tile.key }
                    val next = siblings.getOrNull(i + 1) ?: siblings.getOrNull(i - 1)
                    onHide(tile)
                    focus(next?.key)
                })
                if (tile.action is TileAction.App) add(MenuOption(infoLabel) { close(); onAppInfo(tile) })
                add(MenuOption(cancelLabel) { close() })
            }
            TileMenu(title = tile.title, options = options, onDismiss = { close() })
        }
    }
}

private val confirmKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

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

@Composable
private fun sectionTitle(section: Section): String = when (section) {
    Section.APPS -> ""
    Section.SOURCES -> stringResource(R.string.section_sources)
    Section.MORE -> stringResource(R.string.section_more)
}
