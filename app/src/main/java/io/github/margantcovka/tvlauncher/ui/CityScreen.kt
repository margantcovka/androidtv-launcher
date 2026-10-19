package io.github.margantcovka.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.margantcovka.tvlauncher.R
import io.github.margantcovka.tvlauncher.data.Place
import kotlinx.coroutines.delay

/**
 * Выбор города для погоды: поиск по названию и «Автоматически» (по IP).
 * [search] возвращает null, если сеть недоступна.
 */
@Composable
fun CityScreen(
    automaticLabel: String?,
    search: suspend (String) -> List<Place>?,
    onPick: (Place?) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(400) // ждём, пока допечатают
        results = search(query)
        loading = false
    }

    val rowText = TextStyle(fontSize = 17.sp, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold)
    val secondary = TextStyle(color = Tokens.textSecondary, fontSize = 15.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Medium)

    Row(Modifier.fillMaxSize().drawBehind { drawRect(Tokens.background) }) {
        Column(Modifier.fillMaxHeight().width(330.dp).padding(start = Tokens.sidePadding, top = 72.dp, end = 24.dp)) {
            BasicText(stringResource(R.string.city_title), style = TextStyle(color = Tokens.textPrimary, fontSize = 38.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))
            BasicText(stringResource(R.string.city_hint), style = secondary.copy(lineHeight = 21.sp))
        }
        Column(Modifier.fillMaxHeight().weight(1f).padding(top = 56.dp, end = Tokens.sidePadding)) {
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .drawBehind { drawRoundRect(if (fieldFocused) Color(0x33FFFFFF) else Color(0x1AFFFFFF), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())) }
                    .border(2.dp, if (fieldFocused) Tokens.textPrimary else Color.Transparent, shape)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) BasicText(stringResource(R.string.city_search_hint), style = secondary.copy(fontSize = 17.sp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = rowText.copy(color = Tokens.textPrimary),
                    cursorBrush = SolidColor(Tokens.textPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(field).onFocusChanged { fieldFocused = it.isFocused },
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "auto") {
                    FocusRow(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) { color ->
                        BasicText(automaticCity(automaticLabel), style = rowText.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val found = results
                when {
                    loading -> item(key = "status") { Status(stringResource(R.string.city_searching), secondary) }
                    found == null -> item(key = "status") { Status(stringResource(R.string.city_error), secondary) }
                    found.isEmpty() && query.trim().length >= 2 -> item(key = "status") { Status(stringResource(R.string.city_no_results), secondary) }
                    else -> itemsIndexed(found, key = { i, place -> "$i:${place.lat},${place.lon}" }) { _, place ->
                        FocusRow(onClick = { onPick(place) }, modifier = Modifier.fillMaxWidth()) { color ->
                            BasicText(place.city, style = rowText.copy(color = color), maxLines = 1)
                            if (place.region.isNotEmpty()) {
                                Spacer(Modifier.width(12.dp))
                                BasicText(
                                    place.region,
                                    style = secondary.copy(color = color.copy(alpha = 0.6f)),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Status(text: String, style: TextStyle) {
    BasicText(text, style = style, modifier = Modifier.padding(start = 14.dp, top = 14.dp))
}
