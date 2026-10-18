package io.github.margantcovka.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.margantcovka.tvlauncher.data.TvApp

/** Плитка tvOS: широкая, скруглённая; в фокусе приподнимается, бросает тень, по ней проходит блик. */
@Composable
fun Tile(
    app: TvApp,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val sweep = remember { Animatable(-1f) }
    LaunchedEffect(focused) {
        if (focused) {
            sweep.snapTo(-1f)
            sweep.animateTo(2f, tween(Tokens.sweepMs, easing = FastOutSlowInEasing))
        }
    }
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "label")
    val shape = RoundedCornerShape(Tokens.tileCorner)

    Column(modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                },
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = Tokens.focusedScale),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Tokens.tileFallback,
                focusedContainerColor = Tokens.tileFallback,
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.7f), elevation = 18.dp),
            ),
        ) {
            TileArt(app, Modifier.fillMaxSize().drawWithContent {
                drawContent()
                val p = sweep.value
                if (p > -1f && p < 2f) {
                    val x = size.width * p
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.22f),
                            1f to Color.Transparent,
                            start = Offset(x - size.width * 0.35f, 0f),
                            end = Offset(x, size.height),
                        ),
                    )
                }
            })
        }
        Text(
            text = app.title,
            color = Tokens.textPrimary,
            fontSize = Tokens.tileLabelSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .graphicsLayer { alpha = labelAlpha },
        )
    }
}

@Composable
private fun TileArt(app: TvApp, modifier: Modifier) {
    if (app.isBanner) {
        Image(app.art, contentDescription = app.title, contentScale = ContentScale.Crop, modifier = modifier)
        return
    }
    val edges = app.edgeColors
    val background = if (edges != null) {
        Brush.horizontalGradient(0.5f to Color(edges.first), 0.5f to Color(edges.second))
    } else {
        Brush.horizontalGradient(listOf(Tokens.tileFallback, Tokens.tileFallback))
    }
    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        Image(
            app.art,
            contentDescription = app.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxHeight(if (edges != null) 1f else 0.72f)
                .aspectRatio(1f),
        )
    }
}
