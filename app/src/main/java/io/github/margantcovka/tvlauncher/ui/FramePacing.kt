package io.github.margantcovka.tvlauncher.ui

import androidx.compose.runtime.MonotonicFrameClock

/**
 * Часы кадров, которые пропускают каждый второй vsync: анимации идут ровно 30 кадров в секунду.
 *
 * Композитор этого телевизора (MStar HWC) не успевает показывать полноэкранное окно на каждом
 * vsync: даже пустая анимация получает ~45 кадров/с вперемешку 16 и 33 мс, а очередь буферов
 * копит ~65 мс задержки. Ровные 30 кадров — каждый кадр ровно два vsync — дают равномерное
 * движение и пустую очередь. Анимации зависят от времени, так что их скорость не меняется.
 */
class HalfRateFrameClock(private val base: MonotonicFrameClock) : MonotonicFrameClock {

    /** Время принятого vsync: все ожидающие в этом же vsync получают кадр, следующий пропускается. */
    private var acceptedFrame = 0L

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        while (true) {
            val frame = base.withFrameNanos { it }
            if (acceptedFrame == 0L || frame == acceptedFrame || frame - acceptedFrame >= MIN_INTERVAL_NS) {
                acceptedFrame = frame
                return onFrame(frame)
            }
        }
    }

    private companion object {
        /** Чуть меньше двух vsync, чтобы дрожание отметок времени не съедало кадр. */
        const val MIN_INTERVAL_NS = 25_000_000L
    }
}
