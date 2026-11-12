package si.semafor.app.ui.components

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import si.semafor.app.R
import si.semafor.app.ui.theme.Palette

/**
 * Programmatic view helpers still needed by the screens that have not been migrated to Compose yet
 * (Home, Novice, Cenik). The Compose screens use ui/compose/components instead.
 * Every function takes a [Context] only; nothing here knows about MainActivity.
 */
internal object UiKit {

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun text(ctx: Context, s: String, size: Float, bold: Boolean = false, color: Int? = null): TextView =
        TextView(ctx).apply {
            text = s
            textSize = size
            if (bold) setTypeface(null, Typeface.BOLD)
            if (color != null) setTextColor(color)
        }

    /** Rounded surface with a thin border: the background of every card. */
    fun cardBackground(ctx: Context): GradientDrawable = GradientDrawable().apply {
        setColor(Palette.SURFACE)
        cornerRadius = dp(ctx, 16).toFloat()
        setStroke(dp(ctx, 1), Palette.BORDER)
    }

    /**
     * Header of every secondary screen: a small round back arrow (36 dp circle inside a 48 dp touch target, shifted so
     * the circle lines up with the content edge) followed by the title and an optional grey line under it.
     * Replaces the big "Domov" button that used to sit above the title.
     */
    fun screenHeader(
        ctx: Context,
        title: String,
        subtitle: String? = null,
        bottomMarginDp: Int = 12,
        onBack: () -> Unit
    ): View {
        val back = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 48)).apply { marginStart = -dp(ctx, 6) }
            isClickable = true
            setOnClickListener { onBack() }
            addView(
                ImageView(ctx).apply {
                    setImageResource(R.drawable.ic_arrow_back)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Palette.BORDER)
                    }
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36), Gravity.CENTER)
            )
            asButton("Nazaj na domači zaslon")
        }
        val titles = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(screenTitle(ctx, title))
            if (subtitle != null) {
                addView(text(ctx, subtitle, 12f, false, Palette.TEXT_SECONDARY).apply {
                    setPadding(0, dp(ctx, 2), 0, 0)
                })
            }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(ctx, bottomMarginDp) }
            addView(back)
            addView(titles)
        }
    }

    /** Screen title (18 sp, bold) announced as a heading by TalkBack. */
    fun screenTitle(ctx: Context, title: String): TextView =
        text(ctx, title, 18f, true, Palette.TEXT_PRIMARY).apply {
            ViewCompat.setAccessibilityHeading(this, true)
        }

    /** One look for loading, offline and error states: card with a title, an optional explanation and an optional warning. */
    fun stateCard(ctx: Context, title: String, detail: String? = null, warning: String? = null): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16))
            background = cardBackground(ctx)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(ctx, 8) }
        }
        card.addView(text(ctx, title, 16f, true, Palette.TEXT_PRIMARY))
        if (detail != null) {
            card.addView(text(ctx, detail, 13f, false, Palette.TEXT_SECONDARY).apply {
                setLineSpacing(0f, 1.15f)
                setPadding(0, dp(ctx, 6), 0, 0)
            })
        }
        if (warning != null) {
            card.addView(text(ctx, warning, 13f, true, 0xFFFDD835.toInt()).apply {
                setLineSpacing(0f, 1.15f)
                setPadding(0, dp(ctx, 8), 0, 0)
            })
        }
        return card
    }
}

/** Clickable non-Button views announce themselves as buttons and are reachable with keyboard / switch access. */
internal fun View.asButton(description: String? = null) {
    isFocusable = true
    if (description != null) contentDescription = description
    ViewCompat.setAccessibilityDelegate(
        this,
        object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        })
}

/** Clickable text that opens a web page: announced as a link ("povezava"), focusable for keyboard / switch access. */
internal fun View.asLink() {
    isFocusable = true
    ViewCompat.setAccessibilityDelegate(
        this,
        object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.roleDescription = "povezava"
            }
        })
}
