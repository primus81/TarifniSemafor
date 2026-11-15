package si.semafor.app

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Resolves [UiText] in plain JVM tests by reading the string resources (values/ = Slovenian, values-en/ = English),
 * so tests check the final texts the user sees in both languages. Resource ids are mapped to names through R.
 */
object TestStrings {
    class Lang(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>, val plural: (Int) -> String)

    private fun resDir(folder: String): File =
        listOf(File("src/main/res/$folder"), File("app/src/main/res/$folder")).first { it.isDirectory }

    private fun unescape(raw: String): String {
        var t = raw.trim()
        if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length - 1)
        return t.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
    }

    private fun load(folder: String, plural: (Int) -> String): Lang {
        val strings = HashMap<String, String>()
        val plurals = HashMap<String, Map<String, String>>()
        val formattedFalse = HashSet<String>()
        resDir(folder).listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!.forEach { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val s = doc.getElementsByTagName("string")
            for (i in 0 until s.length) {
                val e = s.item(i) as Element
                val name = e.getAttribute("name")
                strings[name] = unescape(e.textContent)
                if (e.getAttribute("formatted") == "false") formattedFalse += name
            }
            val p = doc.getElementsByTagName("plurals")
            for (i in 0 until p.length) {
                val e = p.item(i) as Element
                val items = e.getElementsByTagName("item")
                plurals[e.getAttribute("name")] = (0 until items.length).associate {
                    val item = items.item(it) as Element
                    item.getAttribute("quantity") to unescape(item.textContent)
                }
            }
        }
        unformatted += formattedFalse
        return Lang(strings, plurals, plural)
    }

    private val unformatted = HashSet<String>()

    val SL: Lang by lazy {
        load("values") { n ->
            when (n % 100) {
                1 -> "one"
                2 -> "two"
                3, 4 -> "few"
                else -> "other"
            }
        }
    }
    val EN: Lang by lazy { load("values-en") { n -> if (n == 1) "one" else "other" } }

    private val stringNames: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    }
    private val pluralNames: Map<Int, String> by lazy {
        R.plurals::class.java.fields.associate { it.getInt(null) to it.name }
    }

    fun resolve(text: UiText, lang: Lang): String = when (text) {
        is UiText.Raw -> text.text
        is UiText.Concat -> text.parts.joinToString("") { resolve(it, lang) }
        is UiText.Res -> {
            val name = stringNames.getValue(text.id)
            val template = lang.strings[name] ?: error("Missing string $name")
            if (name in unformatted) template else String.format(template, *args(text.args, lang))
        }
        is UiText.Plural -> {
            val name = pluralNames.getValue(text.id)
            val forms = lang.plurals[name] ?: error("Missing plurals $name")
            val template = forms[lang.plural(text.count)] ?: forms.getValue("other")
            String.format(template, *args(text.args, lang))
        }
    }

    private fun args(args: List<Any>, lang: Lang): Array<Any> =
        args.map { if (it is UiText) resolve(it, lang) else it }.toTypedArray()

    fun sl(text: UiText) = resolve(text, SL)
    fun en(text: UiText) = resolve(text, EN)
    fun sl(id: Int, vararg args: Any) = resolve(UiText.Res(id, args.toList()), SL)
    fun en(id: Int, vararg args: Any) = resolve(UiText.Res(id, args.toList()), EN)
}

/** Slovenian text of the block-change notification (the resource of [transitionMessage] with the new block). */
fun slTransition(newId: Int, previousId: Int, latenessMs: Long): String =
    TestStrings.sl(transitionMessage(newId, previousId, latenessMs), newId)
