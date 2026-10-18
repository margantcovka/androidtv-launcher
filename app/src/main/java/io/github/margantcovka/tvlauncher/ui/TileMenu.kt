package io.github.margantcovka.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MenuOption(val label: String, val onClick: () -> Unit)

/** Меню плитки поверх экрана: затемнение, панель с названием и пунктами. Back закрывает. */
@Composable
fun TileMenu(title: String, options: List<MenuOption>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize().background(Tokens.scrim), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(360.dp)
                // Фокус не уходит из меню на плитки под ним.
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup()
                .background(Tokens.panel, RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            BasicText(
                title,
                style = TextStyle(color = Tokens.textPrimary, fontSize = 22.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 12.dp),
            )
            options.forEachIndexed { i, option ->
                FocusRow(
                    onClick = option.onClick,
                    modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                ) { color ->
                    BasicText(option.label, style = TextStyle(color = color, fontSize = 17.sp, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}
