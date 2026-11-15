package si.semafor.app.mojelektro

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Minimal HTTP client of the Moj Elektro API (same approach as the rest of the app: HttpURLConnection + org.json,
 * no extra libraries). Blocking: call it only from a background thread / Dispatchers.IO.
 *
 * Security:
 * - The token is sent ONLY in the X-API-TOKEN header, ONLY over HTTPS, ONLY to [BASE_URL]'s host.
 * - Redirects are not followed, so the header can never be forwarded to another host.
 * - The token is never logged, put in a URL or included in an error.
 */
internal object MojElektroClient {
    /** Production environment from the official API description. */
    const val BASE_URL = "https://api.informatika.si/mojelektro/v1"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /** Upper bound of an answer. A month of 15-minute values of several registers stays far below this. */
    private const val MAX_BODY_BYTES = 8 * 1024 * 1024

    /** Raw outcome of one request: HTTP status and body (null for a network failure). */
    data class Response(val status: Int, val body: String?)

    fun meteringPoint(token: String, eimm: String): Response =
        get(token, "/merilno-mesto/" + pathSegment(eimm), emptyList())

    fun measurementPoint(token: String, gsrn: String): Response =
        get(token, "/merilna-tocka/" + pathSegment(gsrn), emptyList())

    fun readingTypes(token: String): Response = get(token, "/reading-type", emptyList())

    fun meterReadings(
        token: String,
        usagePoint: String,
        start: LocalDate,
        end: LocalDate,
        readingTypes: List<String>
    ): Response = get(token, "/meter-readings", meterReadingsQuery(usagePoint, start, end, readingTypes))

    /** Query of GET /meter-readings; every register is one "option=ReadingType=<code>" parameter. */
    internal fun meterReadingsQuery(
        usagePoint: String,
        start: LocalDate,
        end: LocalDate,
        readingTypes: List<String>
    ): List<Pair<String, String>> =
        listOf("usagePoint" to usagePoint, "startTime" to start.toString(), "endTime" to end.toString()) +
            readingTypes.map { "option" to "ReadingType=$it" }

    internal fun buildUrl(path: String, query: List<Pair<String, String>>): String {
        val q = query.joinToString("&") { (k, v) -> encode(k) + "=" + encode(v) }
        return BASE_URL + path + if (q.isEmpty()) "" else "?$q"
    }

    private fun get(token: String, path: String, query: List<Pair<String, String>>): Response {
        val url = URL(buildUrl(path, query))
        require(url.protocol == "https") { "HTTPS only" }
        var c: HttpURLConnection? = null
        return try {
            c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = CONNECT_TIMEOUT_MS
            c.readTimeout = READ_TIMEOUT_MS
            c.requestMethod = "GET"
            c.useCaches = false
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("X-API-TOKEN", token)
            val status = c.responseCode
            val stream = if (status in 200..299) c.inputStream else c.errorStream
            Response(status, stream?.let { readCapped(it) })
        } catch (e: SocketTimeoutException) {
            Response(STATUS_TIMEOUT, null)
        } catch (e: IOException) {
            Response(STATUS_NETWORK, null)
        } finally {
            c?.disconnect()
        }
    }

    /** Status used for "no HTTP answer at all". */
    const val STATUS_NETWORK = -1

    /** Status used for "the server did not answer in time" (connect or read timeout). */
    const val STATUS_TIMEOUT = -2

    private fun readCapped(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        input.use {
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BODY_BYTES) return null
                out.write(buf, 0, n)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun pathSegment(s: String): String = encode(s)
}

/**
 * Maps a raw [MojElektroClient.Response] to a result: 2xx is parsed with [parse], everything else becomes the error the
 * API documents for that status. Pure function (unit tested).
 */
internal fun <T> mapResponse(response: MojElektroClient.Response, parse: (String) -> T): MojElektroResult<T> =
    when (response.status) {
        MojElektroClient.STATUS_NETWORK -> MojElektroResult.Failure(MojElektroError.Network)
        MojElektroClient.STATUS_TIMEOUT -> MojElektroResult.Failure(MojElektroError.Timeout)
        in 200..299 -> {
            val body = response.body
            if (body.isNullOrBlank()) {
                MojElektroResult.Failure(MojElektroError.InvalidResponse)
            } else {
                try {
                    MojElektroResult.Success(parse(body))
                } catch (e: Exception) {
                    MojElektroResult.Failure(MojElektroError.InvalidResponse)
                }
            }
        }

        400 -> MojElektroResult.Failure(MojElektroError.InvalidRequest(MojElektroParser.errorCode(response.body)))
        401 -> MojElektroResult.Failure(MojElektroError.Unauthorized)
        403 -> MojElektroResult.Failure(MojElektroError.Forbidden)
        404 -> MojElektroResult.Failure(MojElektroError.NotFound)
        else -> MojElektroResult.Failure(MojElektroError.Server(response.status))
    }
