package si.semafor.app

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * RemoteViews (home screen widgets) can only inflate a fixed list of classes. Anything else, for example a plain
 * <View>, makes the inflation fail ("Class not allowed to be inflated") and the whole widget stays empty.
 * Lint does not check this, so the widget layouts are checked here.
 */
class WidgetLayoutsTest {

    // Allowed on every API level this app supports (minSdk 26). CheckBox, RadioButton, RadioGroup and Switch are
    // only allowed from API 31, so they are deliberately not in this list.
    private val allowed = setOf(
        "FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout",
        "AnalogClock", "Button", "Chronometer", "ImageButton", "ImageView", "ProgressBar", "TextClock", "TextView",
        "ViewFlipper", "ListView", "GridView", "StackView", "AdapterViewFlipper"
    )

    private fun layoutFile(name: String): File {
        val candidates = listOf(
            File("src/main/res/layout/$name.xml"),
            File("app/src/main/res/layout/$name.xml")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw AssertionError("Layout $name.xml not found (working dir ${File(".").absoluteFile})")
    }

    private fun tags(node: Node, out: MutableList<String>) {
        if (node is Element) out.add(node.tagName)
        var child = node.firstChild
        while (child != null) {
            tags(child, out)
            child = child.nextSibling
        }
    }

    private fun assertRemoteViewsSafe(name: String) {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layoutFile(name))
        val found = ArrayList<String>()
        tags(doc.documentElement, found)
        val forbidden = found.filter { it !in allowed }.distinct()
        assertTrue("$name.xml uses elements RemoteViews cannot inflate: $forbidden", forbidden.isEmpty())
    }

    @Test
    fun smallWidgetLayoutIsInflatableByRemoteViews() = assertRemoteViewsSafe("widget_small")

    @Test
    fun wideWidgetLayoutIsInflatableByRemoteViews() = assertRemoteViewsSafe("widget_wide")

    @Test
    fun legacyWidgetLayoutIsInflatableByRemoteViews() = assertRemoteViewsSafe("widget")
}
