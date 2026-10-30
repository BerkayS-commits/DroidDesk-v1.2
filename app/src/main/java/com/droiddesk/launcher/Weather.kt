package com.droiddesk.launcher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

class WeatherInfo(val city: String, val tempC: Int, val code: Int, val fetchedAt: Long)

/**
 * Optional weather widget data from Open-Meteo (free, no API key). Only used after the user enters a
 * city name; that name is sent to geocoding-api.open-meteo.com, nothing else is transmitted.
 */
object WeatherClient {

    fun fetch(city: String, lang: String): WeatherInfo? {
        return try {
            val geo = get(
                "https://geocoding-api.open-meteo.com/v1/search?name=" +
                    URLEncoder.encode(city, "UTF-8") + "&count=1&language=" + lang
            )
            val results = JSONObject(geo).optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val first = results.getJSONObject(0)
            val lat = first.getDouble("latitude")
            val lon = first.getDouble("longitude")
            val name = first.optString("name", city)
            val body = get(
                "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
                    "&current=temperature_2m,weather_code&timezone=auto"
            )
            val cur = JSONObject(body).getJSONObject("current")
            WeatherInfo(
                name,
                cur.getDouble("temperature_2m").roundToInt(),
                cur.getInt("weather_code"),
                System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Emoji for a WMO weather code. */
    fun emoji(code: Int): String = when (code) {
        0 -> "☀"
        1, 2 -> "⛅"
        3 -> "☁"
        45, 48 -> "🌫"
        in 51..57 -> "🌦"
        in 61..67, in 80..82 -> "🌧"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⛈"
        else -> "☁"
    }

    fun descriptionRes(code: Int): Int = when (code) {
        0 -> R.string.wx_clear
        1, 2 -> R.string.wx_partly
        3 -> R.string.wx_cloudy
        45, 48 -> R.string.wx_fog
        in 51..57 -> R.string.wx_drizzle
        in 61..67, in 80..82 -> R.string.wx_rain
        in 71..77, 85, 86 -> R.string.wx_snow
        in 95..99 -> R.string.wx_storm
        else -> R.string.wx_cloudy
    }
}
