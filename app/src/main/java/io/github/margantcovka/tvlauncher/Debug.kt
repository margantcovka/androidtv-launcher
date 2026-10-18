package io.github.margantcovka.tvlauncher

/**
 * Переключатели для замеров на телевизоре (tools/bench.py --dbg N → `am start --ei dbg N`):
 * биты выключают части отрисовки, чтобы померить цену каждой.
 */
object Debug {
    var flags = 0
    const val NO_SHELF = 1
    const val NO_TILES = 2
    const val NO_SHADOW = 4
    const val NO_SWEEP = 8
    const val NO_LABELS = 16
    const val NO_TITLE = 32
    const val NO_SCALE = 64
    fun off(bit: Int) = flags and bit != 0
}
