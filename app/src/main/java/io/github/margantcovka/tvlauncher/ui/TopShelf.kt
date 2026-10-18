package io.github.margantcovka.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.margantcovka.tvlauncher.Debug
import io.github.margantcovka.tvlauncher.data.LauncherTile
import kotlin.math.roundToInt

/**
 * Фон всего экрана и название выбранного приложения. Всё анимируется в фазе отрисовки:
 * смена приложения не вызывает перекомпоновку, а GPU рисует одну картинку и текст.
 */
@Composable
fun TopShelf(app: LauncherTile?, visible: Boolean, modifier: Modifier = Modifier) {
    val renderer = remember { ShelfRenderer(Tokens.background.toArgb()) }
    val fade = remember { Animatable(1f) }
    val visibility = animateFloatAsState(if (visible) 1f else 0f, tween(Tokens.shelfFadeMs, easing = LinearEasing), label = "shelf")
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val style = remember { TextStyle(color = Tokens.textPrimary, fontSize = Tokens.shelfTitleSize, fontFamily = InterDisplay, fontWeight = FontWeight.Bold) }
    val clock = rememberClockText()
    val greetingStyle = remember { TextStyle(color = Tokens.textPrimary, fontSize = Tokens.greetingSize, fontFamily = InterDisplay, fontWeight = FontWeight.Bold) }
    val dateStyle = remember { TextStyle(color = Tokens.headerSecondary, fontSize = Tokens.dateSize, fontFamily = InterDisplay, fontWeight = FontWeight.Medium) }
    // tnum: цифры одной ширины, время не дёргается при смене минут.
    val timeStyle = remember { TextStyle(color = Tokens.textPrimary, fontSize = Tokens.clockSize, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum") }
    var previousTitle by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(app?.key) {
        renderer.retarget(app?.shelf)
        previousTitle = title
        title = app?.title
        fade.snapTo(0f)
        fade.animateTo(1f, tween(Tokens.backdropFadeMs, easing = LinearEasing))
    }

    Spacer(
        modifier.fillMaxSize().drawBehind {
            val t = fade.value
            val v = visibility.value
            if (!Debug.off(Debug.NO_SHELF)) drawImage(
                renderer.render(t, v),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
            if (v <= 0f || Debug.off(Debug.NO_TITLE)) return@drawBehind
            val pad = Tokens.sidePadding.toPx()
            if (!Debug.off(Debug.NO_TITLE)) {
                // Шапка: приветствие и дата слева, время справа.
                val text = clock.value
                val headerTop = Tokens.headerTop.toPx()
                val greeting = measurer.measure(text.greeting, greetingStyle, maxLines = 1)
                drawText(greeting, topLeft = Offset(pad, headerTop), alpha = v)
                val date = measurer.measure(text.date, dateStyle, maxLines = 1)
                drawText(date, topLeft = Offset(pad, headerTop + greeting.size.height + 2.dp.toPx()), alpha = v)
                val time = measurer.measure(text.time, timeStyle, maxLines = 1)
                // Время на одной базовой линии с приветствием.
                val timeTop = headerTop + greeting.firstBaseline - time.firstBaseline
                drawText(time, topLeft = Offset(size.width - pad - time.size.width, timeTop), alpha = v)
            }
            val top = (Tokens.shelfHeight - 96.dp).toPx()
            val constraints = Constraints(maxWidth = (size.width - 2 * pad).roundToInt().coerceAtLeast(0))
            fun draw(text: String?, alpha: Float) {
                if (text == null || alpha <= 0.01f) return
                val layout = measurer.measure(text, style, TextOverflow.Ellipsis, maxLines = 1, constraints = constraints)
                drawText(layout, topLeft = Offset(pad, top), alpha = alpha)
            }
            draw(previousTitle, (1f - t) * v)
            draw(title, t * v)
        },
    )
}
