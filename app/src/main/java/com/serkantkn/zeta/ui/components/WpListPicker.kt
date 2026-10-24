package com.serkantkn.zeta.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.ui.theme.LocalZuneColors

/**
 * A choice of one of a few, as a box that opens in place: closed it is a text box holding the
 * choice; touched, it grows down to list them all, the chosen one in the accent; touching one
 * picks it and the box closes round it. Back closes it without a choice.
 *
 * [header] is the small line above the box, as a text box's label is.
 */
@Composable
fun <T> WpListPicker(
    items: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    header: String? = null
) {
    val zuneColors = LocalZuneColors.current
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = open) { open = false }

    // Closed, the box is a text box at rest: grey, black words. Open, it is one being edited:
    // white, ringed with the accent.
    val restingFill = if (zuneColors.isDark) Color(0xFFBFBFBF) else Color(0xFFDEDEDE)
    val chosenLabel = label(selected)

    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                text = header,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = zuneColors.textMuted,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(tween(OPEN_MILLIS, easing = OpenEasing))
                .background(if (open) Color.White else restingFill)
                .border(BorderStroke(BORDER, if (open) zuneColors.accentColor else Color.Transparent))
        ) {
            if (!open) {
                PickerRow(
                    text = chosenLabel,
                    color = Color.Black,
                    description = header,
                    onClick = { open = true }
                )
            } else {
                items.forEach { item ->
                    val isChosen = item == selected
                    PickerRow(
                        text = label(item),
                        color = if (isChosen) zuneColors.accentColor else Color.Black,
                        selected = isChosen,
                        onClick = {
                            open = false
                            if (!isChosen) onSelect(item)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    text: String,
    color: Color,
    onClick: () -> Unit,
    selected: Boolean = false,
    description: String? = null
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics {
                role = Role.DropdownList
                this.selected = selected
                if (description != null) stateDescription = description
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val ROW_HEIGHT = 48.dp
private val BORDER = 2.dp
private const val OPEN_MILLIS = 260
private val OpenEasing = CubicBezierEasing(0.10f, 0.90f, 0.20f, 1.00f)
