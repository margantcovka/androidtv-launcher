package io.github.margantcovka.tvlauncher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Дизайн-токены. Экран 1080p на Android TV — это 960×540 dp. */
object Tokens {
    val background = Color(0xFF0B0B0D)
    val textPrimary = Color(0xFFF2F2F7)
    val textSecondary = Color(0x99EBEBF5)

    const val columns = 5
    val sidePadding = 48.dp
    val columnGap = 22.dp
    val rowGap = 36.dp
    val tileCorner = 12.dp
    val tileShadowBlur = 14.dp
    const val focusedScale = 1.1f

    /** Высота top shelf: первая строка плиток начинается под ней. */
    val shelfHeight = 300.dp
    /** Где встаёт строка в фокусе, когда сетка уехала поверх top shelf. */
    val scrolledRowTop = 64.dp
    /** Запас под сеткой, чтобы даже последняя строка могла подняться к scrolledRowTop. */
    val gridBottomSlack = 360.dp
    val shelfTitleSize = 44.sp
    /** Шапка над top shelf: приветствие, дата, время. */
    val headerTop = 34.dp
    val greetingSize = 26.sp
    val dateSize = 16.sp
    val clockSize = 34.sp
    val headerSecondary = Color(0xCCEBEBF5)
    val tileLabelSize = 13.sp

    const val focusMs = 160
    const val backdropFadeMs = 400
    const val shelfFadeMs = 300
    const val sweepMs = 900

    val sectionLabelSize = 15.sp
    val panel = Color(0xFF1C1C1E)
    val scrim = Color(0xB3000000)
    val rowFocused = Color(0xFFF2F2F7)
    val rowText = Color(0xFFF2F2F7)
    val rowTextFocused = Color(0xFF111113)
}
