package si.semafor.app

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext

/**
 * A text that is resolved only where it is shown, in the language of that moment. ViewModels and pure logic return
 * [UiText] instead of finished strings, so switching the language never leaves old texts on screen.
 * Arguments may be other [UiText]s (resolved first).
 */
@Immutable
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    /** Text that is already final (data from an official source, user input). */
    data class Raw(val text: String) : UiText

    /** Several texts one after the other (sentences of one explanation). */
    data class Concat(val parts: List<UiText>) : UiText

    fun resolve(ctx: Context): String = when (this) {
        is Res -> ctx.getString(id, *resolveArgs(ctx, args))
        is Plural -> ctx.resources.getQuantityString(id, count, *resolveArgs(ctx, args))
        is Raw -> text
        is Concat -> parts.joinToString("") { it.resolve(ctx) }
    }

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = Plural(id, count, args.toList())

        private fun resolveArgs(ctx: Context, args: List<Any>): Array<Any> =
            args.map { if (it is UiText) it.resolve(ctx) else it }.toTypedArray()
    }
}

/** [UiText] in the composition's language. */
@Composable
fun UiText.text(): String = resolve(LocalContext.current)
