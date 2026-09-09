package com.serkantkn.zunelauncher.ui.keyboard

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.util.KeyboardLayouts

/**
 * Splits a category's emoji into a list without repeats: the grid keys its cells by the emoji
 * itself, and a repeated one would take the whole panel down.
 */
private fun emojiList(raw: String): List<String> =
    raw.split(" ").filter { it.isNotBlank() }.distinct()

/** One page of the emoji panel. [RECENT] is filled from what the user actually picked. */
enum class EmojiCategory(@StringRes val titleRes: Int, val symbol: String, val emoji: List<String>) {
    RECENT(R.string.emoji_recent, "🕘", emptyList()),
    SMILEYS(
        R.string.emoji_smileys, "🙂",
        emojiList(
            "😀 😃 😄 😁 😆 😅 😂 🤣 🙂 🙃 😉 😊 😇 🥰 😍 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤗 🤭 🤔 🤨 😐 😑 😶 😏 😒 🙄 😬 😌 😔 😪 😴 😷 🤒 🤕 🥳 🥺 😢 😭 😤 😠 😡 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤝 🙏 👍 👎 👌 ✌️ 🤞 👋 💪 🙌 👏 🫶"
        )
    ),
    PEOPLE(
        R.string.emoji_people, "🧑",
        emojiList(
            "👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 👨‍💻 👩‍💻 👨‍🍳 👩‍🍳 👮 🕵️ 💂 👷 🤴 👸 🧑‍🎓 🧑‍🏫 🧑‍⚕️ 🧑‍🔧 👨‍👩‍👧 👨‍👩‍👦 💑 💏 👤 🗣️ 👀 🧠 🦷 👅 👂 👃 🖐️ ✋ 🤚 🖖 🤙 🤛 🤜 ✍️ 💅"
        )
    ),
    ANIMALS(
        R.string.emoji_animals, "🐶",
        emojiList(
            "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🕷️ 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🐫 🦒 🐄 🐖 🐑 🐕 🐈 🌵 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍁 🍂 🌸 🌼 🌻 🌹 🌷 🌺"
        )
    ),
    FOOD(
        R.string.emoji_food, "🍎",
        emojiList(
            "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🥯 🍞 🥖 🧀 🥚 🍳 🧈 🥞 🧇 🥓 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🥙 🧆 🍗 🍖 🍤 🍣 🍱 🍜 🍝 🍛 🍚 🥘 🍲 🥗 🍿 🧂 🥫 🍦 🍰 🎂 🧁 🥧 🍫 🍬 🍭 🍩 🍪 ☕ 🍵 🥤 🧃 🧉 🍺 🍷 🥂"
        )
    ),
    ACTIVITY(
        R.string.emoji_activity, "⚽",
        emojiList(
            "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥅 🏒 🏑 🏹 🎣 🥊 🥋 ⛳ ⛸️ 🎿 🛷 🏂 🏋️ 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🚴 🚵 🎯 🎮 🕹️ 🎲 🧩 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🏆 🥇 🥈 🥉 🎖️ 🎪 🎉 🎊 🎈 🎁 🎀"
        )
    ),
    TRAVEL(
        R.string.emoji_travel, "✈️",
        emojiList(
            "🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🛵 🏍️ 🚲 🛴 🚨 🚔 🚍 🚝 🚄 🚅 🚈 🚂 🚆 🚊 🚉 ✈️ 🛫 🛬 🛩️ 💺 🚁 🚀 🛸 🚤 ⛵ 🛥️ 🚢 ⚓ 🗺️ 🧭 🏔️ ⛰️ 🌋 🏕️ 🏖️ 🏝️ 🏜️ 🏛️ 🏗️ 🏠 🏡 🏢 🏥 🏦 🏫 🏩 💒 🗼 🗽 ⛲ ⛺ 🌅 🌄 🌇 🌆 🌃 🌉 🎡 🎢 🎠"
        )
    ),
    OBJECTS(
        R.string.emoji_objects, "💡",
        emojiList(
            "⌚ 📱 💻 🖥️ 🖨️ ⌨️ 🖱️ 💽 💾 📷 📸 🎥 📺 📻 ⏰ ⏱️ ⌛ 🔋 🔌 💡 🔦 🕯️ 🧯 🛢️ 💸 💵 💳 🧾 💰 ⚖️ 🔧 🔨 ⚙️ 🧰 🧲 🔫 💊 💉 🩹 🚪 🪑 🛏️ 🚿 🛁 🧴 🧻 🧼 🧹 🔑 🗝️ 🔒 🔓 📎 📌 ✂️ 📏 📐 📚 📖 📓 📝 ✏️ 🖊️ 📧 📨 📦 📬 🗑️ 🔍 🔎"
        )
    ),
    SYMBOLS(
        R.string.emoji_symbols, "❤️",
        emojiList(
            "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 ✅ ❌ ❗ ❓ ⚠️ 🚫 ⭕ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🔺 🔻 ⭐ 🌟 ✨ ⚡ 🔥 💥 💫 💤 🎵 🎶 ➕ ➖ ➗ ✖️ ♾️ 🆗 🆕 🔝 🔙 ⏳ ⌚ ♻️ 🔔 🔕 📢 💬 💭 🗯️ ✔️ ☑️ 🔘 🔗 ➡️ ⬅️ ⬆️ ⬇️"
        )
    );

    companion object {
        /** Categories in the order the panel shows them. */
        val PAGES: List<EmojiCategory> = entries
    }
}

/**
 * Windows Phone style emoji board: a strip of category tabs on top, a flat grid below and the
 * usual ABC / backspace keys on the bottom row so typing can continue without leaving the panel.
 */
@Composable
fun EmojiPanel(
    recent: List<String>,
    palette: KeyboardPalette,
    height: Dp,
    rowHeight: Dp,
    onEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    var selected by rememberSaveable { mutableIntStateOf(if (recent.isEmpty()) 1 else 0) }
    val category = EmojiCategory.PAGES.getOrElse(selected) { EmojiCategory.SMILEYS }
    val emoji = remember(category, recent) {
        (if (category == EmojiCategory.RECENT) recent else category.emoji).distinct()
    }
    val gridState = rememberLazyGridState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(palette.board)
            .padding(bottom = bottomPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            EmojiCategory.PAGES.forEachIndexed { index, page ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(if (isSelected) palette.accent else palette.board)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { selected = index }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = page.symbol,
                        fontSize = 16.sp,
                        color = if (isSelected) androidx.compose.ui.graphics.Color.White else palette.text
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (emoji.isEmpty()) {
                Text(
                    text = stringResource(R.string.emoji_recent_empty),
                    color = palette.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(46.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(emoji, key = { index, symbol -> "$index:$symbol" }) { _, symbol ->
                        Box(
                            modifier = Modifier
                                .height(46.dp)
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onEmoji(symbol) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = symbol, fontSize = 24.sp)
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
            PanelKey(
                palette = palette,
                modifier = Modifier.weight(1.5f),
                onClick = onClose
            ) {
                Text(
                    text = KeyboardLayouts.LETTERS_LABEL,
                    color = palette.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            PanelKey(palette = palette, modifier = Modifier.weight(5f), onClick = { onEmoji(" ") }) {}
            PanelKey(palette = palette, modifier = Modifier.weight(1.5f), onClick = onBackspace) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                    tint = palette.text
                )
            }
        }
    }
}

/** A flat key used by the panels' bottom rows. */
@Composable
fun PanelKey(
    palette: KeyboardPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(2.dp)
            .background(palette.key)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
