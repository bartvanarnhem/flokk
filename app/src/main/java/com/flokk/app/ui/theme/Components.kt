package com.flokk.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A tap target with no ripple: flokk marks state with fills and outlines instead. */
fun Modifier.plainClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}

/**
 * A square header button holding one icon. [active] marks a toggle or a panel that's open
 * (the keyboard, the new-workspace picker, settings) with a raised fill and the accent.
 */
@Composable
fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 34.dp,
    iconSize: Dp = 22.dp,
    tint: Color = Flokk.textSecondary,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(if (active) Flokk.raised else Color.Transparent, shape)
            .plainClickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (active) Flokk.accent else tint, modifier = Modifier.size(iconSize))
    }
}

/** The warm dark card everything on the connections and form screens sits in. */
@Composable
fun FlokkCard(
    modifier: Modifier = Modifier,
    background: Color = Flokk.card,
    border: Color? = null,
    radius: Dp = 12.dp,
    padding: Dp = 12.dp,
    spacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, shape)
            .then(if (border != null) Modifier.border(BorderStroke(1.dp, border), shape) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** The amber call to action, e.g. "Save connection" or "Open workspaces". */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    radius: Dp = 10.dp,
    fontSize: Float = 16f,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    background: Color = Flokk.accent,
) {
    ButtonBox(
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .background(background, RoundedCornerShape(radius))
            .plainClickable(enabled = enabled, onClick = onClick),
    ) {
        ButtonContent(text, Flokk.onAccent, FontWeight.SemiBold, fontSize, leadingIcon, trailingIcon, gap = 8.dp)
    }
}

/** The hairline-outlined secondary action, e.g. "Cancel". */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    radius: Dp = 10.dp,
    fontSize: Float = 16f,
    contentColor: Color = Flokk.textSecondary,
    leadingIcon: ImageVector? = null,
) {
    ButtonBox(
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .border(BorderStroke(1.dp, Flokk.border), RoundedCornerShape(radius))
            .plainClickable(enabled = enabled, onClick = onClick),
    ) {
        ButtonContent(text, contentColor, FontWeight.Medium, fontSize, leadingIcon, null, gap = 6.dp, iconSize = 16.dp)
    }
}

/** A filled, low-emphasis button: the in-card Edit/Disconnect/Delete row, Copy, Install. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 34.dp,
    radius: Dp = 8.dp,
    fontSize: Float = 14f,
    background: Color = Flokk.raised,
    contentColor: Color = Flokk.text,
    fontWeight: FontWeight = FontWeight.Medium,
    leadingIcon: ImageVector? = null,
    iconSize: Dp = 18.dp,
) {
    ButtonBox(
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.6f)
            .background(background, RoundedCornerShape(radius))
            .plainClickable(enabled = enabled, onClick = onClick),
    ) {
        ButtonContent(text, contentColor, fontWeight, fontSize, leadingIcon, null, gap = 6.dp, iconSize = iconSize)
    }
}

@Composable
private fun ButtonBox(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ButtonContent(
    text: String,
    color: Color,
    weight: FontWeight,
    fontSize: Float,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
    gap: Dp,
    iconSize: Dp = 20.dp,
) {
    if (leadingIcon != null) {
        Icon(leadingIcon, null, tint = color, modifier = Modifier.padding(end = gap).size(iconSize))
    }
    Text(
        text = text,
        style = TextStyle(fontFamily = Flokk.sans, fontWeight = weight, fontSize = fontSize.sp, color = color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (trailingIcon != null) {
        Icon(trailingIcon, null, tint = color, modifier = Modifier.padding(start = gap).size(iconSize))
    }
}

/** The dashed, low-emphasis "+ Add connection" row. */
@Composable
fun DashedButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = Flokk.border,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                )
            }
            .plainClickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Flokk.textSecondary, modifier = Modifier.padding(end = 8.dp).size(20.dp))
        Text(text, style = Flokk.bodyMedium.copy(color = Flokk.textSecondary))
    }
}

/** A 12sp semibold label sitting above a field or control. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = Flokk.fieldLabel, modifier = modifier)
}

/** A labelled field with an optional label above and helper text below. */
@Composable
fun LabelledField(
    label: String?,
    modifier: Modifier = Modifier,
    helper: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) FieldLabel(label)
        content()
        if (helper != null) Text(helper, style = Flokk.small.copy(color = Flokk.textFaint))
    }
}

/**
 * A 40dp rounded input; the outline lights up amber while focused. [background] is [Flokk.card]
 * on the page and [Flokk.background] inside a card.
 */
@Composable
fun FlokkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper: String? = null,
    mono: Boolean = false,
    singleLine: Boolean = true,
    minHeight: Dp = 40.dp,
    background: Color = Flokk.card,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val textStyle = if (mono) {
        TextStyle(fontFamily = Flokk.mono, fontSize = if (singleLine) 15.sp else 12.5.sp, color = Flokk.text)
    } else {
        Flokk.input
    }
    LabelledField(label = label, helper = helper, modifier = modifier) {
        val shape = RoundedCornerShape(10.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(background, shape)
                .border(BorderStroke(1.dp, if (focused) Flokk.accent else Flokk.border), shape)
                .padding(
                    start = if (singleLine) 16.dp else 14.dp,
                    end = if (trailing != null) 4.dp else 16.dp,
                    top = if (singleLine) 0.dp else 12.dp,
                    bottom = if (singleLine) 0.dp else 12.dp,
                ),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(text = placeholder, style = textStyle.copy(color = Flokk.textFaint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    enabled = enabled,
                    textStyle = textStyle,
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    cursorBrush = SolidColor(Flokk.accent),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                )
            }
            trailing?.invoke()
        }
    }
}

/** One option of a [SegmentedControl]: a label with an optional icon or custom leading glyph. */
class Segment(
    val label: String,
    val icon: ImageVector? = null,
    val leading: (@Composable (color: Color) -> Unit)? = null,
)

/**
 * An N-way choice on a dark track, the selected option filled light with dark text. The
 * track is [Flokk.card] on the page, [Flokk.background] inside a card.
 */
@Composable
fun SegmentedControl(
    options: List<Segment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    track: Color = Flokk.card,
    height: Dp = 32.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(track, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val color = if (selected) Flokk.background else Flokk.textSecondary
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .background(if (selected) Flokk.selectedFill else Color.Transparent, RoundedCornerShape(7.dp))
                    .plainClickable { onSelect(index) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    option.leading != null -> { option.leading.invoke(color); Box(Modifier.size(6.dp)) }
                    option.icon != null -> Icon(option.icon, null, tint = color, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                }
                Text(option.label, style = Flokk.button.copy(color = color), maxLines = 1)
            }
        }
    }
}

/** A 44×26 pill switch: amber with the knob right when on, grey with it left when off. */
@Composable
fun FlokkSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .background(if (checked) Flokk.accent else Flokk.border, RoundedCornerShape(13.dp))
            .plainClickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).background(Flokk.background, CircleShape))
    }
}

/** A full-width settings row: title (and subtitle), with [trailing] at the end, tappable as a whole. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(thickness = 1.dp, color = Flokk.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .then(if (onClick != null) Modifier.plainClickable(onClick = onClick) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = Flokk.bodyMedium)
                if (subtitle != null) Text(subtitle, style = Flokk.small)
            }
            trailing()
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
) {
    SettingsRow(title = title, modifier = modifier, divider = divider, onClick = { onCheckedChange(!checked) }) {
        FlokkSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A 32dp selectable chip, filled light when selected — the jump host and reuse-key pickers. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(32.dp)
            .background(if (selected) Flokk.selectedFill else Flokk.card, RoundedCornerShape(8.dp))
            .plainClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Flokk.body.copy(color = if (selected) Flokk.background else Flokk.textSecondary), maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/**
 * A confirmation that opens in place instead of a dialog: title, one line of context and a
 * Cancel / destructive pair.
 */
@Composable
fun InlineConfirm(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Flokk.raised,
    radius: Dp = 10.dp,
    titleSize: Float = 15f,
    buttonHeight: Dp = 34.dp,
    spacing: Dp = 12.dp,
) {
    FlokkCard(modifier = modifier, background = background, radius = radius, spacing = spacing) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Flokk.cardTitle.copy(fontSize = titleSize.sp))
            Text(message, style = Flokk.small)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton(text = "Cancel", onClick = onCancel, height = buttonHeight, radius = 8.dp, fontSize = 14f, modifier = Modifier.weight(1f))
            PrimaryButton(
                text = confirmLabel,
                onClick = onConfirm,
                height = buttonHeight,
                radius = 8.dp,
                fontSize = 14f,
                background = Flokk.red,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A small icon + text line, e.g. "ⓘ No changes to save yet" or an accent text link. */
@Composable
fun IconLine(
    icon: ImageVector,
    text: String,
    color: Color = Flokk.textMuted,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.then(if (onClick != null) Modifier.plainClickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = Flokk.small.copy(color = color))
    }
}

