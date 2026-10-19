package io.github.margantcovka.tvlauncher.ui

import io.github.margantcovka.tvlauncher.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.margantcovka.tvlauncher.data.Catalog
import io.github.margantcovka.tvlauncher.data.LauncherTile
import io.github.margantcovka.tvlauncher.data.Section

private sealed interface SettingsItem {
    data class Header(val text: String) : SettingsItem
    data class Toggle(val tile: LauncherTile) : SettingsItem
    data object ResetOrder : SettingsItem
    data object WeatherToggle : SettingsItem
    data object WeatherUnits : SettingsItem
}

/** Настройки погоды в шапке. */
data class WeatherSettings(val enabled: Boolean, val fahrenheit: Boolean)

/** Настройки главного экрана: какие плитки показывать и сброс порядка. */
@Composable
fun SettingsScreen(
    catalog: Catalog,
    onToggleHidden: (LauncherTile) -> Unit,
    onResetOrder: () -> Unit,
    weather: WeatherSettings,
    onToggleWeather: () -> Unit,
    onToggleUnits: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val items = buildList {
        val headers = mapOf(Section.APPS to stringResource(R.string.section_apps), Section.SOURCES to stringResource(R.string.section_sources), Section.MORE to stringResource(R.string.section_more))
        Section.entries.forEach { section ->
            val tiles = catalog.all.filter { it.section == section && it.hideable }
            if (tiles.isNotEmpty()) {
                add(SettingsItem.Header(headers.getValue(section)))
                tiles.forEach { add(SettingsItem.Toggle(it)) }
            }
        }
        add(SettingsItem.Header(stringResource(R.string.settings_weather)))
        add(SettingsItem.WeatherToggle)
        if (weather.enabled) add(SettingsItem.WeatherUnits)
        add(SettingsItem.Header(stringResource(R.string.settings_order)))
        add(SettingsItem.ResetOrder)
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val firstFocusable = items.indexOfFirst { it !is SettingsItem.Header }

    Row(Modifier.fillMaxSize().drawBehind { drawRect(Tokens.background) }) {
        Column(Modifier.fillMaxHeight().width(330.dp).padding(start = Tokens.sidePadding, top = 72.dp, end = 24.dp)) {
            BasicText(stringResource(R.string.settings_title), style = TextStyle(color = Tokens.textPrimary, fontSize = 38.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))
            BasicText(
                stringResource(R.string.settings_hint),
                style = TextStyle(color = Tokens.textSecondary, fontSize = 15.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Medium, lineHeight = 21.sp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxHeight().weight(1f),
            contentPadding = PaddingValues(top = 56.dp, bottom = 56.dp, end = Tokens.sidePadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(items, key = { item ->
                when (item) {
                    is SettingsItem.Header -> "h:" + item.text
                    is SettingsItem.Toggle -> item.tile.key
                    SettingsItem.ResetOrder -> "reset"
                    SettingsItem.WeatherToggle -> "weather"
                    SettingsItem.WeatherUnits -> "units"
                }
            }) { item ->
                val index = items.indexOf(item)
                val focusModifier = if (index == firstFocusable) Modifier.focusRequester(first) else Modifier
                when (item) {
                    is SettingsItem.Header -> BasicText(
                        item.text,
                        style = TextStyle(color = Tokens.textSecondary, fontSize = Tokens.sectionLabelSize, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 6.dp),
                    )
                    is SettingsItem.Toggle -> {
                        val shown = item.tile.key !in catalog.hidden
                        FocusRow(onClick = { onToggleHidden(item.tile) }, modifier = focusModifier.fillMaxWidth()) { color ->
                            Image(item.tile.image, contentDescription = null, modifier = Modifier.size(width = 64.dp, height = 36.dp))
                            Spacer(Modifier.width(16.dp))
                            BasicText(
                                item.tile.title,
                                style = TextStyle(color = color, fontSize = 17.sp, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            BasicText(
                                stringResource(if (shown) R.string.settings_shown else R.string.settings_hidden),
                                style = TextStyle(color = color.copy(alpha = if (shown) 0.9f else 0.5f), fontSize = 15.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Medium),
                            )
                        }
                    }
                    SettingsItem.WeatherToggle -> ValueRow(
                        title = stringResource(R.string.settings_weather_show),
                        value = stringResource(if (weather.enabled) R.string.settings_shown else R.string.settings_hidden),
                        dimValue = !weather.enabled,
                        onClick = onToggleWeather,
                        modifier = focusModifier,
                    )
                    SettingsItem.WeatherUnits -> ValueRow(
                        title = stringResource(R.string.settings_weather_units),
                        value = stringResource(if (weather.fahrenheit) R.string.units_fahrenheit else R.string.units_celsius),
                        dimValue = false,
                        onClick = onToggleUnits,
                        modifier = focusModifier,
                    )
                    SettingsItem.ResetOrder -> FocusRow(onClick = onResetOrder, modifier = focusModifier.fillMaxWidth()) { color ->
                        BasicText(stringResource(R.string.settings_reset_order), style = TextStyle(color = color, fontSize = 17.sp, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

/** Строка настройки: название слева, текущее значение справа, OK переключает. */
@Composable
private fun ValueRow(title: String, value: String, dimValue: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FocusRow(onClick = onClick, modifier = modifier.fillMaxWidth()) { color ->
        BasicText(
            title,
            style = TextStyle(color = color, fontSize = 17.sp, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BasicText(
            value,
            style = TextStyle(color = color.copy(alpha = if (dimValue) 0.5f else 0.9f), fontSize = 15.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Medium),
        )
    }
}
