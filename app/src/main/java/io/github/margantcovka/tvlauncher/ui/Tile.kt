package io.github.margantcovka.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.margantcovka.tvlauncher.Debug
import io.github.margantcovka.tvlauncher.data.TileSpec
import io.github.margantcovka.tvlauncher.data.TvApp
import kotlin.math.roundToInt

private val labelStyle = TextStyle(color = Tokens.textPrimary, fontSize = Tokens.tileLabelSize, textAlign = TextAlign.Center)

/**
 * Плитка tvOS: в фокусе приподнимается, бросает тень, по ней проходит блик.
 * Только готовые картинки и масштаб слоя: ни обрезки по форме, ни живых теней, ни
 * полупрозрачных слоёв. Анимации читаются в фазе отрисовки.
 */
@Composable
fun Tile(
    app: TvApp,
    spec: TileSpec,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val lift = animateFloatAsState(
        if (focused) 1f else 0f,
        tween(Tokens.focusMs, easing = FastOutSlowInEasing),
        label = "lift",
    )
    val sweep = remember { Animatable(2f) }
    LaunchedEffect(focused) {
        if (focused) {
            sweep.snapTo(-1f)
            sweep.animateTo(2f, tween(Tokens.sweepMs, easing = FastOutSlowInEasing))
        }
    }
    val interaction = remember { MutableInteractionSource() }

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .graphicsLayer {
                    val s = if (Debug.off(Debug.NO_SCALE)) 1f else 1f + (Tokens.focusedScale - 1f) * lift.value
                    scaleX = s
                    scaleY = s
                }
                .drawBehind {
                    val l = lift.value
                    val k = size.width / spec.widthPx
                    if (l > 0f && !Debug.off(Debug.NO_SHADOW)) {
                        val m = spec.shadowMarginPx * k
                        drawImage(
                            spec.shadow,
                            dstOffset = IntOffset(-m.roundToInt(), -m.roundToInt()),
                            dstSize = IntSize((size.width + 2 * m).roundToInt(), (size.height + 2 * m).roundToInt()),
                            alpha = l,
                            filterQuality = FilterQuality.Low,
                        )
                    }
                    if (!Debug.off(Debug.NO_TILES)) drawImage(
                        app.tile,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.Low,
                    )
                    val p = sweep.value
                    if (p > -1f && p < 2f && !Debug.off(Debug.NO_SWEEP)) {
                        val x = size.width * p
                        drawRoundRect(
                            Brush.linearGradient(
                                0f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = 0.22f),
                                1f to Color.Transparent,
                                start = Offset(x - size.width * 0.35f, 0f),
                                end = Offset(x, size.height),
                            ),
                            cornerRadius = CornerRadius(spec.cornerPx * k),
                        )
                    }
                },
        )
        BasicText(
            text = app.title,
            style = labelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .graphicsLayer {
                    alpha = if (Debug.off(Debug.NO_LABELS)) 0f else lift.value
                    // Прозрачность прямо в операциях рисования, без отдельного буфера.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        )
    }
}
