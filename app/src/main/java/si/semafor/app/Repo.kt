package si.semafor.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Date

data class Advice(val device: String, val use: String, val state: Int)
data class Block(
    val id: Int, val from: Int, val to: Int,
    val load: String, val season: String, val advice: List<Advice>
)

data class Info(val now: Block, val next: Block, val time: Long, val fresh: Boolean)
data class BestBlock(val id: Int, val startMillis: Long)

/**
 * Reliability of the data that is shown.
 * FRESH: just retrieved from the official source.
 * CACHED: stored earlier, still covers the current hour and is not too old.
 * UNVERIFIED: stored data that can no longer be trusted (expired block or too old).
 */
enum class DataState { FRESH, CACHED, UNVERIFIED }

object Repo {
    private const val API = "https://www.uro.si/o/api/tarifnisemafor/v1/casovniblok"
    private val COLORS = intArrayOf(
        0xFF888888.toInt(), 0xFFE53935.toInt(), 0xFFF08A3C.toInt(),
        0xFFFDD835.toInt(), 0xFF9CCC4F.toInt(), 0xFF43A047.toInt()
    )

    private const val REUSE_WINDOW_MS = 30_000L
    private const val MAX_CACHE_AGE_MS = 12 * 3600 * 1000L

    /** Widgets cannot re-verify continuously: data from a successful update younger than this counts as current. */
    const val WIDGET_RECENT_MS = 30 * 60 * 1000L

    private val loadLock = Any()

    private var cachedBest: BestBlock? = null

    /** True if the block's hour interval contains the current hour. */
    fun coversNow(b: Block): Boolean {
        return SloTime.coversHour(SloTime.hourOfDay(), b.from, b.to)
    }

    fun dataState(info: Info): DataState {
        if (info.fresh) return DataState.FRESH
        val age = System.currentTimeMillis() - info.time
        return if (age in 0 until MAX_CACHE_AGE_MS && coversNow(info.now)) DataState.CACHED else DataState.UNVERIFIED
    }

    private fun urlFor(date: Date): URL {
        val ts = SloTime.formatApiTimestamp(date.time)
        return URL("$API?timestamp=" + URLEncoder.encode(ts, "UTF-8").replace("+", "%20"))
    }

    /** Single HTTP GET. Returns the body only for HTTP 200 with a non-blank body; null on any failure. */
    private fun httpGet(url: URL): String? {
        var c: HttpURLConnection? = null
        return try {
            c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("accept", "application/json")
            if (c.responseCode != HttpURLConnection.HTTP_OK) return null
            c.inputStream.bufferedReader().use { it.readText() }.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    fun color(id: Int) = COLORS[id.coerceIn(0, 5)]

    fun getMemoryCachedBest(): BestBlock? = cachedBest

    /** True if the last successful update happened less than [WIDGET_RECENT_MS] ago. */
    fun isRecent(info: Info): Boolean =
        (System.currentTimeMillis() - info.time) in 0 until WIDGET_RECENT_MS

    /** Stored best block if it still lies in the future (no network, no recalculation). */
    fun storedBest(ctx: Context): BestBlock? {
        val prefs = ctx.getSharedPreferences("semafor", Context.MODE_PRIVATE)
        val id = prefs.getInt("best_id", -1)
        val start = prefs.getLong("best_start", 0L)
        return if (id != -1 && start > System.currentTimeMillis()) BestBlock(id, start) else null
    }

    /** Vrne besedilo "ob HH:mm uri" če je danes, sicer "jutri ob HH:mm uri". */
    fun whenText(startMillis: Long): String {
        val timeStr = SloTime.formatHm(startMillis)
        val isToday = SloTime.dateOf(startMillis) == SloTime.today()
        return if (isToday) "ob $timeStr uri" else "jutri ob $timeStr uri"
    }

    /** Izračuna millisekunde naslednjega začetka določene ure. */
    fun nextStartMillis(nextHour: Int): Long {
        return SloTime.nextStartAfter(System.currentTimeMillis(), nextHour)
    }

    /** Prebere zadnje shranjene podatke iz SharedPreferences brez omrežnega klica. */
    fun cached(ctx: Context): Info? {
        val prefs = ctx.getSharedPreferences("semafor", Context.MODE_PRIVATE)
        val text = prefs.getString("json", null) ?: return null
        return try {
            parse(text, prefs.getLong("time", 0), false)
        } catch (e: Exception) {
            null
        }
    }

    /** Prenese podatke za podani čas brez shranjevanja v SharedPreferences. */
    fun fetchAt(date: Date): Info? {
        val text = httpGet(urlFor(date)) ?: return null
        return try {
            parse(text, System.currentTimeMillis(), true)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Loads current data. Requests are serialized, and a successful load younger than 30 s whose block
     * still covers the current hour is reused (no duplicate network traffic from screen, worker, receiver).
     * The stored data ("json" + "time" = time of last successful update) is replaced ONLY by a response
     * that was parsed and validated; on any failure the previous data is returned with fresh = false.
     */
    fun load(ctx: Context): Info? = synchronized(loadLock) {
        val prefs = ctx.getSharedPreferences("semafor", Context.MODE_PRIVATE)
        val saved = prefs.getString("json", null)
        val savedTime = prefs.getLong("time", 0L)

        if (saved != null && (System.currentTimeMillis() - savedTime) in 0 until REUSE_WINDOW_MS) {
            val reused = try {
                parse(saved, savedTime, true)
            } catch (e: Exception) {
                null
            }
            if (reused != null && coversNow(reused.now)) return@synchronized reused
        }

        val text = httpGet(urlFor(Date()))
        if (text != null) {
            try {
                val now = System.currentTimeMillis()
                val info = parse(text, now, true)
                prefs.edit().putString("json", text).putLong("time", now).apply()
                return@synchronized info
            } catch (e: Exception) {
                // Invalid or incomplete response: keep the previously stored data untouched.
            }
        }

        if (saved == null) null else try {
            parse(saved, savedTime, false)
        } catch (e: Exception) {
            null
        }
    }

    /** Poišče naslednji najugodnejši časovni blok v naslednjih 24 urah in ga shranjuje v SharedPreferences. */
    @Synchronized
    fun findBestUpcoming(ctx: Context, currentInfo: Info? = null): BestBlock? {
        val prefs = ctx.getSharedPreferences("semafor", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val savedId = prefs.getInt("best_id", -1)
        val savedStart = prefs.getLong("best_start", 0L)
        val savedCalcTime = prefs.getLong("best_calc_time", 0L)

        if (savedId != -1 && savedStart > now && (now - savedCalcTime) in 0 until 60 * 60 * 1000L) {
            val savedBest = BestBlock(savedId, savedStart)
            cachedBest = savedBest
            return savedBest
        }

        var best: BestBlock? = null
        var failed = false
        var t = Date(now)
        val startWindowMillis = now

        for (i in 0 until 10) {
            val info = fetchAt(t)
            if (info == null) {
                failed = true
                break
            }
            val startMillis = SloTime.nextStartAfter(t.time, info.next.from)
            if (startMillis - startWindowMillis > 24 * 3600 * 1000L) {
                break
            }

            val candidate = BestBlock(info.next.id, startMillis)
            val currBest = best
            if (currBest == null || candidate.id > currBest.id) {
                best = candidate
            }

            t = Date(startMillis + 60000L)
        }

        if (failed) {
            // Incomplete search: never overwrite a still valid stored result with a partial one.
            val keep =
                if (savedId != -1 && savedStart > now) BestBlock(savedId, savedStart) else null
            cachedBest = keep
            return keep
        }

        cachedBest = best
        if (best != null) {
            prefs.edit()
                .putInt("best_id", best.id)
                .putLong("best_start", best.startMillis)
                .putLong("best_calc_time", now)
                .apply()
        } else {
            prefs.edit()
                .remove("best_id")
                .remove("best_start")
                .remove("best_calc_time")
                .apply()
        }
        return best
    }

    internal fun parse(text: String, time: Long, fresh: Boolean): Info {
        val o = JSONObject(text)
        return Info(
            block(o.getJSONObject("casovniBlokTrenutni")),
            block(o.getJSONObject("casovniBlokNaslednji")),
            time,
            fresh
        )
    }

    private fun block(o: JSONObject): Block {
        val cb = o.getJSONObject("casovniBlok")
        val list = ArrayList<Advice>()
        val arr = o.getJSONArray("nasveti")
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            list.add(
                Advice(
                    a.getString("vrstaNaprave"),
                    a.getString("vrstaRabe"),
                    a.getInt("stanje")
                )
            )
        }
        val id = cb.getInt("casovniBlokId")
        val from = cb.getInt("uraOd")
        val to = cb.getInt("uraDo")
        // Sanity checks: reject unexpected values instead of showing them.
        require(id in 1..5) { "Unexpected block id: $id" }
        require(from in 0..24 && to in 0..24) { "Unexpected hours: $from-$to" }
        return Block(
            id, from, to,
            o.getJSONObject("obremenjenostOmrezja").getString("naziv").trim(),
            o.getJSONObject("sezonaCasovnegaBloka").getString("naziv"), list
        )
    }
}
