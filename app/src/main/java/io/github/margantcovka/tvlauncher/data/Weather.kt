package io.github.margantcovka.tvlauncher.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/** Текущая погода для шапки: температура уже в выбранных единицах. */
class Weather(val temperature: Int, val condition: Condition, val city: String, val fahrenheit: Boolean)

enum class Condition { CLEAR, NIGHT, CLOUDY, OVERCAST, FOG, RAIN, SNOW, STORM }

/**
 * Погода по IP: город и координаты от ipapi.co (запасной — ipwho.is), сама погода от Open-Meteo.
 * Оба сервиса без ключей. Город кэшируется на сутки, погода — на [MAX_AGE_MS]; последнее значение
 * хранится, чтобы шапка показала его сразу после запуска, ещё до сети.
 */
class WeatherSource(context: Context) {

    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var fahrenheit: Boolean
        get() = prefs.getBoolean(KEY_FAHRENHEIT, false)
        set(value) = prefs.edit().putBoolean(KEY_FAHRENHEIT, value).remove(KEY_TIME).apply()

    /** Последняя сохранённая погода, если она в нужных единицах и не старше суток. */
    fun cached(): Weather? {
        if (!enabled || System.currentTimeMillis() - prefs.getLong(KEY_TIME, 0) > LOCATION_MAX_AGE_MS) return null
        val unitMatches = prefs.getBoolean(KEY_WEATHER_F, false) == fahrenheit
        val condition = prefs.getString(KEY_CONDITION, null)?.let { runCatching { Condition.valueOf(it) }.getOrNull() }
        if (!unitMatches || condition == null) return null
        return Weather(prefs.getInt(KEY_TEMP, 0), condition, prefs.getString(KEY_CITY, "").orEmpty(), fahrenheit)
    }

    fun isStale(): Boolean = System.currentTimeMillis() - prefs.getLong(KEY_TIME, 0) > MAX_AGE_MS

    /** Загружает погоду из сети. Блокирующий вызов — только не в главном потоке. */
    fun fetch(): Weather? {
        if (!enabled) return null
        val place = location() ?: return null
        return runCatching {
            val unit = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
            val json = get(
                "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
                    "&current=temperature_2m,weather_code,is_day$unit",
            )
            val current = json.getJSONObject("current")
            val weather = Weather(
                temperature = current.getDouble("temperature_2m").roundToInt(),
                condition = condition(current.getInt("weather_code"), current.optInt("is_day", 1) == 1),
                city = place.city,
                fahrenheit = fahrenheit,
            )
            prefs.edit()
                .putInt(KEY_TEMP, weather.temperature)
                .putString(KEY_CONDITION, weather.condition.name)
                .putBoolean(KEY_WEATHER_F, weather.fahrenheit)
                .putLong(KEY_TIME, System.currentTimeMillis())
                .apply()
            weather
        }.onFailure { Log.w(TAG, "weather", it) }.getOrNull()
    }

    private class Place(val lat: Double, val lon: Double, val city: String)

    private fun location(): Place? {
        if (System.currentTimeMillis() - prefs.getLong(KEY_LOCATION_TIME, 0) < LOCATION_MAX_AGE_MS && prefs.contains(KEY_LAT)) {
            return Place(prefs.getFloat(KEY_LAT, 0f).toDouble(), prefs.getFloat(KEY_LON, 0f).toDouble(), prefs.getString(KEY_CITY, "").orEmpty())
        }
        val place = runCatching {
            val json = get("https://ipapi.co/json/")
            Place(json.getDouble("latitude"), json.getDouble("longitude"), json.optString("city"))
        }.recoverCatching {
            Log.w(TAG, "ipapi.co", it)
            val json = get("https://ipwho.is/")
            check(json.optBoolean("success", true)) { json.optString("message") }
            Place(json.getDouble("latitude"), json.getDouble("longitude"), json.optString("city"))
        }.onFailure { Log.w(TAG, "location", it) }.getOrNull() ?: return null
        prefs.edit()
            .putFloat(KEY_LAT, place.lat.toFloat())
            .putFloat(KEY_LON, place.lon.toFloat())
            .putString(KEY_CITY, place.city)
            .putLong(KEY_LOCATION_TIME, System.currentTimeMillis())
            .apply()
        return place
    }

    private fun get(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "tvlauncher")
            connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode} from ${URL(url).host}" }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    /** Коды погоды WMO, как их отдаёт Open-Meteo. */
    private fun condition(code: Int, day: Boolean): Condition = when (code) {
        0, 1 -> if (day) Condition.CLEAR else Condition.NIGHT
        2 -> Condition.CLOUDY
        3 -> Condition.OVERCAST
        45, 48 -> Condition.FOG
        in 51..67, in 80..82 -> Condition.RAIN
        in 71..77, 85, 86 -> Condition.SNOW
        in 95..99 -> Condition.STORM
        else -> Condition.CLOUDY
    }

    companion object {
        const val MAX_AGE_MS = 30 * 60 * 1000L
        private const val LOCATION_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private const val TAG = "Weather"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FAHRENHEIT = "fahrenheit"
        private const val KEY_TEMP = "temp"
        private const val KEY_CONDITION = "condition"
        private const val KEY_WEATHER_F = "weather_f"
        private const val KEY_TIME = "time"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_CITY = "city"
        private const val KEY_LOCATION_TIME = "location_time"
    }
}
