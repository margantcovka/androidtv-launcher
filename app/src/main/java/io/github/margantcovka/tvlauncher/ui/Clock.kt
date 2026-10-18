package io.github.margantcovka.tvlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Приветствие, дата и время для шапки главного экрана. */
class ClockText(val greeting: String, val date: String, val time: String)

/**
 * Текущее время, обновляется системным сигналом раз в минуту (ACTION_TIME_TICK) и при смене
 * времени или часового пояса — без своего таймера, который после сна ТВ отстаёт.
 */
@Composable
fun rememberClockText(): State<ClockText> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(clockText(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { state.value = clockText(context) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        state.value = clockText(context)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

private val russian = Locale("ru")

private fun clockText(context: Context): ClockText {
    val now = Date()
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
    val timePattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
    return ClockText(
        greeting = greeting,
        date = SimpleDateFormat("EEEE, d MMMM", russian).format(now),
        time = SimpleDateFormat(timePattern, russian).format(now),
    )
}
