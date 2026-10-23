package org.futo.inputmethod.latin.uix

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.theme.Typography

/**
 * The strip's chip for Smart corrections for typing: the newest fix ("an → and ↶", a tap
 * reverts it) and, with more than one, a "+N" badge that opens the review list.
 */
@Composable
fun RowScope.TypingFixChip(fixes: List<TypingFix>, onOpenList: () -> Unit) {
    val fix = fixes.lastOrNull() ?: return
    val manager = LocalManager.current
    val view = LocalView.current
    val revertLabel = stringResource(R.string.typing_fix_revert_description, fix.replacement, fix.original)
    Row(Modifier.fillMaxHeight().padding(vertical = 6.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = LocalKeyboardScheme.current.keyboardContainer, shape = RoundedCornerShape(8.dp)) {
            CompositionLocalProvider(LocalContentColor provides LocalKeyboardScheme.current.onKeyboardContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .clickable {
                                manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                                TypingFixes.revert(fix)
                            }
                            .clearAndSetSemantics {
                                role = Role.Button
                                contentDescription = revertLabel
                                onClick(action = { TypingFixes.revert(fix); true })
                            }
                            .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(fix.original) }
                                append("→")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(fix.replacement) }
                            },
                            style = Typography.Small, maxLines = 1
                        )
                        Icon(painterResource(R.drawable.undo), contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                    if (fixes.size > 1) {
                        val more = stringResource(R.string.typing_fix_more_description, fixes.size)
                        Box(
                            Modifier
                                .clickable {
                                    manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                                    onOpenList()
                                }
                                .clearAndSetSemantics {
                                    role = Role.Button
                                    contentDescription = more
                                    onClick(action = { onOpenList(); true })
                                }
                                .padding(start = 2.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text("+${fixes.size - 1}", style = Typography.Small.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

/** The review list: every fix of this text field in context, each revertable on its own. */
val TypingFixListAction = Action(
    icon = R.drawable.undo,
    name = R.string.typing_fix_list_title,
    canShowKeyboard = true,
    shownInEditor = false,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        object : ActionWindow() {
            override val onlyShowAboveKeyboard: Boolean get() = true
            override val positionIsUserManagable: Boolean get() = false

            @Composable
            override fun windowName(): String = stringResource(R.string.typing_fix_list_title)

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                val fixes = TypingFixes.fixes.value
                LaunchedEffect(Unit) { TypingFixes.refreshStale() }
                LaunchedEffect(fixes.isEmpty()) { if (fixes.isEmpty()) manager.closeActionWindow() }
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(fixes.asReversed(), key = { it.id }) { fix -> TypingFixRow(fix) }
                }
            }
        }
    }
)

@Composable
private fun TypingFixRow(fix: TypingFix) {
    val label = if (fix.stale) stringResource(R.string.typing_fix_stale_description, fix.original, fix.replacement)
        else stringResource(R.string.typing_fix_revert_description, fix.replacement, fix.original)
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (fix.stale) 0.4f else 1f)
            .clickable(enabled = !fix.stale) { TypingFixes.revert(fix) }
            .clearAndSetSemantics {
                contentDescription = label
                if (!fix.stale) {
                    role = Role.Button
                    onClick(action = { TypingFixes.revert(fix); true })
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            buildAnnotatedString {
                // an ellipsis only where the context was cut off
                if (fix.before.length >= TypingCorrections.CONTEXT_CHARS) append("…")
                append(fix.before.trimStart())
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(fix.original) }
                append(" → ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(fix.replacement) }
                append(fix.after.trimEnd())
                if (fix.after.length >= TypingCorrections.CONTEXT_CHARS) append("…")
            },
            style = Typography.Body.Regular, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(painterResource(R.drawable.undo), contentDescription = null, modifier = Modifier.padding(start = 12.dp).size(20.dp))
    }
}
