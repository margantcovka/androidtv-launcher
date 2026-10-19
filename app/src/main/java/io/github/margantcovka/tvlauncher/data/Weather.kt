package io.github.margantcovka.tvlauncher.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/** Текущая погода для шапки: температура уже в выбранных единицах. */
class Weather(val temperature: Int, val condition: Condition, val city: String, val fahrenheit: Boolean)

enum class Condition { CLEAR, NIGHT, CLOUDY, OVERCAST, FOG, RAIN, SNOW, STORM }

/** Город для погоды: из поиска в настройках или определённый по IP. */
class Place(val lat: Double, val lon: Double, val city: String, val region: String = "")

/**
 * Погода без API-ключей. Город — выбранный в настройках, иначе по IP (ipapi.co, запасной — ipwho.is,
 * кэш на сутки). Погода — Open-Meteo, а если он не отвечает, MET Norway. Последнее значение
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

    /** Город, выбранный вручную, или null — определять по IP. */
    var manualPlace: Place?
        get() = if (!prefs.getBoolean(KEY_MANUAL, false)) null else Place(
            prefs.getFloat(KEY_MANUAL_LAT, 0f).toDouble(),
            prefs.getFloat(KEY_MANUAL_LON, 0f).toDouble(),
            prefs.getString(KEY_MANUAL_CITY, "").orEmpty(),
        )
        set(place) {
            val edit = prefs.edit().remove(KEY_TIME).putBoolean(KEY_MANUAL, place != null)
            if (place != null) {
                edit.putFloat(KEY_MANUAL_LAT, place.lat.toFloat())
                    .putFloat(KEY_MANUAL_LON, place.lon.toFloat())
                    .putString(KEY_MANUAL_CITY, place.city)
            }
            edit.apply()
        }

    /** Город, определённый по IP в последний раз (для подписи «Автоматически (…)»). */
    fun ipCity(): String? = prefs.getString(KEY_CITY, null)?.takeIf { it.isNotBlank() }

    /** Последняя сохранённая погода, если она для того же города, в нужных единицах и не старше суток. */
    fun cached(): Weather? {
        if (!enabled || System.currentTimeMillis() - prefs.getLong(KEY_TIME, 0) > LOCATION_MAX_AGE_MS) return null
        val unitMatches = prefs.getBoolean(KEY_WEATHER_F, false) == fahrenheit
        val condition = prefs.getString(KEY_CONDITION, null)?.let { runCatching { Condition.valueOf(it) }.getOrNull() }
        if (!unitMatches || condition == null) return null
        return Weather(prefs.getInt(KEY_TEMP, 0), condition, (prefs.getString(KEY_WEATHER_CITY, null) ?: prefs.getString(KEY_CITY, "")).orEmpty(), fahrenheit)
    }

    fun isStale(): Boolean = System.currentTimeMillis() - prefs.getLong(KEY_TIME, 0) > MAX_AGE_MS

    /** Загружает погоду из сети. Блокирующий вызов — только не в главном потоке. */
    fun fetch(): Weather? {
        if (!enabled) return null
        val place = manualPlace ?: ipPlace() ?: return null
        val weather = runCatching { openMeteo(place) }
            .recoverCatching { Log.w(TAG, "open-meteo", it); metNorway(place) }
            .onFailure { Log.w(TAG, "met.no", it) }
            .getOrNull() ?: return null
        prefs.edit()
            .putInt(KEY_TEMP, weather.temperature)
            .putString(KEY_CONDITION, weather.condition.name)
            .putString(KEY_WEATHER_CITY, weather.city)
            .putBoolean(KEY_WEATHER_F, weather.fahrenheit)
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
        return weather
    }

    /** Поиск города по названию (геокодер Open-Meteo), названия — на языке системы. */
    fun search(query: String): List<Place> {
        val name = query.trim()
        if (name.length < 2) return emptyList()
        val language = Locale.getDefault().language
        val json = get(
            "https://geocoding-api.open-meteo.com/v1/search?count=10&language=$language&name=" +
                URLEncoder.encode(name, "UTF-8"),
        )
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            val region = listOf(r.optString("admin1"), r.optString("country"))
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
            Place(r.getDouble("latitude"), r.getDouble("longitude"), r.getString("name"), region)
        }
    }

    private fun openMeteo(place: Place): Weather {
        val unit = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
        val json = get(
            "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
                "&current=temperature_2m,weather_code,is_day$unit",
        )
        val current = json.getJSONObject("current")
        return Weather(
            temperature = current.getDouble("temperature_2m").roundToInt(),
            condition = wmoCondition(current.getInt("weather_code"), current.optInt("is_day", 1) == 1),
            city = place.city,
            fahrenheit = fahrenheit,
        )
    }

    /** MET Norway отдаёт только Цельсий и требует User-Agent с контактом и не больше 4 знаков в координатах. */
    private fun metNorway(place: Place): Weather {
        val lat = String.format(Locale.US, "%.4f", place.lat)
        val lon = String.format(Locale.US, "%.4f", place.lon)
        val json = get("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$lat&lon=$lon")
        val now = json.getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data")
        val celsius = now.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")
        val symbol = (now.optJSONObject("next_1_hours") ?: now.optJSONObject("next_6_hours"))
            ?.optJSONObject("summary")?.optString("symbol_code").orEmpty()
        return Weather(
            temperature = (if (fahrenheit) celsius * 9 / 5 + 32 else celsius).roundToInt(),
            condition = metCondition(symbol),
            city = place.city,
            fahrenheit = fahrenheit,
        )
    }

    private fun ipPlace(): Place? {
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
            connection.setRequestProperty("User-Agent", USER_AGENT)
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode} from ${URL(url).host}" }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    /** Коды погоды WMO, как их отдаёт Open-Meteo. */
    private fun wmoCondition(code: Int, day: Boolean): Condition = when (code) {
        0, 1 -> if (day) Condition.CLEAR else Condition.NIGHT
        2 -> Condition.CLOUDY
        3 -> Condition.OVERCAST
        45, 48 -> Condition.FOG
        in 51..67, in 80..82 -> Condition.RAIN
        in 71..77, 85, 86 -> Condition.SNOW
        in 95..99 -> Condition.STORM
        else -> Condition.CLOUDY
    }

    /** Символы MET Norway: clearsky_day, fair_night, partlycloudy_day, cloudy, fog, lightrain, heavysnowandthunder… */
    private fun metCondition(symbol: String): Condition {
        val night = symbol.endsWith("_night") || symbol.endsWith("_polartwilight")
        return when {
            "thunder" in symbol -> Condition.STORM
            "snow" in symbol || "sleet" in symbol -> Condition.SNOW
            "rain" in symbol -> Condition.RAIN
            symbol.startsWith("fog") -> Condition.FOG
            symbol.startsWith("partlycloudy") -> Condition.CLOUDY
            symbol.startsWith("cloudy") -> Condition.OVERCAST
            symbol.startsWith("clearsky") || symbol.startsWith("fair") -> if (night) Condition.NIGHT else Condition.CLEAR
            else -> Condition.CLOUDY
        }
    }

    companion object {
        const val MAX_AGE_MS = 30 * 60 * 1000L
        private const val LOCATION_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private const val TAG = "Weather"
        private const val USER_AGENT = "tvlauncher/0.3 github.com/margantcovka/androidtv-launcher"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FAHRENHEIT = "fahrenheit"
        private const val KEY_TEMP = "temp"
        private const val KEY_CONDITION = "condition"
        private const val KEY_WEATHER_CITY = "weather_city"
        private const val KEY_WEATHER_F = "weather_f"
        private const val KEY_TIME = "time"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_CITY = "city"
        private const val KEY_LOCATION_TIME = "location_time"
        private const val KEY_MANUAL = "manual"
        private const val KEY_MANUAL_LAT = "manual_lat"
        private const val KEY_MANUAL_LON = "manual_lon"
        private const val KEY_MANUAL_CITY = "manual_city"
    }
}
