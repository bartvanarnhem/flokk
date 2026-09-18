package com.orbisk.flokk.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A rounded-corner, single-line-drawn box with an optional title cut into the top
 * border, mimicking a tmux/ncurses pane frame. Deliberately not a Material Card:
 * no elevation, no ripple — just a hairline outline, matching the design mocks' 6dp
 * rounded connection/workspace cards.
 */
@Composable
fun TuiBox(
    modifier: Modifier = Modifier,
    borderColor: Color = Tui.border,
    cornerRadius: androidx.compose.ui.unit.Dp = 6.dp,
    title: String? = null,
    titleStyle: TextStyle = Tui.label,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(8.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Box(modifier = modifier) {
        // matchParentSize(), not fillMaxSize(): this Canvas is purely decorative (the border),
        // so it must NOT participate in sizing the Box — under an unbounded-height parent
        // (e.g. a LazyColumn item), a fillMaxSize() sibling here collapses to zero instead of
        // matching whatever size the real content below ends up with.
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = with(density) { 1.dp.toPx() }
            val radiusPx = with(density) { cornerRadius.toPx() }
            val titleLayout = title?.let { measurer.measure(it, titleStyle) }
            val gap = with(density) { 12.dp.toPx() }
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
                style = Stroke(width = strokeWidth),
            )
            if (titleLayout != null) {
                // Punch a background-colored gap in the top border, then draw the label over it.
                val labelWidth = titleLayout.size.width.toFloat() + with(density) { 8.dp.toPx() }
                drawLine(
                    color = Tui.background,
                    start = Offset(gap - with(density) { 4.dp.toPx() }, 0f),
                    end = Offset(gap + labelWidth, 0f),
                    strokeWidth = strokeWidth + with(density) { 2.dp.toPx() },
                )
                drawText(
                    titleLayout,
                    topLeft = Offset(gap, -(titleLayout.size.height / 2f)),
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
            content()
        }
    }
}

/** A single-line monospace input with the TUI's hairline-bordered look, label above it. */
@Composable
fun TuiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier) {
        androidx.compose.foundation.layout.Column {
            if (label != null) {
                Text(text = label, style = Tui.label, modifier = Modifier.padding(bottom = 4.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Tui.borderStrong), RoundedCornerShape(5.dp))
                    .background(Tui.inputBackground, RoundedCornerShape(5.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(text = placeholder, style = Tui.body.copy(color = Tui.textFaint))
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = singleLine,
                        minLines = minLines,
                        textStyle = Tui.body,
                        visualTransformation = visualTransformation,
                        keyboardOptions = keyboardOptions,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Tui.accentBlue),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                trailing?.invoke()
            }
        }
    }
}

/** An N-way tab strip sharing one hairline border, the selected segment lit up. */
@Composable
fun TuiSegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Tui.borderStrong), RoundedCornerShape(5.dp)),
    ) {
        options.forEachIndexed { index, opt ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) Tui.keycapBackground else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(index) },
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = opt,
                    style = Tui.label.copy(color = if (selected) Tui.accentBlue else Tui.textDim),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A filled call-to-action button in the TUI's flat, sharp-cornered style. */
@Composable
fun TuiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Tui.accentBlue,
    contentColor: Color = Tui.background,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (enabled) background else Tui.keycapBackground,
                RoundedCornerShape(5.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Tui.label.copy(color = if (enabled) contentColor else Tui.textFaint, fontSize = 12.sp),
        )
    }
}

/** A 30dp bordered square holding one icon — flokk's back/add/link button, everywhere. */
@Composable
fun TuiIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Tui.textDim,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .border(BorderStroke(1.dp, Tui.border), RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** A dashed-bordered, low-emphasis affordance — flokk's "+ add …" row. */
@Composable
fun TuiDashedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = Tui.borderStrong,
                    style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 9.dp, horizontal = 12.dp),
    ) {
        Text(text = text, style = Tui.label.copy(color = Tui.textDim, fontSize = 12.sp))
    }
}

/** An outlined, secondary-weight button matching [TuiButton]'s footprint. */
@Composable
fun TuiOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Tui.textDim,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Tui.borderStrong), RoundedCornerShape(5.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Tui.label.copy(color = if (enabled) contentColor else Tui.textFaint, fontSize = 12.sp),
        )
    }
}

/** A collapsed-by-default section: tap the title row to reveal [content]. */
@Composable
fun TuiExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = !expanded },
                )
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = Tui.label)
            Text(text = if (expanded) "▾" else "▸", style = Tui.label.copy(color = Tui.textFaint))
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun TuiConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
