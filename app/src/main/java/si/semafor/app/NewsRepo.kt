package si.semafor.app

import android.content.Context
import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

data class NewsItem(
    val title: String,
    val description: String,
    val link: String,
    val dateMillis: Long
)

data class NewsResult(
    val items: List<NewsItem>?,
    val isOffline: Boolean
)

object NewsRepo {
    /** Prenese novice z RSS vira. Klicati samo na ozadni niti. */
    fun fetchNews(ctx: Context): NewsResult {
        val prefs = ctx.getSharedPreferences("news_cache", Context.MODE_PRIVATE)
        return try {
            val url = URL(Links.NEWS_FEED)
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("accept", "application/rss+xml, application/xml")
            c.requestMethod = "GET"

            if (c.responseCode != HttpURLConnection.HTTP_OK) throw java.io.IOException("HTTP ${c.responseCode}")
            val xmlText = c.inputStream.bufferedReader().use { it.readText() }
            val items = parseXml(xmlText)
            // An empty or unusable feed must never replace valid stored news.
            if (items.isEmpty()) throw java.io.IOException("Empty feed")
            prefs.edit().putString("xml", xmlText).putLong("time", System.currentTimeMillis())
                .apply()
            NewsResult(items, isOffline = false)
        } catch (e: Exception) {
            val cachedXml = prefs.getString("xml", null)
            if (cachedXml != null) {
                try {
                    val items = parseXml(cachedXml)
                    NewsResult(items, isOffline = true)
                } catch (e2: Exception) {
                    NewsResult(null, isOffline = true)
                }
            } else {
                NewsResult(null, isOffline = true)
            }
        }
    }

    private fun parseXml(xmlText: String): List<NewsItem> {
        val items = ArrayList<NewsItem>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xmlText.reader())

        var eventType = parser.eventType
        var inItem = false
        var title = ""
        var description = ""
        var link = ""
        var pubDateStr = ""

        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

        while (eventType != XmlPullParser.END_DOCUMENT && items.size < 15) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName != null && tagName.equals("item", ignoreCase = true)) {
                        inItem = true
                        title = ""
                        description = ""
                        link = ""
                        pubDateStr = ""
                    } else if (inItem && tagName != null) {
                        when {
                            tagName.equals("title", ignoreCase = true) -> title =
                                readTagText(parser)

                            tagName.equals("description", ignoreCase = true) -> description =
                                readTagText(parser)

                            tagName.equals("link", ignoreCase = true) -> link = readTagText(parser)
                            tagName.equals("pubDate", ignoreCase = true) -> pubDateStr =
                                readTagText(parser)
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (tagName != null && tagName.equals("item", ignoreCase = true) && inItem) {
                        inItem = false
                        val cleanTitle = cleanHtml(title)
                        val cleanDesc = cleanHtml(description)
                        val cleanLink = link.trim()
                        val dateMillis = try {
                            val parsedDate = dateFormat.parse(pubDateStr.trim())
                            parsedDate?.time ?: 0L
                        } catch (e: Exception) {
                            0L
                        }

                        if (cleanTitle.isNotEmpty() || cleanDesc.isNotEmpty()) {
                            items.add(NewsItem(cleanTitle, cleanDesc, cleanLink, dateMillis))
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return items
    }

    private fun readTagText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var eventType = parser.next()
        while (eventType == XmlPullParser.TEXT || eventType == XmlPullParser.CDSECT) {
            sb.append(parser.text)
            eventType = parser.next()
        }
        return sb.toString()
    }

    private fun cleanHtml(raw: String): String {
        if (raw.isBlank()) return ""
        val unescaped = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
        return unescaped.trim().replace(Regex("\\s+"), " ")
    }
}
