package io.github.margantcovka.tvlauncher

/**
 * Временные переключатели для замеров на телевизоре (tools/bench.py --dbg N):
 * `am start --ei dbg N`. Биты выключают части отрисовки.
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
    /** Вместо лаунчера — обычный View с движущимся квадратом: предел платформы. */
    const val PROBE_VIEW = 128
    /** Окно в RGB_565: вдвое меньше памяти на кадр. */
    const val RGB565 = 256
    /** Маленькое окно 640×360 вместо полноэкранного. */
    const val SMALL_WINDOW = 512
    /** Без ограничения 30 кадров/с: анимации на каждом vsync. */
    const val FULL_RATE = 1024
    fun off(bit: Int) = flags and bit != 0
}
