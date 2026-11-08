package cloud.xvps.converter

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Курсы хранятся как "сколько рублей стоит 1 единица валюты".
 * Источник — ЦБ РФ (зеркало cbr-xml-daily.ru), номинал уже учтён.
 */
data class RatesSnapshot(
    val date: String,
    val rates: Map<String, Double>,
    val offline: Boolean
)

object RatesRepository {

    private const val ENDPOINT = "https://www.cbr-xml-daily.ru/daily_json.js"
    private const val PREFS = "rates_cache"
    private const val KEY_JSON = "json"
    private const val KEY_DATE = "date"

    val CODES = listOf(
        "RUB", "USD", "EUR", "CNY", "KZT", "BYN",
        "UAH", "TRY", "GBP", "JPY", "AED", "GEL", "CHF", "PLN"
    )

    val NAMES = mapOf(
        "RUB" to "Российский рубль",
        "USD" to "Доллар США",
        "EUR" to "Евро",
        "CNY" to "Китайский юань",
        "KZT" to "Казахстанский тенге",
        "BYN" to "Белорусский рубль",
        "UAH" to "Украинская гривна",
        "TRY" to "Турецкая лира",
        "GBP" to "Фунт стерлингов",
        "JPY" to "Японская иена",
        "AED" to "Дирхам ОАЭ",
        "GEL" to "Грузинский лари",
        "CHF" to "Швейцарский франк",
        "PLN" to "Польский злотый"
    )

    val SYMBOLS = mapOf(
        "RUB" to "₽", "USD" to "$", "EUR" to "€", "CNY" to "¥",
        "GBP" to "£", "JPY" to "¥", "KZT" to "₸", "UAH" to "₴",
        "TRY" to "₺", "BYN" to "Br", "AED" to "د.إ", "GEL" to "₾",
        "CHF" to "Fr", "PLN" to "zł"
    )

    /** Резерв на случай отсутствия сети и пустого кэша. Приблизительные значения. */
    private val FALLBACK = mapOf(
        "RUB" to 1.0, "USD" to 79.0, "EUR" to 92.0, "CNY" to 11.0,
        "KZT" to 0.15, "BYN" to 26.5, "UAH" to 1.9, "TRY" to 1.95,
        "GBP" to 107.0, "JPY" to 0.53, "AED" to 21.5, "GEL" to 29.0,
        "CHF" to 98.0, "PLN" to 21.5
    )

    /** Сетевой запрос. Вызывать только из фонового потока. */
    fun fetch(context: Context): RatesSnapshot {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "CurrencyConverter/1.0 (Android)")
        }
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val snapshot = parse(body)
            save(context, body)
            return snapshot
        } finally {
            conn.disconnect()
        }
    }

    /** Кэш или резервные значения — работает без сети. */
    fun cached(context: Context): RatesSnapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_JSON, null)
        if (json != null) {
            runCatching { return parse(json).copy(offline = true) }
        }
        return RatesSnapshot(date = "—", rates = FALLBACK, offline = true)
    }

    private fun parse(body: String): RatesSnapshot {
        val root = JSONObject(body)
        val date = root.optString("Date", "").take(10)
        val valute = root.getJSONObject("Valute")
        val map = HashMap<String, Double>()
        map["RUB"] = 1.0
        for (code in CODES) {
            if (code == "RUB") continue
            val item = valute.optJSONObject(code) ?: continue
            val nominal = item.optInt("Nominal", 1).coerceAtLeast(1)
            val value = item.optDouble("Value", 0.0)
            if (value > 0) map[code] = value / nominal
        }
        return RatesSnapshot(date = date.ifEmpty { "—" }, rates = map, offline = false)
    }

    private fun save(context: Context, body: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, body)
            .putLong(KEY_DATE, System.currentTimeMillis())
            .apply()
    }
}
